package team.shade.lmdbviewer.decode

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Strict UTF-8 decoder. Accepts input that is valid UTF-8 and mostly printable, so binary blobs
 * that happen to contain a few ASCII bytes are not mistaken for text.
 */
class Utf8Decoder : ByteDecoder {
    override val id: String = "utf8"
    override val displayName: String = "UTF-8"
    override val priority: Int = 40

    override fun canDecode(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val text = decodeStrict(bytes) ?: return false
        return text.count { it.isPrintableOrWhitespace() } >= text.length * PRINTABLE_RATIO
    }

    override fun decode(bytes: ByteArray): DecodedView {
        val text = decodeStrict(bytes) ?: String(bytes, StandardCharsets.UTF_8)
        return DecodedView(text, monospace = false)
    }

    private companion object {
        const val PRINTABLE_RATIO = 0.85
    }
}

/**
 * Pure 7-bit ASCII decoder. A handy view when bytes are ASCII but you want to see the exact
 * characters without UTF-8 multi-byte interpretation.
 */
class AsciiDecoder : ByteDecoder {
    override val id: String = "ascii"
    override val displayName: String = "ASCII"
    override val priority: Int = 20

    override fun canDecode(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        return bytes.all { (it.toInt() and 0xFF).let { c -> c == 0x09 || c == 0x0A || c == 0x0D || c in 0x20..0x7E } }
    }

    override fun decode(bytes: ByteArray): DecodedView =
        DecodedView(String(bytes, StandardCharsets.US_ASCII), monospace = false)
}

internal fun decodeStrict(bytes: ByteArray): String? = try {
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    decoder.decode(ByteBuffer.wrap(bytes)).toString()
} catch (_: Exception) {
    null
}

internal fun Char.isPrintableOrWhitespace(): Boolean =
    this == '\t' || this == '\n' || this == '\r' || (!this.isISOControl())
