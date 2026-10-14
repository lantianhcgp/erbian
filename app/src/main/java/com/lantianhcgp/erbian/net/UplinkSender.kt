package com.lantianhcgp.erbian.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import model.Message
import model.MsgStatus
import net.Signature
import net.UplinkContract
import queue.MessageQueue
import java.net.HttpURLConnection
import java.net.URL

/**
 * 上行驱动（设计文档 §三）：周期扫描队列 → webhook POST（HMAC 签名）→ 状态回写。
 * 网络细节全部在这里；core 的 MessageQueue 只管状态机。
 */
class UplinkSender(
    private val queue: MessageQueue,
    private val config: () -> String,          // uplinkUrl
    private val secret: () -> String,          // hmac secret
    private val onStatus: suspend (id: String, status: MsgStatus) -> Unit,
    private val log: (String) -> Unit,
    scope: CoroutineScope,
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runOnce()
                delay(2_000)
            }
        }
    }

    fun stop() { job?.cancel(); job = null }

    /** 单轮扫描投递（测试可直接调用）。 */
    suspend fun runOnce() {
        val now = System.currentTimeMillis()
        for (m in queue.due(now)) {
            deliver(m)
        }
    }

    private suspend fun deliver(m: Message) {
        val body = UplinkContract.bodyJson(m.id, m.body, m.origin.ifEmpty { UplinkContract.ORIGIN_CHANNEL }, m.ts)
        try {
            val code = post(config(), body, secret())
            if (code in 200..299) {
                queue.markSent(m.id)
                onStatus(m.id, MsgStatus.SENT)
                log("uplink sent id=${m.id} code=$code")
            } else {
                throw IllegalStateException("http $code")
            }
        } catch (t: Throwable) {
            queue.markFailed(m.id)
            onStatus(m.id, if (queue.failed().any { it.id == m.id }) MsgStatus.FAILED else MsgStatus.PENDING)
            log("uplink FAIL id=${m.id}: ${t.message}")
        }
    }

    private fun post(url: String, body: String, secret: String): Int {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (secret.isNotEmpty()) {
                conn.setRequestProperty(Signature.HEADER, Signature.hmacSha256Hex(secret, body))
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            return conn.responseCode
        } finally {
            conn.disconnect()
        }
    }
}
