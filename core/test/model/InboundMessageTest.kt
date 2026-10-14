package model

import json.Json
import json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InboundMessageTest {

    @Test
    fun roundTripWithTodo() {
        val src = """{"id":"r1","kind":"todo_confirm","body":"加这个日程吗","ts":99,
            "todo":{"title":"修自行车","start":1760100000000,"allDay":false,"desc":"顺路去店家"}}"""
        val inb = InboundMessage.fromJson(Json.parse(src))
        assertEquals("r1", inb.id)
        assertEquals(MsgKind.TODO_CONFIRM, inb.kind)
        val todo = inb.todo!!
        assertEquals("修自行车", todo.title)
        assertEquals(1760100000000L, todo.startMillis)
        assertEquals(false, todo.allDay)
        // toMessage 时 todo 落 meta
        val m = inb.toMessage()
        assertEquals("修自行车", Todo.fromJson(m.meta["todo"]!!).title)
    }

    @Test
    fun withoutTodo() {
        val inb = InboundMessage.fromJson(Json.parse("{\"id\":\"r2\",\"kind\":\"reply\",\"body\":\"好\",\"ts\":1}"))
        assertNull(inb.todo)
        assertEquals(emptyMap<String, JsonValue>(), inb.toMessage().meta)
    }

    @Test
    fun todoDefaults() {
        val t = Todo.fromJson(Json.parse("{\"title\":\"X\"}"))
        assertNull(t.startMillis)
        assertEquals(true, t.allDay)
    }
}
