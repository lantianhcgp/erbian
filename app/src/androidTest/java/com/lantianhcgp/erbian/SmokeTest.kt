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
 * 覆盖：频道页发送出泡、四页可导航、设置页唤醒状态卡存在。
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun channelSendShowsBubble() {
        compose.waitForIdle()
        compose.onNodeWithText("说点什么").assertExists()
        compose.onNodeWithText("说点什么").performTextInput("明天月考别忘了带身份证")
        compose.onNodeWithContentDescription("发送").performClick()
        // 发送链路是 异步入库 + Flow 回流，轮询等待代替单次断言
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("明天月考别忘了带身份证").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("明天月考别忘了带身份证").assertIsDisplayed()
    }

    @Test
    fun navigateAllPages() {
        compose.waitForIdle()
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
