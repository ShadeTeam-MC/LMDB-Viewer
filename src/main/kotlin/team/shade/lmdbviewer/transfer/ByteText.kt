package team.shade.lmdbviewer.transfer

import team.shade.lmdbviewer.decode.decodeStrict
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Lossless, reversible encoding of arbitrary key/value bytes as tagged text for JSON/NDJSON.
 *
 * Bytes that are valid UTF-8 are kept as readable text (`utf8`); anything else falls back to
 * base64. This favours human-readable dumps for the common textual case while still round-tripping
 * arbitrary binary exactly. Reuses the decode layer's strict UTF-8 check ([decodeStrict]).
 */
internal object ByteText {

    const val UTF8 = "utf8"
    const val BASE64 = "base64"

    /** Returns the encoding tag (`utf8`/`base64`) and encoded string for [bytes]. */
    fun encode(bytes: ByteArray): Pair<String, String> {
        val text = decodeStrict(bytes)
        return if (text != null) UTF8 to text else BASE64 to Base64.getEncoder().encodeToString(bytes)
    }

    /** Inverse of [encode]; throws [IllegalArgumentException] on an unknown tag or bad base64. */
    fun decode(enc: String, value: String): ByteArray = when (enc) {
        UTF8 -> value.toByteArray(StandardCharsets.UTF_8)
        BASE64 -> Base64.getDecoder().decode(value)
        else -> throw IllegalArgumentException("Unknown byte encoding '$enc'")
    }

    /** A one-column human rendering for CSV: UTF-8 text when valid, otherwise `0x…` lower-hex. */
    fun human(bytes: ByteArray): String =
        decodeStrict(bytes) ?: bytes.joinToString(separator = "", prefix = "0x") { "%02x".format(it.toInt() and 0xFF) }
}
