package team.shade.lmdbviewer.decode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class DecoderTest {

    private val registry = DecoderRegistry(
        listOf(
            HexDumpDecoder(),
            Utf8Decoder(),
            AsciiDecoder(),
            JsonDecoder(),
            IntegerDecoder(),
        ),
    )

    @Test
    fun hexDecoderHandlesAnyBytesAndIsLowestPriority() {
        val hex = HexDumpDecoder()
        assertTrue(hex.canDecode(byteArrayOf(0, 1, 2)))
        val view = hex.decode(byteArrayOf(0x41, 0x42))
        assertTrue(view.text.contains("41 42"))
        assertTrue(view.text.contains("|AB|"))
        assertEquals(0, hex.priority)
    }

    @Test
    fun utf8RejectsBinaryAcceptsText() {
        val utf8 = Utf8Decoder()
        assertTrue(utf8.canDecode("héllo wörld".toByteArray(StandardCharsets.UTF_8)))
        assertFalse(utf8.canDecode(byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0xFE.toByte())))
    }

    @Test
    fun jsonDetectsAndPrettyPrints() {
        val json = JsonDecoder()
        val raw = """{"b":2,"a":[1,2,{"x":true}]}""".toByteArray()
        assertTrue(json.canDecode(raw))
        val pretty = json.decode(raw).text
        assertTrue(pretty.contains("\n"))
        assertTrue(pretty.contains("\"a\": ["))
        // Not JSON:
        assertFalse(json.canDecode("just text".toByteArray()))
        assertFalse(json.canDecode(byteArrayOf(0x00, 0x01)))
    }

    @Test
    fun integerDecoderReadsBothByteOrders() {
        val dec = IntegerDecoder()
        // 0x00000001 big-endian == 1; little-endian == 16777216
        val be = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        assertTrue(dec.canDecode(be))
        val text = dec.decode(be).text
        assertTrue(text.contains("Big-endian"))
        assertTrue(text.contains("signed   = 1"))
        assertTrue(text.contains("16777216"))
        assertFalse(dec.canDecode(byteArrayOf(1, 2, 3))) // 3 bytes: not an integer width
    }

    @Test
    fun autoDetectPrefersJsonThenTextThenHex() {
        assertEquals("json", registry.autoDetect("""{"k":1}""".toByteArray())?.id)
        assertEquals("utf8", registry.autoDetect("plain text".toByteArray())?.id)
        assertEquals("hex", registry.autoDetect(byteArrayOf(0x00, 0xFF.toByte(), 0x01))?.id)
    }

    @Test
    fun autoDetectNeverThrowsOnEmpty() {
        assertNotNull(registry.autoDetect(ByteArray(0)))
    }
}
