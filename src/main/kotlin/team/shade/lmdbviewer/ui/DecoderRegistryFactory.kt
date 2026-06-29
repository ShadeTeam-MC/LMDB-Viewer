package team.shade.lmdbviewer.ui

import com.intellij.openapi.extensions.ExtensionPointName
import team.shade.lmdbviewer.decode.ByteDecoder
import team.shade.lmdbviewer.decode.DecoderRegistry

/** Builds a [DecoderRegistry] from the `team.shade.lmdbviewer.byteDecoder` extension point. */
internal object DecoderRegistryFactory {

    private val EP_NAME: ExtensionPointName<ByteDecoder> =
        ExtensionPointName.create("team.shade.lmdbviewer.byteDecoder")

    fun create(): DecoderRegistry = DecoderRegistry(EP_NAME.extensionList)
}
