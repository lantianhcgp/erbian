package com.lantianhcgp.erbian

import android.app.Application
import com.lantianhcgp.erbian.data.MessageRepository
import com.lantianhcgp.erbian.net.UplinkSender
import com.lantianhcgp.erbian.shizuku.ShizukuBridge
import com.lantianhcgp.erbian.server.ErbianServer
import com.lantianhcgp.erbian.util.AppConfig
import com.lantianhcgp.erbian.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import model.Message
import model.MsgKind
import model.MsgStatus
import queue.MessageQueue
import java.util.UUID

/** 手动 DI（YAGNI：不引 Hilt）。 */
object Locator {
    lateinit var app: ErbianApp
        internal set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val logger: Logger by lazy { Logger(app) }
    val config: AppConfig by lazy { AppConfig(app) }
    val repository: MessageRepository by lazy { MessageRepository(app, scope) }
    val queue: MessageQueue by lazy { MessageQueue(repository.queueStore).also { it.restore() } }
    val shizuku: ShizukuBridge by lazy { ShizukuBridge(logger) }
    val uplink: UplinkSender by lazy {
        UplinkSender(
            queue = queue,
            config = { config.uplinkUrl },
            secret = { config.uplinkSecret },
            onStatus = { id, st -> repository.setStatus(id, st) },
            log = { logger.i("uplink", it) },
            scope = scope,
        )
    }
    val server: ErbianServer by lazy { ErbianServer() }

    /** 2026-10-16：/api/profile 写完通知 UI 重读（agent 改画像后页面不刷新的修复）。 */
    @Volatile
    var onProfileChanged: (() -> Unit)? = null

    fun appFiles() = app.filesDir

    /** Settings.Secure.assistant 目标组件（本应用）。 */
    fun assistantComponent(): String =
        "com.lantianhcgp.erbian/com.lantianhcgp.erbian.voice.AssistInteractionService"

    /** 频道发送（浮层与频道页共用）：入频道库 + 入上行队列。 */
    suspend fun sendUserRecord(text: String, origin: String) {
        val m = Message(
            id = UUID.randomUUID().toString(),
            kind = MsgKind.USER_RECORD,
            body = text,
            ts = System.currentTimeMillis(),
            status = MsgStatus.PENDING,
            origin = origin,
        )
        repository.add(m)
        queue.enqueue(m)
        logger.i("send", "user_record id=${m.id} origin=$origin len=${text.length}")
    }
}

class ErbianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Locator.app = this
        Locator.logger.i("app", "ErbianApp onCreate v${BuildConfig.VERSION_NAME} process=${Application.getProcessName()}")

        // 只在主进程启动 上行通道 + 内嵌 server（:assist 进程只跑 VoiceInteraction 服务；
        // 双进程抢 8790 端口 → BindException 崩溃环，2026-10-14 真机实锤）
        val isMain = Application.getProcessName() == packageName
        if (isMain) {
            Locator.uplink.start(Locator.scope)
            Locator.scope.launch { runCatching { Locator.server.start(Locator) } }
            Locator.scope.launch { selfCheckWake() }
        }
    }

    /**
     * 唤醒设置自检自愈（设计文档 §二）：Shizuku 可用且三键不健康时写回。
     * Shizuku 未授权 = 静默跳过（设置页有红条引导，不闪退）。
     */
    private suspend fun selfCheckWake() {
        kotlinx.coroutines.delay(3_000)
        val shizuku = Locator.shizuku
        if (!shizuku.hasPermission()) {
            Locator.logger.i("selfcheck", "shizuku unavailable, skip wake self-check")
            return
        }
        val current = shizuku.readWakeSettings() ?: return
        val our = Locator.assistantComponent()
        if (current.isHealthy(our)) {
            Locator.logger.i("selfcheck", "wake settings healthy")
            return
        }
        Locator.logger.w("selfcheck", "wake settings unhealthy: ${current.describe()} -> repairing")
        val results = shizuku.repair(our)
        val ok = results.values.all { it == "ok" }
        Locator.repository.add(
            Message(
                id = java.util.UUID.randomUUID().toString(),
                kind = if (ok) model.MsgKind.PROACTIVE else model.MsgKind.ERROR_NOTICE,
                body = if (ok) "已修复唤醒设置（长按电源键已指向耳边）" else "唤醒设置修复失败：$results",
                ts = System.currentTimeMillis(),
                status = model.MsgStatus.PENDING,
                origin = "system",
            )
        )
    }
}
