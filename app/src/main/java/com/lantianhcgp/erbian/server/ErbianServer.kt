package com.lantianhcgp.erbian.server

import com.lantianhcgp.erbian.Locator
import com.lantianhcgp.erbian.data.MessageRepository
import com.lantianhcgp.erbian.util.AppConfig
import com.lantianhcgp.erbian.util.Logger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import json.Json
import json.JsonValue
import model.InboundMessage

/**
 * 内嵌服务端（0.0.0.0:8790，设计文档 §五）。
 * REST: /health /api/message /api/profile
 * MCP:  /mcp （JSON-RPC 2.0 子集：initialize / tools/list / tools/call）
 * 鉴权: api 路由与 /mcp 需要 Authorization: Bearer <serverToken>；/health 免鉴权。
 */
class ErbianServer {

    private var engine: io.ktor.server.engine.ApplicationEngine? = null

    fun start(deps: Locator) {
        val config = deps.config
        val log = deps.logger
        val repo = deps.repository
        engine?.stop(gracePeriodMillis = 100, timeoutMillis = 500)
        engine = embeddedServer(CIO, port = config.serverPort, host = "0.0.0.0") {
            routing {
                get("/health") {
                    call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
                }
                post("/api/message") {
                    if (!authorized(call.request.headers["Authorization"], config)) {
                        call.respondText("""{"error":"unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                        return@post
                    }
                    val body = call.receiveText()
                    handleInbound(body, repo, log)?.let { resp ->
                        call.respondText(resp, ContentType.Application.Json)
                    } ?: call.respondText("""{"error":"bad json"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
                post("/api/profile") {
                    if (!authorized(call.request.headers["Authorization"], config)) {
                        call.respondText("""{"error":"unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                        return@post
                    }
                    val body = call.receiveText()
                    runCatching { Json.parse(body) }.getOrElse {
                        call.respondText("""{"error":"bad json"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    deps.appFiles().resolve("profile.json").writeText(body)
                    log.i("server", "profile.json updated (${body.length} chars)")
                    call.respondText("""{"ok":true}""", ContentType.Application.Json)
                }
                post("/mcp") {
                    if (!config.mcpEnabled) {
                        call.respondText("Not Found", status = HttpStatusCode.NotFound)
                        return@post
                    }
                    if (!authorized(call.request.headers["Authorization"], config)) {
                        call.respondText("""{"error":"unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                        return@post
                    }
                    val body = call.receiveText()
                    val (code, text) = Mcp.handle(body, config, repo, deps)
                    call.respondText(text, ContentType.Application.Json, HttpStatusCode.fromValue(code))
                }
            }
        }.start(wait = false)
        log.i("server", "listening on 0.0.0.0:${config.serverPort}")
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 100, timeoutMillis = 500)
        engine = null
    }

    private fun authorized(header: String?, config: AppConfig): Boolean {
        val token = config.serverToken
        return header != null && header.startsWith("Bearer ") && header.removePrefix("Bearer ") == token
    }

    private suspend fun handleInbound(body: String, repo: MessageRepository, log: Logger): String? {
        val parsed = runCatching { Json.parse(body) }.getOrNull() ?: return null
        val inb = runCatching { InboundMessage.fromJson(parsed) }.getOrNull() ?: return null
        val inserted = repo.add(inb.toMessage())
        log.i("server", "inbound ${inb.kind.toWire()} id=${inb.id} inserted=$inserted todo=${inb.todo != null}")
        return """{"ok":true,"inserted":$inserted}"""
    }
}
