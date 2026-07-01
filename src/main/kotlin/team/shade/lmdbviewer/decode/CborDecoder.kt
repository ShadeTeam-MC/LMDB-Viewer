package team.shade.lmdbviewer.decode

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import kotlin.math.pow

/**
 * Decodes **CBOR** (RFC 8949) values and renders them as pretty-printed JSON. CBOR is a compact,
 * self-describing binary format used by many stores as the on-disk value encoding, so a hex dump of
 * such a value is unreadable — this turns it into a human-readable JSON tree.
 *
 * Universal and dependency-free (no Jackson/CBOR library), in the spirit of the other built-in
 * decoders. Handles the full CBOR type set: unsigned/negative integers (falling back to
 * [BigInteger] beyond `Long` range), byte/text strings (definite + indefinite), arrays, maps,
 * tags, bignums (tag 2/3), binary UUIDs (tag 37 → canonical `xxxxxxxx-…` string), half/single/double
 * floats, booleans, null/undefined, and simple values. Byte strings are shown as `0x…` hex.
 */
class CborDecoder : ByteDecoder {
    override val id: String = "cbor"
    override val displayName: String = "CBOR"

    // Below text JSON (80) so real JSON text still wins, above Integer (60): structured beats scalar.
    override val priority: Int = 70

    /**
     * Strict on purpose: only claim a value in auto-detect when the root is a container-ish item
     * (array/map/tag) AND the whole buffer parses as exactly one CBOR item with no trailing bytes.
     * This keeps random integers/text (which are also technically valid tiny CBOR) out of Auto.
     */
    override fun canDecode(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val major = (bytes[0].toInt() and 0xE0) ushr 5
        if (major != MAJOR_ARRAY && major != MAJOR_MAP && major != MAJOR_TAG) return false
        return runCatching {
            val reader = Reader(bytes)
            reader.readValue()
            reader.atEnd() // must consume every byte — no trailing garbage
        }.getOrDefault(false)
    }

    override fun decode(bytes: ByteArray): DecodedView {
        return runCatching {
            val value = Reader(bytes).readValue()
            val sb = StringBuilder()
            writeJson(value, sb, 0)
            DecodedView(sb.toString(), monospace = true)
        }.getOrElse { DecodedView("(CBOR decode error: ${it.message})", monospace = true) }
    }

    // --- JSON rendering (parsed CBOR objects -> pretty JSON text) ---

    private fun writeJson(value: Any?, sb: StringBuilder, indent: Int) {
        when (value) {
            null -> sb.append("null")
            is Boolean -> sb.append(value.toString())
            is String -> writeString(value, sb)
            is Double -> sb.append(jsonNumber(value))
            is Long, is Int, is BigInteger -> sb.append(value.toString())
            is ByteArray -> writeString(toHex(value), sb)
            is List<*> -> writeArray(value, sb, indent)
            is Map<*, *> -> writeObject(value, sb, indent)
            else -> writeString(value.toString(), sb)
        }
    }

    private fun writeArray(list: List<*>, sb: StringBuilder, indent: Int) {
        if (list.isEmpty()) { sb.append("[]"); return }
        sb.append("[\n")
        val child = indent + 1
        list.forEachIndexed { i, e ->
            appendIndent(sb, child)
            writeJson(e, sb, child)
            if (i < list.size - 1) sb.append(',')
            sb.append('\n')
        }
        appendIndent(sb, indent)
        sb.append(']')
    }

    private fun writeObject(map: Map<*, *>, sb: StringBuilder, indent: Int) {
        if (map.isEmpty()) { sb.append("{}"); return }
        sb.append("{\n")
        val child = indent + 1
        val entries = map.entries.toList()
        entries.forEachIndexed { i, (k, v) ->
            appendIndent(sb, child)
            writeString(keyToString(k), sb) // JSON keys must be strings; stringify non-text keys
            sb.append(": ")
            writeJson(v, sb, child)
            if (i < entries.size - 1) sb.append(',')
            sb.append('\n')
        }
        appendIndent(sb, indent)
        sb.append('}')
    }

