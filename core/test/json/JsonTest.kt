package json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun roundTripNested() {
        val src = Json.obj(
            "id" to JsonValue.Str("m-1"),
            "ts" to JsonValue.Num(1760000000000.0),
            "ok" to JsonValue.Bool(true),
            "none" to JsonValue.Null,
            "meta" to Json.obj("k" to JsonValue.Num(3.5)),
            "tags" to JsonValue.Arr(listOf(JsonValue.Str("a"), JsonValue.Num(2.0), JsonValue.Bool(false))),
        )
        val text = Json.write(src)
        val back = Json.parse(text)
        assertEquals(src, back)
        assertEquals("m-1", back.optString("id"))
        assertEquals(1760000000000L, back.optLong("ts"))
        assertTrue(back.optBool("ok"))
        assertEquals(3.5, back.opt("meta")!!.asObject()["k"]!!.asDouble(), 0.0)
        assertEquals(3, back.opt("tags")!!.asArray().size)
    }

    @Test
    fun escapes() {
        val s = "引号\" 反斜杠\\ 换行\n 制表\t 控制\u0001 emoji😀"
        val text = Json.write(Json.obj("s" to JsonValue.Str(s)))
        assertTrue(text.contains("\\\""))
        assertTrue(text.contains("\\\\"))
        assertTrue(text.contains("\\n"))
        assertTrue(text.contains("\\u0001"))
        assertEquals(s, Json.parse(text).optString("s"))
    }

    @Test
    fun numberFormats() {
        assertEquals(42L, Json.parse("42").asDouble().toLong())
        assertEquals(-7.25, Json.parse("-7.25").asDouble(), 0.0)
        assertEquals(1000.0, Json.parse("1e3").asDouble(), 0.0)
        assertEquals("42", Json.write(JsonValue.Num(42.0)))
        assertEquals("0.5", Json.write(JsonValue.Num(0.5)))
    }

    @Test
    fun whitespaceAndEmptyContainers() {
        val v = Json.parse("  {  \"a\" : [ ] , \"b\": { } }  ")
        assertEquals(0, v.opt("a")!!.asArray().size)
        assertEquals(0, v.opt("b")!!.asObject().size)
    }

    @Test
    fun unicodeEscapeParsing() {
        assertEquals("中A", Json.parse("\"\\u4e2d\\u0041\"").asString())
    }

    @Test(expected = JsonException::class)
    fun unterminatedString() { Json.parse("\"abc") }

    @Test(expected = JsonException::class)
    fun trailingGarbage() { Json.parse("{} extra") }

    @Test(expected = JsonException::class)
    fun badLiteral() { Json.parse("truex") }

    @Test(expected = JsonException::class)
    fun missingColon() { Json.parse("{\"a\" 1}") }

    @Test
    fun optDefaults() {
        val v = Json.parse("{}")
        assertEquals("", v.optString("nope"))
        assertEquals(9L, v.optLong("nope", 9L))
        assertEquals(false, v.optBool("nope"))
        assertNull(v.opt("nope"))
    }
}
