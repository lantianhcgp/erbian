package model

import json.Json
import json.JsonException
import json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTest {

    @Test
    fun roundTripAllKinds() {
        for (k in MsgKind.entries) {
            val m = Message(
                id = "id-${k.name}", kind = k, body = "内容\"带\"引号\n换行", ts = 123L,
                status = MsgStatus.SENDING, origin = "float",
                meta = mapOf("todo_id" to JsonValue.Str("t1"), "confidence" to JsonValue.Num(0.9)),
            )
            val back = Message.fromJson(Json.parse(Json.write(m.toJson())))
            assertEquals(m, back)
        }
    }

    @Test
    fun wireNames() {
        assertEquals("user_record", MsgKind.USER_RECORD.toWire())
        assertEquals(MsgKind.TODO_CONFIRM, MsgKind.fromWire("todo_confirm"))
    }

    @Test(expected = JsonException::class)
    fun unknownKindThrows() { MsgKind.fromWire("nope") }

    @Test
    fun defaultsOnMissingFields() {
        val m = Message.fromJson(Json.parse("{}"))
        assertEquals("", m.id)
        assertEquals(MsgKind.REPLY, m.kind)
        assertEquals(MsgStatus.PENDING, m.status)
        assertEquals(emptyMap<String, JsonValue>(), m.meta)
    }

    @Test
    fun nullMetaTreatedEmpty() {
        val m = Message.fromJson(Json.parse("{\"meta\":null}"))
        assertEquals(emptyMap<String, JsonValue>(), m.meta)
    }
}
