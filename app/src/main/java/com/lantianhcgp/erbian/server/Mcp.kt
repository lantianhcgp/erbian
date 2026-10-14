package com.lantianhcgp.erbian.server

import com.lantianhcgp.erbian.Locator
import com.lantianhcgp.erbian.data.MessageRepository
import com.lantianhcgp.erbian.util.AppConfig
import json.Json
import json.JsonValue
import logic.WakeSettings
import model.Message
import model.MsgKind
import java.io.File

/**
 * MCP 服务端实现（JSON-RPC 2.0 子集，计划 Task 21 降级方案——完全自控零版本风险）。
 * 工具面：fs_list / fs_read / log_tail / channel_history / queue_status / settings_state / todo_state
 */
object Mcp {

    data class Tool(val name: String, val description: String, val schema: JsonValue)

    private fun strSchema(props: Map<String, String>, required: List<String> = emptyList()): JsonValue = Json.obj(
        "type" to JsonValue.Str("object"),
        "properties" to JsonValue.Obj(props.mapValues { (_, desc) ->
            Json.obj("type" to JsonValue.Str("string"), "description" to JsonValue.Str(desc))
        }),
        "required" to JsonValue.Arr(required.map { JsonValue.Str(it) }),
    )

    private val tools = listOf(
        Tool("fs_list", "List files under the app's private files directory (sandboxed).",
            strSchema(mapOf("path" to "Relative path under filesDir (default '.')"), listOf("path"))),
        Tool("fs_read", "Read a text file under filesDir (sandboxed).",
            strSchema(mapOf("path" to "Relative path", "maxBytes" to "max bytes to read (default 8192)"), listOf("path"))),
        Tool("log_tail", "Tail the app's runtime log (network/queue/self-check all recorded).",
            strSchema(mapOf("lines" to "number of trailing lines as string, default 200"))),
        Tool("channel_history", "Structured channel message history incl. send status.",
            strSchema(mapOf("limit" to "max messages, default 50"))),
        Tool("queue_status", "Uplink queue state (in-flight / failed entries).", Json.obj("type" to JsonValue.Str("object"), "properties" to JsonValue.Obj(emptyMap()))),
        Tool("settings_state", "Live wake-chain settings (3 keys) + Shizuku availability + health verdict.",
            Json.obj("type" to JsonValue.Str("object"), "properties" to JsonValue.Obj(emptyMap()))),
        Tool("todo_state", "Recent todo_confirm / todo_result messages with confirmation state.",
            strSchema(mapOf("limit" to "max entries, default 20"))),
    )

    /** 返回 (httpCode, jsonText)。 */
    fun handle(body: String, config: AppConfig, repo: MessageRepository, deps: Locator): Pair<Int, String> {
        val req = runCatching { Json.parse(body) }.getOrNull()
            ?: return 400 to rpcError(null, -32700, "parse error")
        val id = req.opt("id")?.let { if (it is JsonValue.Null) null else it }
        val method = req.optString("method")
        val params = req.opt("params") ?: JsonValue.Obj(emptyMap())

        // 通知（无 id）：不回包内容
        if (id == null && method.startsWith("notifications/")) return 202 to ""

        val result: JsonValue = when (method) {
            "initialize" -> Json.obj(
                "protocolVersion" to JsonValue.Str("2024-11-05"),
                "capabilities" to Json.obj("tools" to JsonValue.Obj(emptyMap())),
                "serverInfo" to Json.obj(
                    "name" to JsonValue.Str("erbian"),
                    "version" to JsonValue.Str("1.0"),
                ),
            )
            "ping" -> JsonValue.Obj(emptyMap())
            "tools/list" -> Json.obj(
                "tools" to JsonValue.Arr(tools.map {
                    Json.obj(
                        "name" to JsonValue.Str(it.name),
                        "description" to JsonValue.Str(it.description),
                        "inputSchema" to it.schema,
                    )
                })
            )
            "tools/call" -> {
                val name = params.optString("name")
                val args = params.opt("arguments") ?: JsonValue.Obj(emptyMap())
                val (ok, text) = runTool(name, args, repo, deps)
                Json.obj(
                    "content" to JsonValue.Arr(listOf(JsonValue.Obj(mapOf(
                        "type" to JsonValue.Str("text"),
                        "text" to JsonValue.Str(text),
                    )))),
                    "isError" to JsonValue.Bool(!ok),
                )
            }
            else -> return 404 to rpcError(id, -32601, "method not found: $method")
        }
        val resp = Json.obj(
            "jsonrpc" to JsonValue.Str("2.0"),
            "id" to (id ?: JsonValue.Null),
            "result" to result,
        )
        return 200 to Json.write(resp)
    }

