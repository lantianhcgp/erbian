package com.lantianhcgp.erbian.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lantianhcgp.erbian.Locator
import kotlinx.coroutines.launch
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

    init {
        refresh()
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
        viewModelScope.launch { Locator.sendUserRecord(text, origin) }
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
    val queueInFlight get() = Locator.queue.inFlightCount()
    val shizukuPermission get() = Locator.shizuku.hasPermission()
    val assistantComponent get() = Locator.assistantComponent()
}
