package com.lantianhcgp.erbian.ui.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantianhcgp.erbian.ui.AppViewModel
import com.lantianhcgp.erbian.ui.widgets.CardTitle
import com.lantianhcgp.erbian.ui.widgets.EmptyState
import com.lantianhcgp.erbian.ui.widgets.ErbianCard
import com.lantianhcgp.erbian.ui.widgets.PillButton
import com.lantianhcgp.erbian.ui.widgets.StatusDot
import model.Message
import model.MsgKind
import model.MsgStatus

@Composable
fun ChannelPage(vm: AppViewModel) {
    val messages by vm.channel.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("频道", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusDot(ok = vm.shizukuPermission, label = "Shizuku")
                StatusDot(ok = vm.queueInFlight == 0, label = "通道")
            }
        }

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState("这里还很安静", "长按电源键，或者在下面说点什么")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(messages, key = { it.id }) { m ->
                    when (m.kind) {
                        MsgKind.SUMMARY -> SummaryBar(m)
                        MsgKind.TODO_CONFIRM -> TodoConfirmCard(m, vm)
                        else -> Bubble(m, onResend = { vm.resend(m) })
                    }
                }
            }
        }

        Surface(tonalElevation = 2.dp, shape = ErbianShapes(), modifier = Modifier.padding(bottom = 84.dp).padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        if (t.isNotEmpty()) { vm.send(t); input = "" }
                    },
                    enabled = input.isNotBlank(),
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送") }
            }
        }
    }
}

// 圆角占位（顶部28/四角16 的频道输入条）
private fun ErbianShapes(): RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 16.dp, bottomEnd = 16.dp)

@Composable
private fun Bubble(m: Message, onResend: () -> Unit) {
    val isUser = m.origin in setOf("float", "channel")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
            else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(m.body, fontSize = 15.sp)
                if (isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            when (m.status) {
                                MsgStatus.PENDING, MsgStatus.SENDING -> "发送中…"
                                MsgStatus.SENT -> "已送达"
                                MsgStatus.FAILED -> "失败，点击重发"
                            },
                            fontSize = 11.sp,
                            color = if (m.status == MsgStatus.FAILED) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (m.status == MsgStatus.FAILED) Modifier
                                .clickable { onResend() } else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBar(m: Message) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("摘要", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            Text(m.body, fontSize = 13.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TodoConfirmCard(m: Message, vm: AppViewModel) {
    ErbianCard {
        CardTitle("待办确认")
        Text(m.body, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("确认", onClick = { vm.send("确认：${m.body}") })
            PillButton("不用", onClick = { vm.send("不用了：${m.body}") }, filled = false)
        }
    }
}
