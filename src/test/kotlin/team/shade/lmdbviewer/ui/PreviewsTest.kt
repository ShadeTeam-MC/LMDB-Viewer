package team.shade.lmdbviewer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Tests the single-line [Previews.preview] formatter: empty marker, printable-text handling with
 * whitespace substitution, the hex fallback for binary, length truncation, and the multi-byte
 * boundary trimming that protects long values.
 */
class PreviewsTest {

    @Test
    fun emptyBytesShowNullMarker() {
        assertEquals("∅", Previews.preview(ByteArray(0))) // ∅
    }

    @Test
    fun printableTextPassesThrough() {
        assertEquals("hello world", Previews.preview("hello world".utf8()))
    }

    @Test
    fun newlineAndTabAreSubstituted() {
        // '\n' -> '␊' (U+240A), '\t' -> ' '
        assertEquals("a␊b", Previews.preview("a\nb".utf8()))
        assertEquals("a b", Previews.preview("a\tb".utf8()))
    }

    @Test
    fun binaryFallsBackToHex() {
        assertEquals("00 FF", Previews.preview(byteArrayOf(0x00, 0xFF.toByte())))
        // A control byte (other than tab/newline/cr) forces the hex path.
        assertTrue(Previews.preview(byteArrayOf(0x41, 0x01, 0x42)).matches(Regex("[0-9A-F ]+")))
    }

    @Test
    fun textAtMaxLengthIsNotTruncated() {
        val exactly120 = "a".repeat(120)
        assertEquals(exactly120, Previews.preview(exactly120.utf8()))
    }

    @Test
    fun textBeyondMaxLengthIsTruncatedWithEllipsis() {
        val result = Previews.preview("a".repeat(121).utf8())
        assertEquals("a".repeat(120) + "…", result) // 120 chars + ellipsis (U+2026)
        assertTrue(result.endsWith("…"))
    }

    @Test
    fun longValueDoesNotEmitSplitMultibyteReplacementChar() {
        // 255 ASCII bytes then the first byte of a 2-byte UTF-8 char, so the char straddles the
        // 256-byte scan limit. The trailing replacement char must be trimmed, not shown.
        val bytes = ByteArray(255) { 'a'.code.toByte() } + byteArrayOf(0xC3.toByte(), 0xA9.toByte())
        val result = Previews.preview(bytes)
        assertFalse("must not contain U+FFFD", result.contains('�'))
        assertTrue(result.startsWith("a"))
    }

    private fun String.utf8() = toByteArray(StandardCharsets.UTF_8)
}
