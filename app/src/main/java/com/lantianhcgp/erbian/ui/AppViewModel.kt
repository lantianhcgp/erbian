package com.lantianhcgp.erbian.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lantianhcgp.erbian.Locator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logic.WakeSettings
import model.Message
import model.MsgStatus

/** UI 状态与动作（四页共用）。 */
class AppViewModel : ViewModel() {

    val channel = Locator.repository.channel

    var wakeSettings by mutableStateOf<WakeSettings?>(null)
        private set
    var wakeHealthy by mutableStateOf(false)
        private set
    var repairResult by mutableStateOf<Map<String, String>?>(null)
        private set
    var shizukuAvailable by mutableStateOf(false)
        private set
    var profileJson by mutableStateOf<String?>(null)
        private set
    var uplinkTest by mutableStateOf<String?>(null)
        private set

    /** F3：agent 正在处理（发送后置位，收到回信/超时清零）——频道页 typing 指示。 */
    var agentProcessing by mutableStateOf(false)
        private set
    /** F4：设置页一键 ping Agent 端口（8644）的结果。 */
    var agentHealth by mutableStateOf<String?>(null)
        private set
    /** B4：Shizuku 授权状态（可观察，授权弹窗回来后自动刷新 UI）。 */
    var shizukuGranted by mutableStateOf(false)
        private set
    /** B5：待办删除结果提示。 */
    var todoNote by mutableStateOf<String?>(null)
        private set
    /** F1：日志诊断发送状态。 */
    var diagNote by mutableStateOf<String?>(null)
        private set

    init {
        // B1：agent 推送画像后立刻重读（原来只在 refresh() 读一次 → 页面永远显示旧画像）
        Locator.onProfileChanged = {
            viewModelScope.launch {
                profileJson = runCatching {
                    Locator.appFiles().resolve("profile.json").readText()
                }.getOrNull()
            }
        }
        // B4：注册 Shizuku 授权结果监听（一次，App 生命周期内）
        runCatching {
            Locator.shizuku.addPermissionListener { granted ->
                shizukuGranted = granted
                if (granted) refresh()
            }
        }
        shizukuGranted = Locator.shizuku.hasPermission()
        refresh()
        // F3：收到 agent 的入站消息即视为「回复到达」→ 关掉 typing
        viewModelScope.launch {
            channel.collect { list ->
                val last = list.lastOrNull()
                if (last != null && last.origin !in setOf("float", "channel")) agentProcessing = false
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            shizukuAvailable = Locator.shizuku.isAvailable()
            val s = Locator.shizuku.readWakeSettings()
            wakeSettings = s
            wakeHealthy = s?.isHealthy(Locator.assistantComponent()) ?: false
            profileJson = runCatching { Locator.appFiles().resolve("profile.json").readText() }.getOrNull()
        }
    }

    fun send(text: String, origin: String = "channel") {
        // F3：用户主动发话 → 显示「agent 处理中」，直到回信到达或 90s 兜底超时
        agentProcessing = true
        viewModelScope.launch {
            Locator.sendUserRecord(text, origin)
            delay(90_000)
            agentProcessing = false
        }
    }

    /** B4：发起 Shizuku 真实授权弹窗。 */
    fun requestShizuku() = Locator.shizuku.requestPermission(1001)

    /** F4：一键 ping Agent 侧 webhook 端口（127.0.0.1:8644）。 */
    fun pingAgent() {
        viewModelScope.launch {
            agentHealth = "ping 中…"
            val t0 = System.currentTimeMillis()
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val c = java.net.URL("http://127.0.0.1:8644/health")
                        .openConnection() as java.net.HttpURLConnection
                    c.connectTimeout = 4000
                    c.readTimeout = 4000
                    val code = c.responseCode
                    val body = c.inputStream.bufferedReader().readText().take(120)
                    c.disconnect()
                    "8644 HTTP $code · ${System.currentTimeMillis() - t0}ms · $body"
                }.getOrElse { "8644 不通：${it.javaClass.simpleName} ${it.message}" }
            }
            agentHealth = r
            logger.i("health", "ping agent -> $r")
        }
    }

    /** F1：一键把运行日志发给 agent 诊断（走既有上行队列/签名/重试链路）。 */
    fun sendLogDiagnostics() {
        viewModelScope.launch {
            val tail = withContext(Dispatchers.IO) {
                runCatching { logger.tail(200).joinToString("\n") }
                    .getOrDefault("(日志读取失败)")
            }
            if (tail.isBlank()) { diagNote = "没有可发送的日志"; return@launch }
            diagNote = "发送中…"
            runCatching {
                Locator.sendUserRecord(
                    "【日志诊断】请分析以下耳边 App 运行日志，找出异常并给出结论：\n$tail",
                    "logdiag",
                )
            }.onSuccess { diagNote = "已入队发送给 Agent（${tail.length} 字符）" }
             .onFailure { diagNote = "发送失败：${it.message}" }
        }
    }

    /** B5：删除日历待办（经 Shizuku shell 删事件）。无权限时给引导文案。 */
    fun deleteCalendarEvent(eventId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!Locator.shizuku.hasPermission()) {
                todoNote = "需要 Shizuku 授权：设置 → Shizuku 点此授权后重试"
                return@launch
            }
            val r = Locator.shizuku.runShell(
                "content delete --uri content://com.android.calendar/events/$eventId"
            )
            todoNote = if (r.first == 0) "待办已删除"
            else "删除失败：${r.second.ifEmpty { "code ${r.first}" }}"
            logger.i("todo", "delete event $eventId -> ${todoNote}")
        }
    }

    fun resend(m: Message) {
        viewModelScope.launch {
            Locator.repository.setStatus(m.id, MsgStatus.PENDING)
            Locator.queue.enqueue(m.copy(status = MsgStatus.PENDING))
        }
    }

    fun repairWake() {
        viewModelScope.launch {
            val r = Locator.shizuku.repair(Locator.assistantComponent())
            repairResult = r
            val s = Locator.shizuku.readWakeSettings()
            wakeSettings = s
            wakeHealthy = s?.isHealthy(Locator.assistantComponent()) ?: false
        }
    }

    fun testUplink() {
        viewModelScope.launch {
            uplinkTest = "测试中…"
            val before = Locator.queue.all().size
            runCatching { Locator.uplink.runOnce() }
            uplinkTest = "已触发一轮投递（队列 $before 条）"
        }
    }

    val config get() = Locator.config
    val logger get() = Locator.logger
    val queueInFlight get() = Locator.queue.inFlightCount()
    // 基于可观察 state（授权回调写入），UI 订阅即可刷新；不再每次实时查询
    val shizukuPermission get() = shizukuGranted
    val assistantComponent get() = Locator.assistantComponent()
}
