package logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryThrottleTest {

    @Test
    fun firstRequestAlwaysGoes() {
        val t = SummaryThrottle()
        assertTrue(t.shouldRequest(nowMs = 1_000))
    }

    @Test
    fun windowBlocksSecondRequest() {
        val t = SummaryThrottle()
        assertTrue(t.shouldRequest(1_000))
        assertFalse(t.shouldRequest(29_999))
        assertFalse(t.shouldRequest(30_999)) // 距上次 29_999ms < 30s
    }

    @Test
    fun windowAllowsAfterExpiry() {
        val t = SummaryThrottle()
        t.shouldRequest(1_000)
        assertTrue(t.shouldRequest(31_001))
    }
}
