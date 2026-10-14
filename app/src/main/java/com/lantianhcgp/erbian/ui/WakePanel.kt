package com.lantianhcgp.erbian.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantianhcgp.erbian.ui.theme.ErbianShapes

/**
 * 唤醒浮层（共享 UI）：AssistSession（会话路由）与 MainActivity ACTION_ASSIST（activity 路由）
 * 共用同一块面板——不管系统从哪条派发路径进来，用户看到的都是「耳边」浮层。
 */
@Composable
fun WakePanel(
    preview: List<Pair<String, String>>,   // (来源, 内容) 最近两条
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = ErbianShapes.SheetTop,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("耳边", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text("点此收起", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable { onDismiss() })
            }
            if (preview.isEmpty()) {
                Text("我在。说点什么，或者长按输入", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp))
            } else {
                Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    preview.forEach { (from, body) ->
                        Text("$from: $body", fontSize = 12.sp, fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            var input by remember { mutableStateOf("") }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp, max = 120.dp),
                    placeholder = { Text("说点什么") },
                    maxLines = 4,
                )
                IconButton(
                    onClick = {
                        val t = input.trim()
                        if (t.isNotEmpty()) { onSend(t); input = "" }
                    },
                    enabled = input.isNotBlank(),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送") }
            }
        }
    }
}
