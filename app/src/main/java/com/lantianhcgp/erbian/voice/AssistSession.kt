package com.lantianhcgp.erbian.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import com.lantianhcgp.erbian.ui.MainActivity
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantianhcgp.erbian.Locator
import kotlinx.coroutines.launch

/**
 * 长按电源唤起的会话浮层（VoiceInteractionSession，系统级窗口，锁屏可弹）。
 * 形态：底部半高面板。输入（语音交给输入法）→ 发送 → 自动收起。
 * 不做录音、不做转写（设计文档 §四）。
 */
class AssistSession(private val ctx: android.content.Context) : VoiceInteractionSession(ctx) {

    // 小爱同款体验（用户预期 2026-10-15）：面板直接浮在当前应用上方。
    // 战史：会话窗口（onCreateContentView）系统只画入场动画不画内容——特权留给系统助理；
    // activity 跳转被否。最终方案：会话回调当扳机，面板走自家悬浮窗（TYPE_APPLICATION_OVERLAY）。
    override fun onCreateContentView(): android.view.View? {
        // 无悬浮窗权限时的兜底（模拟器等场景可用）
        com.lantianhcgp.erbian.Locator.logger.i("session", "onCreateContentView (fallback panel)")
        return SessionPanelView(ctx, onDismiss = { hide() })
    }

    // SDK35 真实签名: onShow(Bundle args, int showFlags)；收起用 hide()
    override fun onShow(args: android.os.Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        com.lantianhcgp.erbian.Locator.logger.i("session", "onShow flags=$showFlags")
        if (!android.provider.Settings.canDrawOverlays(ctx)) {
            com.lantianhcgp.erbian.Locator.logger.e("session", "no SYSTEM_ALERT_WINDOW - overlay skipped (fallback panel)")
            return
        }
        showOverlay()
    }

    private fun showOverlay() {
        val wm = ctx.getSystemService(android.content.WindowManager::class.java)
        val params = android.view.WindowManager.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            0,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            softInputMode = android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        val holder = arrayOfNulls<SessionPanelView>(1)
        val panel = SessionPanelView(ctx, onDismiss = {
            runCatching { holder[0]?.let { v -> wm.removeView(v) } }
            hide()
        })
        holder[0] = panel
        runCatching {
            wm.addView(panel, params)
            com.lantianhcgp.erbian.Locator.logger.i("session", "overlay panel added (over current app)")
        }.onFailure {
            com.lantianhcgp.erbian.Locator.logger.e("session", "overlay add failed: $it")
            finish()
        }
    }
}

class SessionPanelView(
    context: android.content.Context,
    private val onDismiss: () -> Unit,
) : AbstractComposeView(context) {
    @Composable
    override fun Content() {
        SessionPanel(onDismiss = onDismiss)
    }
}

@Composable
fun SessionPanel(onDismiss: () -> Unit) {
    var text by mutableStateOf("")
    var sent by mutableStateOf(false)
    val scope = rememberCoroutineScope()
    val preview = Locator.repository.channel.value.takeLast(2)

    MaterialTheme {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = 3.dp,
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("耳边", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (preview.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (m in preview) {
                                Text(
                                    "${if (m.origin == "float") "我" else "耳边"}: ${m.body}",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("说点什么…（语音可用输入法）") },
                        minLines = 2,
                        maxLines = 4,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (sent) {
                            Text("已送达 ✓", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                        }
                        TextButton(onClick = onDismiss) { Text("关闭") }
                        Button(
                            onClick = {
                                val t = text.trim()
                                if (t.isEmpty() || sent) return@Button
                                sent = true
                                scope.launch {
                                    runCatching { Locator.sendUserRecord(t, "float") }
                                    onDismiss()
                                }
                            },
                            enabled = text.isNotBlank() && !sent,
                        ) { Text("发送") }
                    }
                }
            }
        }
    }
}
