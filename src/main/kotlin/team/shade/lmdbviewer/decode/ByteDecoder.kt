package team.shade.lmdbviewer.decode

/**
 * A human-readable view of a byte array produced by a [ByteDecoder].
 *
 * @param text the rendered content to show
 * @param monospace whether the content should be displayed with a fixed-width font
 */
data class DecodedView(
    val text: String,
    val monospace: Boolean = true,
)

/**
 * Decodes opaque LMDB byte arrays (keys or values) into a human-readable view.
 *
 * Implementations must be pure and side-effect free. Register additional decoders through the
 * `team.shade.lmdbviewer.byteDecoder` extension point. See CLAUDE.md for the contract.
 */
interface ByteDecoder {
    /** Stable identifier, e.g. `"hex"`, `"utf8"`, `"json"`. */
    val id: String

    /** Human-facing name shown in the decoder dropdown. */
    val displayName: String

    /**
     * Ordering hint for auto-detection. Higher wins. Structured formats (JSON) should rank above
     * plain text, which should rank above the hex fallback.
     */
    val priority: Int

    /** Cheap, side-effect-free check. Return false rather than throwing on unsuitable input. */
    fun canDecode(bytes: ByteArray): Boolean

    /** Render [bytes]. Must never throw — return a best-effort [DecodedView] instead. */
    fun decode(bytes: ByteArray): DecodedView
}
