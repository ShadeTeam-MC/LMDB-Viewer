package team.shade.lmdbviewer.decode

/**
 * Holds the available [ByteDecoder]s and selects the best one for a given byte array.
 *
 * Pure and platform-free: construct with an explicit list. In the running IDE the list comes from
 * the `team.shade.lmdbviewer.byteDecoder` extension point (see `ui/DecoderRegistryFactory`).
 */
class DecoderRegistry(decoders: List<ByteDecoder>) {

    /** All decoders, highest priority first. */
    val decoders: List<ByteDecoder> = decoders.sortedByDescending { it.priority }

    /** The highest-priority decoder whose [ByteDecoder.canDecode] accepts [bytes], or null. */
    fun autoDetect(bytes: ByteArray): ByteDecoder? = decoders.firstOrNull {
        runCatching { it.canDecode(bytes) }.getOrDefault(false)
    }
}
