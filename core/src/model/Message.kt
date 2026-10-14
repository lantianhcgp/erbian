package model

import json.Json
import json.JsonValue

enum class MsgKind {
    USER_RECORD, REPLY, PROACTIVE, SUMMARY, TODO_CONFIRM, TODO_RESULT, ERROR_NOTICE;

    companion object {
        fun fromWire(s: String): MsgKind = when (s) {
            "user_record" -> USER_RECORD
            "reply" -> REPLY
            "proactive" -> PROACTIVE
            "summary" -> SUMMARY
            "todo_confirm" -> TODO_CONFIRM
            "todo_result" -> TODO_RESULT
            "error_notice" -> ERROR_NOTICE
            else -> throw json.JsonException("unknown kind '$s'")
        }
    }

    fun toWire(): String = when (this) {
        USER_RECORD -> "user_record"
        REPLY -> "reply"
        PROACTIVE -> "proactive"
        SUMMARY -> "summary"
        TODO_CONFIRM -> "todo_confirm"
        TODO_RESULT -> "todo_result"
        ERROR_NOTICE -> "error_notice"
    }
}

enum class MsgStatus { PENDING, SENDING, SENT, FAILED }

/**
 * 频道消息（App 与 Agent 通用 schema，设计文档 §三）。
 * meta 保持松散（todo_id/confidence/origin 等自由键）。
 */
data class Message(
    val id: String,
    val kind: MsgKind,
    val body: String,
    val ts: Long,
    val status: MsgStatus = MsgStatus.PENDING,
    val origin: String = "",
    val meta: Map<String, JsonValue> = emptyMap(),
) {
    fun toJson(): JsonValue = Json.obj(
        "id" to JsonValue.Str(id),
        "kind" to JsonValue.Str(kind.toWire()),
        "body" to JsonValue.Str(body),
        "ts" to JsonValue.Num(ts.toDouble()),
        "status" to JsonValue.Str(status.name.lowercase()),
        "origin" to JsonValue.Str(origin),
        "meta" to JsonValue.Obj(meta),
    )

    fun withStatus(s: MsgStatus): Message = copy(status = s)

    companion object {
        fun fromJson(v: JsonValue): Message = Message(
            id = v.optString("id"),
            kind = MsgKind.fromWire(v.optString("kind", "reply")),
            body = v.optString("body"),
            ts = v.optLong("ts"),
            status = when (v.optString("status", "pending")) {
                "pending" -> MsgStatus.PENDING
                "sending" -> MsgStatus.SENDING
                "sent" -> MsgStatus.SENT
                "failed" -> MsgStatus.FAILED
                else -> MsgStatus.PENDING
            },
            origin = v.optString("origin"),
            meta = v.opt("meta")?.let { if (it is JsonValue.Null) emptyMap() else it.asObject() } ?: emptyMap(),
        )
    }
}
