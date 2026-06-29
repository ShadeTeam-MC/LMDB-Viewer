package team.shade.lmdbviewer.decode

import java.nio.charset.StandardCharsets

/**
 * Detects values that are valid JSON and pretty-prints them. Highest priority so structured data
 * wins over plain text in auto-detect. Uses a small dependency-free validator/formatter to keep the
 * `decode` package free of external libraries.
 */
class JsonDecoder : ByteDecoder {
    override val id: String = "json"
    override val displayName: String = "JSON"
    override val priority: Int = 80

    override fun canDecode(bytes: ByteArray): Boolean {
        val text = decodeStrict(bytes)?.trim() ?: return false
        if (text.isEmpty()) return false
        // Quick reject before the full parse.
        val first = text.first()
        if (first != '{' && first != '[') return false
        return runCatching { JsonFormatter(text).parseAndFormat() }.isSuccess
    }

    override fun decode(bytes: ByteArray): DecodedView {
        val text = String(bytes, StandardCharsets.UTF_8)
        val pretty = runCatching { JsonFormatter(text.trim()).parseAndFormat() }.getOrElse { text }
        return DecodedView(pretty, monospace = true)
    }
}

/**
 * Minimal recursive-descent JSON parser that re-emits the document with 2-space indentation.
 * Throws on malformed input (used both for validation and formatting).
 */
private class JsonFormatter(private val s: String) {
    private var i = 0
    private val out = StringBuilder()
    private var indent = 0

    fun parseAndFormat(): String {
        skipWs()
        parseValue()
        skipWs()
        if (i != s.length) error("Trailing content at $i")
        return out.toString()
    }

    private fun parseValue() {
        skipWs()
        when (val c = peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> out.append(parseString())
            't' -> { expect("true"); out.append("true") }
            'f' -> { expect("false"); out.append("false") }
            'n' -> { expect("null"); out.append("null") }
            else -> if (c == '-' || c in '0'..'9') parseNumber() else error("Unexpected '$c' at $i")
        }
    }

    private fun parseObject() {
        consume('{')
        skipWs()
        if (peek() == '}') { consume('}'); out.append("{}"); return }
        out.append("{\n")
        indent++
        var first = true
        while (true) {
            skipWs()
            if (!first) { out.append(",\n") }
            first = false
            appendIndent()
            out.append(parseString())
            skipWs()
            consume(':')
            out.append(": ")
            parseValue()
            skipWs()
            when (consumeOne()) {
                ',' -> continue
                '}' -> break
                else -> error("Expected ',' or '}' at $i")
            }
        }
        indent--
        out.append('\n')
        appendIndent()
        out.append('}')
    }

    private fun parseArray() {
        consume('[')
        skipWs()
        if (peek() == ']') { consume(']'); out.append("[]"); return }
        out.append("[\n")
        indent++
        var first = true
        while (true) {
            if (!first) { out.append(",\n") }
            first = false
            appendIndent()
            parseValue()
            skipWs()
            when (consumeOne()) {
                ',' -> continue
                ']' -> break
                else -> error("Expected ',' or ']' at $i")
            }
        }
        indent--
        out.append('\n')
        appendIndent()
        out.append(']')
    }

    private fun parseString(): String {
        val start = i
        consume('"')
        val sb = StringBuilder("\"")
        while (true) {
            val c = consumeOne()
            sb.append(c)
            when (c) {
                '"' -> return sb.toString()
                '\\' -> sb.append(consumeOne()) // keep escape sequence verbatim
            }
            if (i > s.length) error("Unterminated string from $start")
        }
    }

    private fun parseNumber() {
        val start = i
        if (peek() == '-') i++
        while (i < s.length && s[i] in '0'..'9') i++
        if (i < s.length && s[i] == '.') { i++; while (i < s.length && s[i] in '0'..'9') i++ }
        if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
            i++
            if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
            while (i < s.length && s[i] in '0'..'9') i++
        }
        val num = s.substring(start, i)
        if (num.isEmpty() || num == "-") error("Invalid number at $start")
        out.append(num)
    }

    private fun appendIndent() = repeat(indent) { out.append("  ") }
    private fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
    private fun peek(): Char = if (i < s.length) s[i] else error("Unexpected end of input")
    private fun consumeOne(): Char = peek().also { i++ }
    private fun consume(expected: Char) { if (peek() != expected) error("Expected '$expected' at $i"); i++ }
    private fun expect(literal: String) {
        if (!s.startsWith(literal, i)) error("Expected '$literal' at $i")
        i += literal.length
    }
}