    private fun keyToString(key: Any?): String = when (key) {
        null -> "null"
        is String -> key
        is ByteArray -> toHex(key)
        else -> key.toString()
    }

    /** JSON forbids NaN/Infinity literals — emit them as strings so the output stays valid JSON. */
    private fun jsonNumber(d: Double): String = when {
        d.isNaN() -> "\"NaN\""
        d == Double.POSITIVE_INFINITY -> "\"Infinity\""
        d == Double.NEGATIVE_INFINITY -> "\"-Infinity\""
        else -> d.toString()
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private fun appendIndent(sb: StringBuilder, indent: Int) = repeat(indent) { sb.append("  ") }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder("0x")
        for (b in bytes) sb.append("%02X".format(b.toInt() and 0xFF))
        return sb.toString()
    }

    /** Signals malformed/truncated CBOR; caught by [canDecode]/[decode] so nothing ever escapes. */
    private class CborError(message: String) : RuntimeException(message)

    /**
     * Forward-only RFC 8949 reader. Produces plain Kotlin objects: [Long]/[BigInteger] integers,
     * [Double] floats, [Boolean], `null`, [String] text (and UUIDs), [ByteArray] byte strings,
     * [List] arrays and [LinkedHashMap] maps (insertion order preserved).
     */
    private inner class Reader(private val data: ByteArray) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= data.size

        fun readValue(): Any? {
            val ib = readByte()
            val major = ib ushr 5
            val info = ib and 0x1F
            return when (major) {
                MAJOR_UINT -> readUnsignedInt(info)
                MAJOR_NINT -> readNegativeInt(info)
                MAJOR_BYTES -> if (info == INDEFINITE) readIndefinite(MAJOR_BYTES) else readBytes(readLength(info))
                MAJOR_TEXT -> String(
                    if (info == INDEFINITE) readIndefinite(MAJOR_TEXT) else readBytes(readLength(info)),
                    Charsets.UTF_8,
                )
                MAJOR_ARRAY -> readArray(info)
                MAJOR_MAP -> readMap(info)
                MAJOR_TAG -> readTagged(info)
                MAJOR_SIMPLE -> readSimpleOrFloat(info)
                else -> throw CborError("unknown CBOR major type $major")
            }
        }

        private fun readUnsignedInt(info: Int): Any {
            val raw = readArgument(info)
            // info 27 (8 bytes) can exceed Long.MAX (raw goes negative) — widen to an unsigned BigInteger.
            return if (info == 27 && raw < 0) unsignedBig(raw) else raw
        }

        private fun readNegativeInt(info: Int): Any {
            val raw = readArgument(info)
            return if (info == 27 && raw < 0) BigInteger.valueOf(-1L).subtract(unsignedBig(raw)) else -1L - raw
        }

        private fun readArray(info: Int): List<Any?> {
            val list = ArrayList<Any?>()
            if (info == INDEFINITE) {
                while (!tryBreak()) list.add(readValue())
            } else {
                val n = readLength(info)
                repeat(n) { list.add(readValue()) }
            }
            return list
        }

        private fun readMap(info: Int): LinkedHashMap<Any?, Any?> {
            val map = LinkedHashMap<Any?, Any?>()
            if (info == INDEFINITE) {
                while (!tryBreak()) { val k = readValue(); map[k] = readValue() }
            } else {
                val n = readLength(info)
                repeat(n) { val k = readValue(); map[k] = readValue() }
            }
            return map
        }

        private fun readTagged(info: Int): Any? {
            val tag = readArgument(info)
            val value = readValue()
            return when {
                tag == TAG_UUID && value is ByteArray -> uuidFromBytes(value)
                tag == TAG_BIGNUM_POS && value is ByteArray -> BigInteger(1, value)
                tag == TAG_BIGNUM_NEG && value is ByteArray -> BigInteger.valueOf(-1L).subtract(BigInteger(1, value))
                else -> value // unknown tag: pass the wrapped value through transparently
            }
        }

