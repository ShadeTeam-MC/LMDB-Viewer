package team.shade.lmdbviewer.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets

/** Tests the UTF-8 / Hex byte encoding used by the entry editor dialog. */
class ByteCodecTest {

    @Test
    fun utf8ParsesAndFormats() {
        val bytes = ByteCodec.parse("héllo", ByteCodec.Mode.UTF8)!!
        assertArrayEquals("héllo".toByteArray(StandardCharsets.UTF_8), bytes)
        assertEquals("héllo", ByteCodec.format(bytes, ByteCodec.Mode.UTF8))
    }

    @Test
    fun hexParsesWithAndWithoutPrefixAndWhitespace() {
        val expected = byteArrayOf(0x00, 0xFF.toByte(), 0x10)
        assertArrayEquals(expected, ByteCodec.parse("00FF10", ByteCodec.Mode.HEX))
        assertArrayEquals(expected, ByteCodec.parse("0x00 ff 10", ByteCodec.Mode.HEX))
        assertArrayEquals(expected, ByteCodec.parse(" 00 FF 10 ", ByteCodec.Mode.HEX))
    }

    @Test
    fun hexFormatIsUppercaseSpaceSeparated() {
        assertEquals("00 FF 10", ByteCodec.format(byteArrayOf(0x00, 0xFF.toByte(), 0x10), ByteCodec.Mode.HEX))
    }

    @Test
    fun hexRejectsOddLengthAndNonHex() {
        assertNull(ByteCodec.parse("ABC", ByteCodec.Mode.HEX))   // odd number of digits
        assertNull(ByteCodec.parse("zz", ByteCodec.Mode.HEX))    // not hex digits
    }

    @Test
    fun roundTripsBinaryThroughHex() {
        val original = byteArrayOf(0, 1, 2, 127, -1, -128)
        val text = ByteCodec.format(original, ByteCodec.Mode.HEX)
        assertArrayEquals(original, ByteCodec.parse(text, ByteCodec.Mode.HEX))
    }

    @Test
    fun defaultModeChoosesUtf8ForTextAndHexForBinary() {
        assertEquals(ByteCodec.Mode.UTF8, ByteCodec.defaultMode("plain text".toByteArray()))
        assertEquals(ByteCodec.Mode.HEX, ByteCodec.defaultMode(byteArrayOf(0x00, 0xFF.toByte())))
        assertEquals(ByteCodec.Mode.HEX, ByteCodec.defaultMode(ByteArray(0))) // empty -> hex
    }
}
