package logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeSettingsTest {

    private val ours = "com.lantianhcgp.erbian/com.lantianhcgp.erbian.voice.AssistInteractionService"

    @Test
    fun healthyWhenAllMatched() {
        val s = WakeSettings("none", 5, ours, ours)
        assertTrue(s.isHealthy(ours))
        assertEquals(emptyMap<String, String>(), s.repairPlan(ours))
    }

    @Test
    fun xiaoaiStateNeedsFullPlan() {
        // 设备原始状态（逆向实测值）
        val s = WakeSettings("launch_voice_assistant", null, "com.miui.voiceassist/x", "com.miui.voiceassist/x")
        assertFalse(s.isHealthy(ours))
        val plan = s.repairPlan(ours)
        assertEquals("none", plan[WakeSettings.KEY_RULE])
        assertEquals("5", plan[WakeSettings.KEY_BEHAVIOR])
        assertEquals(ours, plan[WakeSettings.KEY_ASSISTANT])
        assertEquals(ours, plan[WakeSettings.KEY_VOICE_INTERACTION])
    }

    @Test
    fun partialFixOnlyWritesDiff() {
        val s = WakeSettings("none", 5, "com.miui.voiceassist/x", "com.miui.voiceassist/x")
        val plan = s.repairPlan(ours)
        assertEquals(setOf(WakeSettings.KEY_ASSISTANT, WakeSettings.KEY_VOICE_INTERACTION), plan.keys)
    }

    @Test
    fun describeContainsValues() {
        val text = WakeSettings(null, null, null, null).describe()
        assertTrue(text.contains("未设置"))
    }
}
