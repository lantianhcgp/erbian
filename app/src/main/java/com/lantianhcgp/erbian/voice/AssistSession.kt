package com.lantianhcgp.erbian.voice

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
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

    override fun onShow(args: android.os.Bundle?, activityOptions: android.app.ActivityOptions?) {
        super.onShow(args, activityOptions)
        val parent = window?.decorView as? ViewGroup ?: return
        parent.removeAllViews()
        parent.addView(SessionPanelView(ctx, onDismiss = { hideSession() }))
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
