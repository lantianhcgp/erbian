package com.lantianhcgp.erbian

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lantianhcgp.erbian.ui.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 模拟器冒烟验收（计划 Task 27）。
 * 单方法串行：发送出泡 → 四页导航。
 * （曾拆两方法，第二次启动 activity 时序问题导致 No compose hierarchies，故合并。）
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun fullSmoke() {
        compose.waitForIdle()

        // 1) 频道页发送出泡
        compose.onNodeWithText("说点什么").assertExists()
        compose.onNodeWithText("说点什么").performTextInput("明天月考别忘了带身份证")
        compose.onNodeWithContentDescription("发送").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("明天月考别忘了带身份证").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("明天月考别忘了带身份证").assertIsDisplayed()

        // 2) 四页导航（各页标记文本）
        for ((page, marker) in listOf(
            1 to "耳边待办",
            2 to "画像",
            3 to "唤醒状态",
            0 to "频道",
        )) {
            compose.onNodeWithContentDescription(page.toString()).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(marker, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(marker, substring = true).assertExists("page $page marker '$marker' missing")
        }
    }
}
