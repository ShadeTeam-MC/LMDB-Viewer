package team.shade.lmdbviewer.ui

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer

/** Single-line previews of raw bytes for the entries table. */
internal object Previews {

    private const val MAX_CHARS = 120

    /** Only ever scan this many leading bytes — a one-line preview never needs more. */
    private const val SCAN_LIMIT = 256

    /** A compact one-line representation: readable text when possible, otherwise hex. */
    fun preview(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "∅"
        val text = asPrintableText(bytes)
        return if (text != null) truncate(text) else truncate(hexPreview(bytes))
    }

    private fun asPrintableText(bytes: ByteArray): String? {
        // Decode only the leading bytes leniently, so a huge value never allocates a huge String.
        val head = if (bytes.size > SCAN_LIMIT) bytes.copyOf(SCAN_LIMIT) else bytes
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        var decoded = try {
            decoder.decode(ByteBuffer.wrap(head)).toString()
        } catch (_: Exception) {
            return null
        }
        // A trailing replacement char can be a multi-byte sequence split by the scan limit — drop it.
        if (bytes.size > SCAN_LIMIT) decoded = decoded.trimEnd('�')
        if (decoded.any { it == '�' || (it.isISOControl() && it != '\t' && it != '\n' && it != '\r') }) {
            return null
        }
        return decoded.replace('\n', '␊').replace('\t', ' ')
    }

    private fun hexPreview(bytes: ByteArray): String {
        val sb = StringBuilder()
        val n = minOf(bytes.size, MAX_CHARS / 3)
        for (i in 0 until n) {
            sb.append(String.format("%02X", bytes[i].toInt() and 0xFF))
            if (i < n - 1) sb.append(' ')
        }
        if (n < bytes.size) sb.append(" …")
        return sb.toString()
    }

    private fun truncate(s: String): String =
        if (s.length <= MAX_CHARS) s else s.substring(0, MAX_CHARS) + "…"
}
