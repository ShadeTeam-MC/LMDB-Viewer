package team.shade.lmdbviewer.decode

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Interprets 1/2/4/8-byte values as integers in both byte orders (LMDB `MDB_INTEGERKEY` keys are a
 * very common case). Shows signed and unsigned readings so the user can pick the right one.
 */
class IntegerDecoder : ByteDecoder {
    override val id: String = "int"
    override val displayName: String = "Integer"
    override val priority: Int = 60

    override fun canDecode(bytes: ByteArray): Boolean = bytes.size in INT_SIZES

    override fun decode(bytes: ByteArray): DecodedView {
        if (bytes.size !in INT_SIZES) {
            return DecodedView("(not a 1/2/4/8-byte integer: ${bytes.size} bytes)", monospace = true)
        }
        val le = read(bytes, ByteOrder.LITTLE_ENDIAN)
        val be = read(bytes, ByteOrder.BIG_ENDIAN)
        val width = bytes.size * 8
        val sb = StringBuilder()
        sb.append("Width: $width-bit\n\n")
        sb.append("Little-endian:\n")
        sb.append("  signed   = ${le.signed}\n")
        sb.append("  unsigned = ${le.unsigned}\n\n")
        sb.append("Big-endian:\n")
        sb.append("  signed   = ${be.signed}\n")
        sb.append("  unsigned = ${be.unsigned}")
        return DecodedView(sb.toString(), monospace = true)
    }

    private fun read(bytes: ByteArray, order: ByteOrder): Reading {
        val buf = ByteBuffer.wrap(bytes).order(order)
        return when (bytes.size) {
            1 -> {
                val v = bytes[0].toLong()
                Reading(v.toString(), (v and 0xFF).toString())
            }
            2 -> {
                val v = buf.short
                Reading(v.toString(), (v.toInt() and 0xFFFF).toString())
            }
            4 -> {
                val v = buf.int
                Reading(v.toString(), (v.toLong() and 0xFFFFFFFFL).toString())
            }
            8 -> {
                val v = buf.long
                Reading(v.toString(), java.lang.Long.toUnsignedString(v))
            }
            else -> Reading("?", "?")
        }
    }

    private data class Reading(val signed: String, val unsigned: String)

    private companion object {
        val INT_SIZES = setOf(1, 2, 4, 8)
    }
}
