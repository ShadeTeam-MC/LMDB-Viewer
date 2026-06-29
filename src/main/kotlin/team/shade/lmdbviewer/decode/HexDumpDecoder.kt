package team.shade.lmdbviewer.decode

/**
 * Classic hex dump: 16 bytes per row with an offset gutter and an ASCII column.
 * Always applicable; used as the universal fallback, so it has the lowest priority.
 */
class HexDumpDecoder : ByteDecoder {
    override val id: String = "hex"
    override val displayName: String = "Hex"
    override val priority: Int = 0

    override fun canDecode(bytes: ByteArray): Boolean = true

    override fun decode(bytes: ByteArray): DecodedView {
        if (bytes.isEmpty()) return DecodedView("(empty, 0 bytes)")

        val sb = StringBuilder()
        var offset = 0
        while (offset < bytes.size) {
            sb.append(String.format("%08X  ", offset))
            val rowEnd = minOf(offset + BYTES_PER_ROW, bytes.size)

            // Hex columns.
            for (i in offset until offset + BYTES_PER_ROW) {
                if (i < rowEnd) {
                    sb.append(String.format("%02X ", bytes[i].toInt() and 0xFF))
                } else {
                    sb.append("   ")
                }
                if (i - offset == BYTES_PER_ROW / 2 - 1) sb.append(' ')
            }

            // ASCII column.
            sb.append(" |")
            for (i in offset until rowEnd) {
                val c = bytes[i].toInt() and 0xFF
                sb.append(if (c in 0x20..0x7E) c.toChar() else '.')
            }
            sb.append("|\n")
            offset = rowEnd
        }
        return DecodedView(sb.toString().trimEnd('\n'))
    }

    private companion object {
        const val BYTES_PER_ROW = 16
    }
}
