package queue

import model.Message
import model.MsgKind
import model.MsgStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageQueueTest {

    private fun msg(id: String, ts: Long = 0L) = Message(id = id, kind = MsgKind.USER_RECORD, body = "b", ts = ts)

    private fun queue(timeoutMs: Long = 30_000L, maxAttempts: Int = 2) =
        MessageQueue(InMemoryQueueStore(), timeoutMs, maxAttempts).also { it.restore() }

    @Test
    fun enqueueDedupsById() {
        val q = queue()
        assertTrue(q.enqueue(msg("a")))
        assertFalse(q.enqueue(msg("a")))
        assertEquals(1, q.all().size)
    }

    @Test
    fun dueMarksSendingAndCountsAttempt() {
        val q = queue()
        q.enqueue(msg("a", ts = 1000))
        val due = q.due(nowMs = 1000)
        assertEquals(1, due.size)
        assertEquals(MsgStatus.SENDING, due[0].status)
        // 未超时的 SENDING 不再投递
        assertEquals(0, q.due(nowMs = 20_000).size)
    }

    @Test
    fun successRemovesFromPipeline() {
        val q = queue()
        q.enqueue(msg("a"))
        q.due(0)
        q.markSent("a")
        assertEquals(0, q.all().size)
    }

    @Test
    fun timeoutThenRetryThenFail() {
        val q = queue(maxAttempts = 2)
        q.enqueue(msg("a", ts = 0))
        q.due(0)                    // 尝试1
        val retry = q.due(30_000)   // 超时重试
        assertEquals(1, retry.size)
        q.markFailed("a")           // 第2次失败 -> 终态
        assertEquals(MsgStatus.FAILED, q.failed().single().status)
        assertEquals(0, q.due(999_999).size) // FAILED 不再投递
    }

    @Test
    fun failThenRetryBeforeMax() {
        val q = queue(maxAttempts = 3)
        q.enqueue(msg("a"))
        q.due(0)
        q.markFailed("a")           // 尝试1失败, attempts=1 < 3 -> 回 PENDING
        val again = q.due(1)
        assertEquals(1, again.size)
        assertEquals(MsgStatus.SENDING, again[0].status)
    }

    @Test
    fun persistAndRestore() {
        val store = InMemoryQueueStore()
        val q1 = MessageQueue(store)
        q1.restore()
        q1.enqueue(msg("a"))
        q1.enqueue(msg("b"))
        q1.due(0)
        q1.markFailed("a")

        val q2 = MessageQueue(store)
        q2.restore()
        assertEquals(2, q2.all().size)
        assertTrue(store.snapshots.isNotEmpty())
    }
}
