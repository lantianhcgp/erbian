package logic

/**
 * 混合待办策略（设计文档 §一 / 计划 Task 9）。
 * 输入一句话 → AUTO（直写日历+报告）/ CONFIRM（先问）/ NONE（无待办）。
 *
 * 规则（v1 关键词启发式；判错的个案由用户"这条不对"回流修正，规则迭代在 Agent 侧）：
 * 1. 先找"动作锚点"：忘/要/得/需要/记得/别忘/安排/订/买/交/复习/准备/写/背/刷/打/去/参加/考…
 * 2. 明确时间词（明天/周X/下周/X月X日/后天/今晚/具体钟点）+ 动作锚点 → AUTO
 * 3. 模糊拖延词（找时间/回头/有空/哪天/改天/以后/抽空/最近/想办法）→ CONFIRM
 * 4. 有动作锚点但无时间 → CONFIRM
 * 5. 无动作锚点 → NONE
 */
enum class TodoDecision { AUTO, CONFIRM, NONE }

object TodoPolicy {

    private val ACTION_WORDS = listOf(
        "忘", "要", "得", "需要", "记得", "安排", "订", "买", "交", "缴", "还",
        "复习", "准备", "写", "背", "刷", "打卡", "参加", "考", "报名", "预约",
        "取", "寄", "送", "修", "换", "洗", "理", "挂", "复诊", "开会", "体检",
        "去", "走", "带", "拿", "填", "改", "听", "看", "练",
    )

    private val EXPLICIT_TIME = listOf(
        "明天", "明晚", "明早", "后天", "大后天", "今晚", "今天", "早上", "中午", "下午", "晚上",
        "周日", "周一", "周二", "周三", "周四", "周五", "周六", "周天", "礼拜", "星期",
        "下个月", "下周", "这周", "月底", "年初", "号", "日早上", "点", "分",
    )

    private val VAGUE_WORDS = listOf(
        "找时间", "回头", "有空", "哪天", "改天", "以后", "抽空", "抽个", "最近", "想办法",
        "抽点", "择日", "下次", "有时间", "看看再说", "再说",
    )

    fun decide(text: String): TodoDecision {
        val hasAction = ACTION_WORDS.any { text.contains(it) }
        if (!hasAction) return TodoDecision.NONE
        val hasVague = VAGUE_WORDS.any { text.contains(it) }
        if (hasVague) return TodoDecision.CONFIRM
        val hasTime = EXPLICIT_TIME.any { text.contains(it) }
        return if (hasTime) TodoDecision.AUTO else TodoDecision.CONFIRM
    }
}
