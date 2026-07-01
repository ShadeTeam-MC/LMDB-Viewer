package team.shade.lmdbviewer.transfer

/**
 * Minimal, dependency-free JSON helpers for the transfer layer: a string escaper for writing and a
 * recursive-descent value parser for reading. The existing decode-layer [team.shade.lmdbviewer.decode]
 * formatter only re-emits JSON; import needs actual parsed values, so this parser produces a tree of
 * [Map], [List], [String], [Double], [Boolean], and null.
 */
internal object Json {

    private val FORM_FEED: Char = 12.toChar() // ASCII form feed (0x0C); no raw control char in source

    /** Appends [s] as a quoted, escaped JSON string to [out]. */
    fun appendString(out: Appendable, s: String) {
        out.append('"')
        for (c in s) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                FORM_FEED -> out.append("\\f")
                else -> if (c < ' ') out.append("\\u").append("%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    /** Parses [text] as a single JSON value (no trailing content allowed). */
    fun parse(text: String): Any? = Parser(text).parseWhole()

    private class Parser(private val s: String) {
        private var i = 0

        fun parseWhole(): Any? {
            skipWs()
            val v = parseValue()
            skipWs()
            if (i != s.length) error("Trailing content at $i")
            return v
        }

        private fun parseValue(): Any? {
            skipWs()
            return when (val c = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> if (c == '-' || c in '0'..'9') parseNumber() else error("Unexpected '$c' at $i")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            consume('{')
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') { i++; return map }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                consume(':')
                map[key] = parseValue()
                skipWs()
                when (consumeOne()) {
                    ',' -> continue
                    '}' -> break
                    else -> error("Expected ',' or '}' at $i")
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            consume('[')
            val list = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') { i++; return list }
            while (true) {
                list.add(parseValue())
                skipWs()
                when (consumeOne()) {
                    ',' -> continue
                    ']' -> break
                    else -> error("Expected ',' or ']' at $i")
                }
            }
            return list
        }

        private fun parseString(): String {
            consume('"')
            val sb = StringBuilder()
            while (true) {
                val c = consumeOne()
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = consumeOne()) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'f' -> sb.append(FORM_FEED)
                        'u' -> {
                            if (i + 4 > s.length) error("Bad \\u escape at $i")
                            val hex = s.substring(i, i + 4)
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                        }
                        else -> error("Bad escape '\\$e' at $i")
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = i
            if (peek() == '-') i++
            while (i < s.length && s[i] in '0'..'9') i++
            if (i < s.length && s[i] == '.') { i++; while (i < s.length && s[i] in '0'..'9') i++ }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i] in '0'..'9') i++
            }
            return s.substring(start, i).toDoubleOrNull() ?: error("Invalid number at $start")
        }

        private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun peek(): Char = if (i < s.length) s[i] else error("Unexpected end of input")
        private fun consumeOne(): Char = peek().also { i++ }
        private fun consume(expected: Char) { if (peek() != expected) error("Expected '$expected' at $i"); i++ }
        private fun expect(literal: String) {
            if (!s.startsWith(literal, i)) error("Expected '$literal' at $i")
            i += literal.length
        }
    }
}
