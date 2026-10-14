package queue

import model.Message

/**
 * 上行队列状态机（设计文档 §三 / 计划 Task 6）。
 *
 * 状态: PENDING -> SENDING -> (成功) 移除 / (失败) 重试回 PENDING 或终态 FAILED
 * 规则: 每条最多 maxAttempts 次发送尝试；SENDING 超时(30s)视为失败可重试；
 *       id 去重；持久化 = 每次变更后整表快照（量小）。
 *
 * 定时驱动放在 App 层（协程周期调用 due() + 投递 + markSent/markFailed），
 * core 保持纯同步可测。
 */
class MessageQueue(
    private val store: QueueStore,
    private val timeoutMs: Long = 30_000L,
    private val maxAttempts: Int = 2,
) {
    private val pending = LinkedHashMap<String, Entry>()

    private data class Entry(val msg: Message, var attempts: Int = 0)

    fun restore() {
        pending.clear()
        for (m in store.loadAll()) pending[m.id] = Entry(m)
    }

    /** 入队；id 已存在则忽略并返回 false。 */
    fun enqueue(msg: Message): Boolean {
        if (pending.containsKey(msg.id)) return false
        pending[msg.id] = Entry(msg)
        persist()
        return true
    }

    /**
     * 取出此刻应投递的消息并置为 SENDING（attempts+1）。
     * PENDING 一律到期；SENDING 需超时且仍有剩余尝试次数。
     */
    fun due(nowMs: Long): List<Message> {
        val due = mutableListOf<Message>()
        for (e in pending.values) {
            val isDue = when (e.msg.status) {
                model.MsgStatus.PENDING -> true
                model.MsgStatus.SENDING -> nowMs - e.msg.ts >= timeoutMs && e.attempts < maxAttempts
                else -> false
            }
            if (!isDue) continue
            e.attempts++
            val sending = e.msg.copy(status = model.MsgStatus.SENDING)
            pending[e.msg.id] = e.copy(msg = sending)
            due.add(sending)
        }
        if (due.isNotEmpty()) persist()
        return due
    }

    /** 投递成功：移出队列。 */
    fun markSent(id: String) {
        if (pending.remove(id) != null) persist()
    }

    /** 投递失败：剩余尝试次数用尽 → FAILED 终态（保留在 store 供展示/重发）；否则回 PENDING 等下一轮。 */
    fun markFailed(id: String) {
        val e = pending[id] ?: return
        if (e.attempts >= maxAttempts) {
            pending[id] = e.copy(msg = e.msg.copy(status = model.MsgStatus.FAILED))
        } else {
            pending[id] = e.copy(msg = e.msg.copy(status = model.MsgStatus.PENDING))
        }
        persist()
    }

    fun failed(): List<Message> = pending.values.map { it.msg }.filter { it.status == model.MsgStatus.FAILED }

    fun inFlightCount(): Int = pending.values.count { it.msg.status != model.MsgStatus.FAILED }

    fun all(): List<Message> = pending.values.map { it.msg }

    private fun persist() = store.saveAll(pending.values.map { it.msg })
}
