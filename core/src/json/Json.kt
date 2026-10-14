package json

/**
 * 极简 JSON 值模型。零外部依赖（core 模块要求可在纯 JVM 下 kotlinc+JUnit 独立测试）。
 * 数字统一用 Double 存储；时间戳（毫秒）在 2^53 内可精确表示。
 */
sealed class JsonValue {
    object Null : JsonValue()
    data class Bool(val v: Boolean) : JsonValue()
    data class Num(val v: Double) : JsonValue()
    data class Str(val v: String) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue()

    fun asString(): String = (this as? Str)?.v ?: throw JsonException("not a string: $this")
    fun asDouble(): Double = (this as? Num)?.v ?: throw JsonException("not a number: $this")
    fun asLong(): Long = asDouble().toLong()
    fun asBool(): Boolean = (this as? Bool)?.v ?: throw JsonException("not a bool: $this")
    fun asArray(): List<JsonValue> = (this as? Arr)?.items ?: throw JsonException("not an array: $this")
    fun asObject(): Map<String, JsonValue> = (this as? Obj)?.fields ?: throw JsonException("not an object: $this")

    fun opt(key: String): JsonValue? = (this as? Obj)?.fields?.get(key)
    fun optString(key: String, default: String = ""): String = opt(key)?.let { if (it is JsonValue.Null) default else it.asString() } ?: default
    fun optLong(key: String, default: Long = 0L): Long = opt(key)?.let { if (it is JsonValue.Null) default else it.asDouble().toLong() } ?: default
    fun optInt(key: String, default: Int = 0): Int = optLong(key, default.toLong()).toInt()
    fun optBool(key: String, default: Boolean = false): Boolean = opt(key)?.let { if (it is JsonValue.Null) default else it.asBool() } ?: default
}

class JsonException(message: String) : Exception(message)

object Json {

    fun obj(vararg pairs: Pair<String, JsonValue>): JsonValue.Obj =
        JsonValue.Obj(linkedMapOf(*pairs))

    fun parse(text: String): JsonValue {
        val p = Parser(text)
        val v = p.parseValue()
        p.skipWs()
        if (!p.atEnd()) throw JsonException("trailing characters at ${p.pos}")
        return v
    }

    fun write(v: JsonValue): String = StringBuilder().also { writeValue(v, it) }.toString()

    private fun writeValue(v: JsonValue, sb: StringBuilder) {
        when (v) {
            is JsonValue.Null -> sb.append("null")
            is JsonValue.Bool -> sb.append(if (v.v) "true" else "false")
            is JsonValue.Num -> sb.append(formatNumber(v.v))
            is JsonValue.Str -> writeString(v.v, sb)
            is JsonValue.Arr -> {
                sb.append('[')
                v.items.forEachIndexed { i, item ->
                    if (i > 0) sb.append(',')
                    writeValue(item, sb)
                }
                sb.append(']')
            }
            is JsonValue.Obj -> {
                sb.append('{')
                var first = true
                for ((k, item) in v.fields) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(k, sb)
                    sb.append(':')
                    writeValue(item, sb)
                }
                sb.append('}')
            }
        }
    }

    private fun formatNumber(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "0"
        val asLong = d.toLong()
        if (asLong.toDouble() == d && Math.abs(d) < 9007199254740992.0) return asLong.toString()
        return d.toString()
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c == '\b' -> sb.append("\\b")
                c == '\u000C' -> sb.append("\\f")
                c.code < 0x20 -> sb.append("\\u").append(String.format("%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun atEnd(): Boolean = pos >= s.length

        fun skipWs() {
            while (pos < s.length && s[pos].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) pos++
        }

        private fun peek(): Char {
            if (atEnd()) throw JsonException("unexpected end of input")
            return s[pos]
        }

        private fun expect(c: Char) {
            if (atEnd() || s[pos] != c) throw JsonException("expected '$c' at $pos")
            pos++
        }

        fun parseValue(): JsonValue {
            skipWs()
            return when (peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't' -> { literal("true"); JsonValue.Bool(true) }
                'f' -> { literal("false"); JsonValue.Bool(false) }
                'n' -> { literal("null"); JsonValue.Null }
                else -> parseNumber()
            }
        }

        private fun literal(word: String) {
            if (!s.startsWith(word, pos)) throw JsonException("invalid literal at $pos")
            pos += word.length
        }

        private fun parseObject(): JsonValue.Obj {
            expect('{')
            val fields = linkedMapOf<String, JsonValue>()
            skipWs()
            if (!atEnd() && peek() == '}') { pos++; return JsonValue.Obj(fields) }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                fields[key] = parseValue()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return JsonValue.Obj(fields) }
                    else -> throw JsonException("expected ',' or '}' at $pos")
                }
            }
        }

        private fun parseArray(): JsonValue.Arr {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWs()
            if (!atEnd() && peek() == ']') { pos++; return JsonValue.Arr(items) }
            while (true) {
                items.add(parseValue())
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return JsonValue.Arr(items) }
                    else -> throw JsonException("expected ',' or ']' at $pos")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonException("unterminated string")
                val c = s[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (atEnd()) throw JsonException("unterminated escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonException("bad unicode escape at $pos")
                                val hex = s.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: throw JsonException("bad unicode escape '$hex'")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw JsonException("bad escape '\\$e' at ${pos - 1}")
                        }
                    }
                    c.code < 0x20 -> throw JsonException("unescaped control char at ${pos - 1}")
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): JsonValue.Num {
            val start = pos
            if (!atEnd() && (peek() == '-' || peek() == '+')) pos++
            while (!atEnd() && s[pos].let { it.isDigit() || it == '.' || it == 'e' || it == 'E' || it == '-' || it == '+' }) pos++
            val text = s.substring(start, pos)
            val d = text.toDoubleOrNull() ?: throw JsonException("invalid number '$text' at $start")
            return JsonValue.Num(d)
        }
    }
}
