package model

import json.Json
import json.JsonValue

/** 结构化待办（Agent 提取，App 写日历用）。 */
data class Todo(
    val title: String,
    val startMillis: Long?,   // null = 全天待定日
    val allDay: Boolean,
    val desc: String,
) {
    fun toJson(): JsonValue = Json.obj(
        "title" to JsonValue.Str(title),
        "start" to JsonValue.Num((startMillis ?: -1L).toDouble()),
        "allDay" to JsonValue.Bool(allDay),
        "desc" to JsonValue.Str(desc),
    )

    companion object {
        fun fromJson(v: JsonValue): Todo {
            val start = v.optLong("start", -1L)
            return Todo(
                title = v.optString("title"),
                startMillis = if (start < 0) null else start,
                // 缺失时：无时间戳的待办按全天处理
                allDay = if (v.opt("allDay") == null) true else v.optBool("allDay"),
                desc = v.optString("desc"),
            )
        }
    }
}

/**
 * 下行消息（Agent → App POST /api/message，计划 Task 11）。
 */
data class InboundMessage(
    val id: String,
    val kind: MsgKind,
    val body: String,
    val ts: Long,
    val todo: Todo? = null,
) {
    fun toMessage(): Message = Message(
        id = id, kind = kind, body = body, ts = ts, status = MsgStatus.PENDING,
        meta = todo?.let { mapOf("todo" to it.toJson()) } ?: emptyMap(),
    )

    companion object {
        fun fromJson(v: JsonValue): InboundMessage = InboundMessage(
            id = v.optString("id"),
            kind = MsgKind.fromWire(v.optString("kind", "reply")),
            body = v.optString("body"),
            ts = v.optLong("ts"),
            todo = v.opt("todo")?.let { if (it is JsonValue.Null) null else Todo.fromJson(it) },
        )
    }
}