    private fun rpcError(id: JsonValue?, code: Int, message: String): String = Json.write(
        Json.obj(
            "jsonrpc" to JsonValue.Str("2.0"),
            "id" to (id ?: JsonValue.Null),
            "error" to Json.obj(
                "code" to JsonValue.Num(code.toDouble()),
                "message" to JsonValue.Str(message),
            ),
        )
    )

    private fun runTool(name: String, args: JsonValue, repo: MessageRepository, deps: Locator): Pair<Boolean, String> {
        val filesDir = deps.appFiles()
        return try {
            when (name) {
                "fs_list" -> {
                    val dir = sandbox(filesDir, args.optString("path", "."))
                    val items = dir.listFiles()?.joinToString("\n") {
                        "${if (it.isDirectory) "dir " else "file"} ${it.name} ${if (it.isFile) it.length() else ""}"
                    } ?: "(empty)"
                    true to items
                }
                "fs_read" -> {
                    val f = sandbox(filesDir, args.optString("path"))
                    val max = args.optString("maxBytes", "8192").toIntOrNull() ?: 8192
                    val text = f.readText().take(max)
                    true to text
                }
                "log_tail" -> {
                    val n = args.optString("lines", "200").toIntOrNull() ?: 200
                    true to deps.logger.tail(n).joinToString("\n")
                }
                "channel_history" -> {
                    val n = args.optString("limit", "50").toIntOrNull() ?: 50
                    val msgs = repo.channel.value.takeLast(n)
                    true to Json.write(JsonValue.Arr(msgs.map { it.toJson() }))
                }
                "queue_status" -> {
                    val q = deps.queue
                    true to Json.write(Json.obj(
                        "in_flight" to JsonValue.Num(q.inFlightCount().toDouble()),
                        "failed" to JsonValue.Arr(q.failed().map { it.toJson() }),
                    ))
                }
                "settings_state" -> {
                    val r = deps.shizuku.readWakeSettings()
                    val desc = r?.let {
                        val health = it.isHealthy(Locator.assistantComponent())
                        Json.obj(
                            "values" to JsonValue.Obj(mapOf(
                                WakeSettings.KEY_RULE to JsonValue.Str(it.singleKeyRule ?: "null"),
                                WakeSettings.KEY_BEHAVIOR to JsonValue.Str(it.aospBehavior?.toString() ?: "null"),
                                WakeSettings.KEY_ASSISTANT to JsonValue.Str(it.assistant ?: "null"),
                                WakeSettings.KEY_VOICE_INTERACTION to JsonValue.Str(it.voiceInteraction ?: "null"),
                            )),
                            "healthy" to JsonValue.Bool(health),
                        ).let { o -> Json.write(o) }
                    } ?: Json.write(Json.obj(
                        "error" to JsonValue.Str("shizuku unavailable"),
                    ))
                    true to desc
                }
                "todo_state" -> {
                    val n = args.optString("limit", "20").toIntOrNull() ?: 20
                    val todos = repo.channel.value
                        .filter { it.kind == MsgKind.TODO_CONFIRM || it.kind == MsgKind.TODO_RESULT }
                        .takeLast(n)
                    true to Json.write(JsonValue.Arr(todos.map { it.toJson() }))
                }
                else -> false to "unknown tool: $name"
            }
        } catch (t: Throwable) {
            false to "${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun sandbox(base: File, rel: String): File {
        val clean = rel.trim().trimStart('/').ifEmpty { "." }
        val f = File(base, clean).canonicalFile
        if (!f.path.startsWith(base.canonicalFile.path)) throw SecurityException("path escapes sandbox: $rel")
        return f
    }
}