        private fun readSimpleOrFloat(info: Int): Any? = when (info) {
            20 -> false
            21 -> true
            22 -> null
            23 -> null // undefined -> null
            25 -> halfToDouble((readArgument(25) and 0xFFFF).toInt())
            26 -> Float.fromBits(readArgument(26).toInt()).toDouble()
            27 -> Double.fromBits(readArgument(27))
            INDEFINITE -> throw CborError("unexpected break")
            else -> (if (info == 24) readByte().toLong() else info.toLong()) // simple value -> number
        }

        private fun uuidFromBytes(bytes: ByteArray): Any {
            if (bytes.size != 16) return bytes // not a UUID payload — render as hex
            val h = StringBuilder()
            for (b in bytes) h.append("%02x".format(b.toInt() and 0xFF))
            return "${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-" +
                "${h.substring(16, 20)}-${h.substring(20)}"
        }

        /** IEEE 754 half-precision (16-bit) → double. */
        private fun halfToDouble(half: Int): Double {
            val sign = if (half and 0x8000 != 0) -1.0 else 1.0
            val exp = (half ushr 10) and 0x1F
            val mant = half and 0x03FF
            val magnitude = when (exp) {
                0 -> mant * 2.0.pow(-24) // subnormal
                31 -> if (mant == 0) Double.POSITIVE_INFINITY else Double.NaN
                else -> (mant + 1024) * 2.0.pow(exp - 25)
            }
            return sign * magnitude
        }

        /** Reads a definite-length chunked (indefinite) byte/text string, concatenating chunks. */
        private fun readIndefinite(expectedMajor: Int): ByteArray {
            val out = ByteArrayOutputStream()
            while (!tryBreak()) {
                val ib = readByte()
                val info = ib and 0x1F
                if ((ib ushr 5) != expectedMajor || info == INDEFINITE) {
                    throw CborError("malformed indefinite-length string chunk")
                }
                out.write(readBytes(readLength(info)))
            }
            return out.toByteArray()
        }

        /** Reads the head argument (inline value, or 1/2/4/8 trailing big-endian bytes). */
        private fun readArgument(info: Int): Long = when {
            info < 24 -> info.toLong()
            info == 24 -> readByte().toLong()
            info == 25 -> readN(2)
            info == 26 -> readN(4)
            info == 27 -> readN(8)
            else -> throw CborError("reserved/invalid additional info $info")
        }

        private fun readLength(info: Int): Int {
            val raw = readArgument(info)
            if (raw < 0 || raw > Int.MAX_VALUE) throw CborError("length out of range: $raw")
            return raw.toInt()
        }

        private fun readN(n: Int): Long {
            var v = 0L
            repeat(n) { v = (v shl 8) or (readByte().toLong() and 0xFF) }
            return v
        }

        private fun unsignedBig(raw: Long): BigInteger =
            BigInteger(java.lang.Long.toUnsignedString(raw))

        private fun tryBreak(): Boolean {
            if (pos < data.size && (data[pos].toInt() and 0xFF) == BREAK) { pos++; return true }
            return false
        }

        private fun readByte(): Int {
            if (pos >= data.size) throw CborError("truncated CBOR payload")
            return data[pos++].toInt() and 0xFF
        }

        private fun readBytes(len: Int): ByteArray {
            if (len < 0 || pos + len > data.size) throw CborError("truncated CBOR payload")
            val out = data.copyOfRange(pos, pos + len)
            pos += len
            return out
        }
    }

    private companion object {
        // CBOR major types (high 3 bits of the initial byte).
        const val MAJOR_UINT = 0
        const val MAJOR_NINT = 1
        const val MAJOR_BYTES = 2
        const val MAJOR_TEXT = 3
        const val MAJOR_ARRAY = 4
        const val MAJOR_MAP = 5
        const val MAJOR_TAG = 6
        const val MAJOR_SIMPLE = 7

        const val INDEFINITE = 31 // additional-info value marking an indefinite-length item
        const val BREAK = 0xFF // the "break" stop code (major 7, info 31)

        const val TAG_BIGNUM_POS = 2L
        const val TAG_BIGNUM_NEG = 3L
        const val TAG_UUID = 37L // IANA-registered binary UUID tag
    }
}
