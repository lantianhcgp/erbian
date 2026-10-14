package logic

/**
 * 唤醒三件设置的状态与修复计划（计划 Task 10 / 设计文档 §二）。
 * 值语义与 MIUI/AOSP 实测一致（设计阶段已验证切换实验）。
 */
data class WakeSettings(
    val singleKeyRule: String?,      // Settings.System "long_press_power_key"
    val aospBehavior: Int?,          // Settings.Global "power_button_long_press"
    val assistant: String?,          // Settings.Secure "assistant"
    val voiceInteraction: String?,   // Settings.Secure "voice_interaction_service"
) {
    companion object {
        const val KEY_RULE = "long_press_power_key"
        const val KEY_BEHAVIOR = "power_button_long_press"
        const val KEY_ASSISTANT = "assistant"
        const val KEY_VOICE_INTERACTION = "voice_interaction_service"
        const val TARGET_RULE = "none"
        const val TARGET_BEHAVIOR = 5
    }

    fun isHealthy(ourAssistant: String): Boolean =
        singleKeyRule == TARGET_RULE &&
            aospBehavior == TARGET_BEHAVIOR &&
            assistant == ourAssistant &&
            voiceInteraction == ourAssistant

    /** 需要写回的键值对（healthy 时为空）。 */
    fun repairPlan(ourAssistant: String): Map<String, String> {
        if (isHealthy(ourAssistant)) return emptyMap()
        val plan = mutableMapOf<String, String>()
        if (singleKeyRule != TARGET_RULE) plan[KEY_RULE] = TARGET_RULE
        if (aospBehavior != TARGET_BEHAVIOR) plan[KEY_BEHAVIOR] = TARGET_BEHAVIOR.toString()
        if (assistant != ourAssistant) plan[KEY_ASSISTANT] = ourAssistant
        if (voiceInteraction != ourAssistant) plan[KEY_VOICE_INTERACTION] = ourAssistant
        return plan
    }

    /** 摘要文本（设置页展示）。 */
    fun describe(): String =
        "rule=${singleKeyRule ?: "未设置"} behavior=${aospBehavior ?: "未设置"} " +
            "assistant=${assistant ?: "未设置"} vi=${voiceInteraction ?: "未设置"}"
}
