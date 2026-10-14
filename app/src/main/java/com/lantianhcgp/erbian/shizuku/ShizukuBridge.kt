package com.lantianhcgp.erbian.shizuku

import com.lantianhcgp.erbian.util.Logger
import logic.WakeSettings
import rikka.shizuku.Shizuku

/**
 * Shizuku 桥（计划 Task 26）：读/写唤醒三件设置（经 shizuku shell: settings 命令）。
 * 不可用/未授权时所有 API 返回 null/empty，不抛异常（App 不闪退原则）。
 */
class ShizukuBridge(private val log: Logger) {

    fun isAvailable(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        isAvailable() && Shizuku.checkSelfPermission() == Shizuku.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun readWakeSettings(): WakeSettings? {
        if (!hasPermission()) return null
        return WakeSettings(
            singleKeyRule = getSetting("system", WakeSettings.KEY_RULE),
            aospBehavior = getSetting("global", WakeSettings.KEY_BEHAVIOR)?.toIntOrNull(),
            assistant = getSetting("secure", WakeSettings.KEY_ASSISTANT),
            voiceInteraction = getSetting("secure", WakeSettings.KEY_VOICE_INTERACTION),
        )
    }

    /** 按 diff 写回；返回每个键的执行结果（键 to "ok"/错误摘要）。 */
    fun repair(ourAssistant: String): Map<String, String> {
        val current = readWakeSettings() ?: return mapOf("*" to "shizuku unavailable")
        val plan = current.repairPlan(ourAssistant)
        if (plan.isEmpty()) return emptyMap()
        val nsOf = mapOf(
            WakeSettings.KEY_RULE to "system",
            WakeSettings.KEY_BEHAVIOR to "global",
            WakeSettings.KEY_ASSISTANT to "secure",
            WakeSettings.KEY_VOICE_INTERACTION to "secure",
        )
        val results = mutableMapOf<String, String>()
        for ((key, value) in plan) {
            val ns = nsOf.getValue(key)
            val res = runShell("settings put $ns $key $value")
            results[key] = if (res.first == 0) "ok" else "fail: ${res.second.ifEmpty { res.first.toString() }}"
            log.i("shizuku", "put $ns $key = $value -> ${results[key]}")
        }
        return results
    }

    fun runShell(cmd: String): Pair<Int, String> = runCatching {
        // Shizuku 13.x 里 newProcess 是私有的，反射调用（与 rish 同一底层）
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8).trim()
        val err = p.errorStream.readBytes().toString(Charsets.UTF_8).trim()
        p.waitFor()
        p.exitValue() to (if (out.isNotEmpty()) out else err)
    }.getOrElse { -1 to (it.message ?: "exception") }

    private fun getSetting(ns: String, key: String): String? {
        val (code, out) = runShell("settings get $ns $key")
        if (code != 0) return null
        return out.trim().takeIf { it.isNotEmpty() && it != "null" }
    }
}
