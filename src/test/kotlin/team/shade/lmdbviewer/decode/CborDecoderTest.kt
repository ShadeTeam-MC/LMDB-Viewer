package team.shade.lmdbviewer.decode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class CborDecoderTest {

    private val cbor = CborDecoder()

    /** Parses a hex string (whitespace/newlines ignored) into bytes. */
    private fun hex(s: String): ByteArray {
        val clean = s.filter { !it.isWhitespace() }
        require(clean.length % 2 == 0) { "odd hex length" }
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    // A real waypoint-marker value dumped from an LMDB store (the case that motivated this decoder).
    private val markerSample1 = hex(
        """
        B0 62 49 64 D8 25 50 8B 57 1C D8 DF A0 42 F7 88
        EE 0E CE BC 7A 9B E5 61 58 FB 40 72 5D 85 29 60
        BA 3B 61 59 FB 40 50 3C 00 00 C5 CA 68 61 5A FB
        40 57 9A D2 12 AB 3C FE 63 59 61 77 FA 80 00 00
        00 68 50 61 74 68 55 75 69 64 D8 25 50 74 C9 1B
        00 7E 88 47 98 83 2F D9 40 F3 C7 B9 D3 69 50 61
        74 68 49 6E 64 65 78 01 6A 50 61 74 68 43 6F 6C
        6F 72 52 18 D4 6A 50 61 74 68 43 6F 6C 6F 72 47
        18 1D 6A 50 61 74 68 43 6F 6C 6F 72 42 18 1D 68
        50 61 74 68 4E 61 6D 65 6B 44 61 6E 67 65 72 20
        50 61 74 68 6A 50 61 74 68 57 65 69 67 68 74 FA
        3F 80 00 00 6B 50 61 74 68 4C 6F 6F 70 69 6E 67
        F4 6B 42 72 61 6E 63 68 50 6F 69 6E 74 F4 65 53
        70 65 65 64 FA 00 00 00 00 69 57 61 69 74 44 65
        6C 61 79 00
        """,
    )

    private val markerSample2 = hex(
        """
        B0 62 49 64 D8 25 50 9B 80 DF 3F 53 BB 48 A4 9B
        38 03 8B 01 6B B2 35 61 58 FB 40 73 B9 43 2C DA
        E3 EE 61 59 FB 40 4F F8 00 00 00 00 00 61 5A FB
        40 57 72 EA D4 EE 52 D3 63 59 61 77 FA 00 00 00
        00 68 50 61 74 68 55 75 69 64 D8 25 50 74 C9 1B
        00 7E 88 47 98 83 2F D9 40 F3 C7 B9 D3 69 50 61
        74 68 49 6E 64 65 78 02 6A 50 61 74 68 43 6F 6C
        6F 72 52 18 D4 6A 50 61 74 68 43 6F 6C 6F 72 47
        18 1D 6A 50 61 74 68 43 6F 6C 6F 72 42 18 1D 68
        50 61 74 68 4E 61 6D 65 6B 44 61 6E 67 65 72 20
        50 61 74 68 6A 50 61 74 68 57 65 69 67 68 74 FA
        3F 80 00 00 6B 50 61 74 68 4C 6F 6F 70 69 6E 67
        F4 6B 42 72 61 6E 63 68 50 6F 69 6E 74 F4 65 53
        70 65 65 64 FA 00 00 00 00 69 57 61 69 74 44 65
        6C 61 79 00
        """,
    )

    @Test
    fun rendersRealWaypointMarkerAsJson() {
        val json = cbor.decode(markerSample1).text
        assertTrue(json.contains("\"Id\": \"8b571cd8-dfa0-42f7-88ee-0ecebc7a9be5\""))
        assertTrue(json.contains("\"PathUuid\": \"74c91b00-7e88-4798-832f-d940f3c7b9d3\""))
        assertTrue(json.contains("\"PathName\": \"Danger Path\""))
        assertTrue(json.contains("\"PathIndex\": 1"))
        assertTrue(json.contains("\"PathColorR\": 212"))
        assertTrue(json.contains("\"PathLooping\": false"))
        assertTrue(json.contains("\"BranchPoint\": false"))
        assertTrue(json.contains("\"X\": ")) // a float value, exact form not asserted
    }

    @Test
    fun rendersSecondMarkerWithItsOwnUuidAndIndex() {
        val json = cbor.decode(markerSample2).text
        assertTrue(json.contains("\"Id\": \"9b80df3f-53bb-48a4-9b38-038b016bb235\""))
        assertTrue(json.contains("\"PathIndex\": 2"))
        assertTrue(json.contains("\"PathName\": \"Danger Path\""))
    }

    @Test
    fun decodesScalarTypesInsideAMap() {
        // {"i":5,"n":-1,"f":3.5,"t":true,"z":null,"s":"hi"}
        val bytes = hex("A6 61 69 05 61 6E 20 61 66 FB 400C000000000000 61 74 F5 61 7A F6 61 73 62 6869")
        val json = cbor.decode(bytes).text
        assertTrue(json.contains("\"i\": 5"))
        assertTrue(json.contains("\"n\": -1"))
        assertTrue(json.contains("\"f\": 3.5"))
        assertTrue(json.contains("\"t\": true"))
        assertTrue(json.contains("\"z\": null"))
        assertTrue(json.contains("\"s\": \"hi\""))
    }

    @Test
    fun decodesNestedMapArrayAndByteString() {
        // {"m":{"x":1},"a":["p","q"],"b":h'DEADBEEF'}
        val bytes = hex("A3 61 6D A1 61 78 01 61 61 82 61 70 61 71 61 62 44 DEADBEEF")
        val json = cbor.decode(bytes).text
        assertTrue(json.contains("\"m\": {"))
        assertTrue(json.contains("\"x\": 1"))
        assertTrue(json.contains("\"a\": ["))
        assertTrue(json.contains("\"p\""))
        assertTrue(json.contains("\"b\": \"0xDEADBEEF\"")) // byte strings rendered as 0x hex
    }

    @Test
    fun float32AndUuidTagRenderCleanly() {
        // {"w":1.0(float32),"u":37(h'...16 bytes...')}
        val bytes = hex(
            "A2 61 77 FA 3F800000 61 75 D8 25 50 74C91B007E884798 83 2F D940F3C7B9D3",
        )
        val json = cbor.decode(bytes).text
        assertTrue(json.contains("\"w\": 1.0"))
        assertTrue(json.contains("\"u\": \"74c91b00-7e88-4798-832f-d940f3c7b9d3\""))
    }

    @Test
    fun canDecodeIsStrict() {
        // Accepts the real map-rooted marker.
        assertTrue(cbor.canDecode(markerSample1))
        // Text JSON starts with '{' (major 3) -> rejected, leaving it to the JSON decoder.
        assertFalse(cbor.canDecode("""{"a":1}""".toByteArray(StandardCharsets.UTF_8)))
        // A bare integer byte (major 0) -> rejected, so Integer still wins in auto-detect.
        assertFalse(cbor.canDecode(byteArrayOf(0x05)))
        // Plain text (major 3) -> rejected.
        assertFalse(cbor.canDecode("hello".toByteArray(StandardCharsets.UTF_8)))
        // A map head with trailing garbage must not be accepted.
        assertFalse(cbor.canDecode(hex("A1 61 61 01 FF FF")))
        assertFalse(cbor.canDecode(ByteArray(0)))
    }

    @Test
    fun neverThrowsOnMalformedInput() {
        // Truncated map (claims 1 pair, no bytes follow) — decode returns an error view, not an exception.
        val view = cbor.decode(hex("A1"))
        assertTrue(view.text.startsWith("(CBOR decode error"))
    }

    @Test
    fun participatesInAutoDetectWithoutHijackingOthers() {
        val registry = DecoderRegistry(
            listOf(
                HexDumpDecoder(),
                Utf8Decoder(),
                AsciiDecoder(),
                JsonDecoder(),
                CborDecoder(),
                IntegerDecoder(),
            ),
        )
        assertEquals("cbor", registry.autoDetect(markerSample1)?.id)
        assertEquals("json", registry.autoDetect("""{"k":1}""".toByteArray())?.id)
        assertEquals("int", registry.autoDetect(byteArrayOf(0x00, 0x00, 0x00, 0x01))?.id)
        assertEquals("utf8", registry.autoDetect("plain text".toByteArray())?.id)
    }
}
