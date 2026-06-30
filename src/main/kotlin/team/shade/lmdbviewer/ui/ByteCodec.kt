package team.shade.lmdbviewer.ui

import team.shade.lmdbviewer.decode.decodeStrict
import java.nio.charset.StandardCharsets

/**
 * Converts between raw bytes and the editable text shown in [EntryEditorDialog]. Two encodings are
 * offered: plain UTF-8 text, and hexadecimal (the same convention as the key-prefix search —
 * pairs of hex digits, optional whitespace, optional `0x` lead).
 */
internal object ByteCodec {

    enum class Mode { UTF8, HEX }

    /** Parses [text] in [mode] into bytes, or null if it is not valid for that mode. */
    fun parse(text: String, mode: Mode): ByteArray? = when (mode) {
        Mode.UTF8 -> text.toByteArray(StandardCharsets.UTF_8)
        Mode.HEX -> parseHex(text)
    }

    /** Renders [bytes] for display/editing in [mode]. */
    fun format(bytes: ByteArray, mode: Mode): String = when (mode) {
        Mode.UTF8 -> String(bytes, StandardCharsets.UTF_8)
        Mode.HEX -> bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
    }

    /** UTF-8 when the bytes are valid (round-trippable) UTF-8 text, otherwise HEX. */
    fun defaultMode(bytes: ByteArray): Mode =
        if (bytes.isNotEmpty() && decodeStrict(bytes) != null) Mode.UTF8 else Mode.HEX

    private fun parseHex(text: String): ByteArray? {
        var hex = text.trim()
        if (hex.startsWith("0x", ignoreCase = true)) hex = hex.substring(2)
        hex = hex.filter { !it.isWhitespace() }
        if (hex.length % 2 != 0) return null
        return runCatching {
            ByteArray(hex.length / 2) {
                ((hex[it * 2].digitToInt(16) shl 4) or hex[it * 2 + 1].digitToInt(16)).toByte()
            }
        }.getOrNull()
    }
}
