package logic

/**
 * 摘要节流（计划 Task 8）：App 进入前台时请求摘要，30s 窗口内不重复请求。
 * 时钟注入，纯逻辑可测。
 */
class SummaryThrottle(
    private val windowMs: Long = 30_000L,
    private var lastRequestAt: Long = Long.MIN_VALUE,
) {
    /** 返回 true = 本次应发请求；false = 窗口内，跳过。 */
    fun shouldRequest(nowMs: Long): Boolean {
        if (lastRequestAt != Long.MIN_VALUE && nowMs - lastRequestAt < windowMs) return false
        lastRequestAt = nowMs
        return true
    }

    fun lastRequest(): Long = lastRequestAt
}
