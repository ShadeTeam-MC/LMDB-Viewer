package team.shade.lmdbviewer.decode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * Edge-case coverage complementing [DecoderTest]: the ASCII decoder, empty-input behaviour across
 * all decoders, JSON boundary cases, the non-4-byte integer widths, and one characterization of
 * how priority ordering resolves an ambiguous value.
 */
class DecoderEdgeCasesTest {

    private val registry = DecoderRegistry(
        listOf(
            HexDumpDecoder(),
            Utf8Decoder(),
            AsciiDecoder(),
            JsonDecoder(),
            IntegerDecoder(),
        ),
    )

    // ----- AsciiDecoder (previously untested) -----

    @Test
    fun asciiAcceptsPrintableAndAllowedWhitespace() {
        val ascii = AsciiDecoder()
        assertEquals(20, ascii.priority)
        assertTrue(ascii.canDecode("Hello\tWorld\r\n".bytes()))
        assertTrue(ascii.canDecode(byteArrayOf(0x20, 0x7E))) // space .. tilde bounds
        assertEquals("AB", ascii.decode(byteArrayOf(0x41, 0x42)).text)
    }

    @Test
    fun asciiRejectsControlExtendedAndEmpty() {
        val ascii = AsciiDecoder()
        assertFalse(ascii.canDecode(byteArrayOf(0x00)))            // NUL
        assertFalse(ascii.canDecode(byteArrayOf(0x1F)))            // control below space
        assertFalse(ascii.canDecode(byteArrayOf(0x7F)))            // DEL
        assertFalse(ascii.canDecode(byteArrayOf(0x80.toByte())))   // extended / high bit
        assertFalse(ascii.canDecode(byteArrayOf(0xFF.toByte())))
        assertFalse(ascii.canDecode(ByteArray(0)))
    }

    // ----- Empty input across every decoder -----

    @Test
    fun emptyInputRejectedByTextAndIntegerDecodersButHexHandlesIt() {
        val empty = ByteArray(0)
        assertFalse(Utf8Decoder().canDecode(empty))
        assertFalse(AsciiDecoder().canDecode(empty))
        assertFalse(JsonDecoder().canDecode(empty))
        assertFalse(IntegerDecoder().canDecode(empty))

        val hex = HexDumpDecoder()
        assertTrue(hex.canDecode(empty)) // hex is the universal fallback
        assertEquals("(empty, 0 bytes)", hex.decode(empty).text)

        // autoDetect must still resolve to *something* (hex) and never return null.
        assertEquals("hex", registry.autoDetect(empty)?.id)
        assertNotNull(registry.autoDetect(empty))
    }

    // ----- JSON boundary cases -----

    @Test
    fun jsonAcceptsEmptyContainers() {
        val json = JsonDecoder()
        assertTrue(json.canDecode("{}".bytes()))
        assertTrue(json.canDecode("[]".bytes()))
        assertEquals("{}", json.decode("{}".bytes()).text)
        assertEquals("[]", json.decode("  [] ".bytes()).text)
    }

    @Test
    fun jsonRejectsMalformedAndBareValues() {
        val json = JsonDecoder()
        assertFalse(json.canDecode("""{"a":1""".bytes()))   // unclosed object
        assertFalse(json.canDecode("""{"a":1,}""".bytes()))  // trailing comma
        assertFalse(json.canDecode("""{"a":1}trailing""".bytes())) // trailing content
        assertFalse(json.canDecode("42".bytes()))            // bare number (must start with { or [)
        assertFalse(json.canDecode("\"just a string\"".bytes())) // bare string
    }

    // ----- IntegerDecoder widths beyond the 4-byte case -----

    @Test
    fun integerDecoderAcceptsOneTwoAndEightBytes() {
        val dec = IntegerDecoder()
        assertTrue(dec.canDecode(ByteArray(1)))
        assertTrue(dec.canDecode(ByteArray(2)))
        assertTrue(dec.canDecode(ByteArray(8)))

        val one = dec.decode(byteArrayOf(0xFF.toByte())).text
        assertTrue(one.contains("Width: 8-bit"))
        assertTrue(one.contains("signed   = -1"))   // byte 0xFF as signed
        assertTrue(one.contains("unsigned = 255"))

        val two = dec.decode(byteArrayOf(0x01, 0x00)).text
        assertTrue(two.contains("Width: 16-bit"))
    }

    @Test
    fun integerDecoderEightByteUnsignedOverflow() {
        val dec = IntegerDecoder()
        val allOnes = ByteArray(8) { 0xFF.toByte() }
        val text = dec.decode(allOnes).text
        assertTrue(text.contains("Width: 64-bit"))
        assertTrue(text.contains("signed   = -1"))                       // two's complement
        assertTrue(text.contains("unsigned = 18446744073709551615"))      // 2^64 - 1
    }

    @Test
    fun integerDecoderRejectsNonPowerWidths() {
        val dec = IntegerDecoder()
        assertFalse(dec.canDecode(ByteArray(0)))
        assertFalse(dec.canDecode(ByteArray(3)))
        assertFalse(dec.canDecode(ByteArray(5)))
        assertFalse(dec.canDecode(ByteArray(16)))
    }

    // ----- Auto-detect prefers text over integer for printable fixed-width values -----

    @Test
    fun fourByteAsciiAutoDetectsAsTextNotInteger() {
        // "name" is valid UTF-8 *and* a 4-byte integer width. Auto-detect prefers text: IntegerDecoder
        // declines printable values, so a short readable string is not shown as an int32. Binary bytes
        // of the same width still decode as integers; the user can pick Integer manually.
        assertEquals("utf8", registry.autoDetect("name".bytes())?.id)
        assertEquals("int", registry.autoDetect(byteArrayOf(0x00, 0x00, 0x00, 0x01))?.id)
    }

    private fun String.bytes() = toByteArray(StandardCharsets.UTF_8)
}
