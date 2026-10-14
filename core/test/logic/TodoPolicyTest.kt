package logic

import org.junit.Assert.assertEquals
import org.junit.Test

class TodoPolicyTest {

    private fun d(text: String) = TodoPolicy.decide(text)

    @Test
    fun explicitTimeWithActionIsAuto() {
        assertEquals(TodoDecision.AUTO, d("明天月考别忘了带身份证"))
        assertEquals(TodoDecision.AUTO, d("周五下午三点数学考试"))
        assertEquals(TodoDecision.AUTO, d("后天把英语作文交了"))
        assertEquals(TodoDecision.AUTO, d("今晚八点记得打卡"))
        assertEquals(TodoDecision.AUTO, d("下周日之前把报名表交上去"))
    }

    @Test
    fun vagueWordsForceConfirm() {
        assertEquals(TodoDecision.CONFIRM, d("回头找时间把那本书还了"))
        assertEquals(TodoDecision.CONFIRM, d("有空得理个发"))
        assertEquals(TodoDecision.CONFIRM, d("改天买个新耳机"))
        assertEquals(TodoDecision.CONFIRM, d("明天找时间把自行车修了")) // 有时间但同时有拖延词 → 仍确认
    }

    @Test
    fun actionWithoutTimeIsConfirm() {
        assertEquals(TodoDecision.CONFIRM, d("得把资料打印一下"))
        assertEquals(TodoDecision.CONFIRM, d("要买支新笔"))
    }

    @Test
    fun noActionIsNone() {
        assertEquals(TodoDecision.NONE, d("今天好累啊"))
        assertEquals(TodoDecision.NONE, d("数学老师今天讲课很有趣"))
        assertEquals(TodoDecision.NONE, d(""))
    }
}
