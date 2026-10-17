package com.lantianhcgp.erbian.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lantianhcgp.erbian.Locator
import com.lantianhcgp.erbian.ui.channel.ChannelPage
import com.lantianhcgp.erbian.ui.profile.ProfilePage
import com.lantianhcgp.erbian.ui.schedule.SchedulePage
import com.lantianhcgp.erbian.ui.settings.SettingsPage
import com.lantianhcgp.erbian.ui.theme.ErbianShapes
import com.lantianhcgp.erbian.ui.theme.ErbianTheme
import model.Message
import model.MsgKind

class MainActivity : ComponentActivity() {
// onNewIntent 复用场景（App 已在前台时长按电源）也要切到面板 —— v1.6 真机实锤的 bug
    private var assistModeState by androidx.compose.runtime.mutableStateOf(false)

    private fun Intent.isAssist() =
        action == Intent.ACTION_ASSIST || action == "android.intent.action.VOICE_COMMAND"

    override fun onCreate(savedInstanceState: Bundle?) {
        // assist 模式必须在 super 之前切透明主题，否则全屏不透明页（用户实测反馈）
        assistModeState = intent?.isAssist() == true
        if (assistModeState) setTheme(com.lantianhcgp.erbian.R.style.Theme_Erbian_Assist)
        super.onCreate(savedInstanceState)
        // 2026-10-16 edge-to-edge：系统栏区原本显示 windowBackground、Compose 不画到其后 →
        // 两截色。启用后 Compose 画满全屏（含系统栏），透明系统栏即与页面同色（assist 浮层除外）。
        // enableEdgeToEdge 在当前依赖下不可解析 → 用 WindowCompat（同效果：内容画到系统栏后）
        if (!assistModeState) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        setContent {
            val vm: AppViewModel = viewModel()
            ErbianTheme(
                dynamicColor = vm.config.dynamicColor,
                pureBlack = vm.config.pureBlack,
            ) {
                if (assistModeState) AssistPanelScreen(vm) { finish() }
                else HomeScreen(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val nowAssist = intent.isAssist()
        if (nowAssist != assistModeState) {
            assistModeState = nowAssist
            // 主题切换需要重建：assist=透明浮层，普通=完整App
            setTheme(if (nowAssist) com.lantianhcgp.erbian.R.style.Theme_Erbian_Assist else com.lantianhcgp.erbian.R.style.Theme_Erbian)
            recreate()
        }
    }
}

/**
 * ACTION_ASSIST 路由的浮层（activity 兜底路径，设计文档 §二 双路兜底）：
 * 半透明遮罩 + 底部面板；发送即 finish 收起。
 */
@androidx.compose.runtime.Composable
fun AssistPanelScreen(vm: AppViewModel, onDismiss: () -> Unit) {
    val messages by vm.channel.collectAsState()
    val preview = messages.takeLast(2).map { m ->
        (if (m.origin == "float" || m.origin == "channel") "你" else "我") to m.body
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable { onDismiss() }) {
        androidx.compose.foundation.layout.Spacer(Modifier.fillMaxSize())
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .clickable(enabled = false) {},
            shape = ErbianShapes.SheetTop,
            tonalElevation = 3.dp,
        ) {
            WakePanel(
                preview = preview,
                onSend = { text ->
                    vm.send(text, "float")
                    onDismiss()
                },
                onDismiss = onDismiss,
            )
        }
    }
}

private data class Tab(val label: String, val on: ImageVector, val off: ImageVector)

private val tabs = listOf(
    Tab("频道", Icons.Filled.Chat, Icons.Outlined.Chat),
    Tab("日程", Icons.Filled.Event, Icons.Outlined.Event),
    Tab("画像", Icons.Filled.Person, Icons.Outlined.Person),
    Tab("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@androidx.compose.runtime.Composable
fun HomeScreen(vm: AppViewModel) {
    var page by remember { mutableIntStateOf(0) }

    // 2026-10-17 v2：**不再接管 LocalTextToolbar**（接管导致耳边内菜单一直闪，用户反馈）。
    // 系统默认选择菜单（复制/全选）+ 由 ACTION_PROCESS_TEXT 注册的「记录到 Agent」全局项，
    // 一处注册、所有应用生效，且系统菜单本身稳定不闪。
    androidx.compose.material3.MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            // 底层铺满全屏（含状态栏/手势条区）：透明页面与状态栏不再透出白色窗口底
            // （2026-10-16 真机实测：状态栏 y0-100 露白 250，主体 y110 起才深色）
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                modifier = Modifier.fillMaxSize(),
            ) { p ->
                when (p) {
                    0 -> ChannelPage(vm)
                    1 -> SchedulePage(vm)
                    2 -> ProfilePage(vm)
                    else -> SettingsPage(vm)
                }
            }
            // 浮动 dock：统一贴底（用户定案 2026-10-15：所有页面导航都在下面，页头导航否决）
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                ErbianDock(page = page, onSelected = { page = it })
            }
        }
    }
}
