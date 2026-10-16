package com.lantianhcgp.erbian.ui.channel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantianhcgp.erbian.ui.AppViewModel
import com.lantianhcgp.erbian.ui.dockBottomInset
import com.lantianhcgp.erbian.ui.hairlineModifier
import com.lantianhcgp.erbian.ui.pressableScale
import com.lantianhcgp.erbian.ui.widgets.CardTitle
import com.lantianhcgp.erbian.ui.widgets.EmptyState
import com.lantianhcgp.erbian.ui.widgets.ErbianCard
import com.lantianhcgp.erbian.ui.widgets.PillButton
import com.lantianhcgp.erbian.ui.widgets.StatusDot
import model.Message
import model.MsgKind
import model.MsgStatus

/**
 * 频道页 v2（docs/ui-redo-spec.md §3，RikkaHub 结构）：
 * - 输入栏走 bottomBar：键盘关=垫过 dock（dockBottomInset），键盘开=imePadding 顶起
 * - 空态与列表走同一 padding 分支（根治"输入框被挤没/垫底错位"）
 * - 气泡方向尾、hairline 描边语言、发送按钮状态机
 */
@Composable
fun ChannelPage(vm: AppViewModel) {
    val messages by vm.channel.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, vm.agentProcessing) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        else if (vm.agentProcessing) listState.animateScrollToItem(0)
    }

    // 键盘开合决定底垫：关=盖过 dock（含手势条）；开=交给 imePadding（dock 此时被键盘盖住无需避让）
    val imeOpen = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    val bottomPad = if (imeOpen) 8.dp else dockBottomInset() + 8.dp  // 输入栏浮在 dock 之上留缝

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { ChannelHeader(vm) },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = bottomPad),
            ) {
                InputContainer(
                    value = input,
                    onValueChange = { input = it },
                    onSend = {
                        val t = input.trim()
                        if (t.isNotEmpty()) { vm.send(t); input = "" }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (messages.isEmpty()) {
                EmptyState("这里还很安静", "长按电源键，或者在下面说点什么")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    items(messages, key = { it.id }) { m ->
                        when (m.kind) {
                            MsgKind.SUMMARY -> SummaryBar(m)
                            MsgKind.TODO_CONFIRM -> TodoConfirmCard(m, vm)
                            else -> Bubble(
                                m,
                                onResend = { vm.resend(m) },
                                onNotRight = { vm.send("这条不对：" + m.body) },
                                // F2（2026-10-16）：把这条记录交给 agent 分析/记录
                                onAnalyze = { vm.send("请分析这条记录并给出处理：" + m.body) },
                            )
                        }
                    }
                    // F3（2026-10-16 用户）：agent 处理中的进行时指示
                    if (vm.agentProcessing) {
                        item(key = "agent-typing") { TypingRow() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(vm: AppViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "频道",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // 摘要按钮（设计§六）
            androidx.compose.material3.IconButton(
                onClick = { vm.send("给我一份摘要：上次摘要之后的所有回复") },
                modifier = Modifier
                    .size(30.dp)
                    .semantics { contentDescription = "摘要" },
            ) {
                Icon(Icons.Outlined.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            StatusDot(ok = vm.shizukuPermission, label = "Shizuku")
            StatusDot(ok = vm.queueInFlight == 0, label = "通道")
        }
    }
}

/** 输入容器：圆角20外框+hairline+透明字段+30dp圆形发送（skill 状态机） */
@Composable
private fun InputContainer(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    val shape = RoundedCornerShape(20)
    val sending = false
    val empty = value.isBlank()
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp, max = 140.dp),
                placeholder = { Text("说点什么", fontSize = 15.sp) },
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(16),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "耳边 · 直达本机 Agent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f),
                )
                SendCircle(enabled = !empty && !sending, sending = sending, onClick = onSend)
            }
        }
    }
}

@Composable
private fun SendCircle(enabled: Boolean, sending: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        sending -> scheme.primary
        !enabled -> scheme.surfaceContainerHigh
        else -> scheme.primary
    }
    val content = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        else -> scheme.onPrimary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(container)
            .pressableScale(onClick = { if (enabled) onClick() }),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send,
            contentDescription = "发送",
            tint = content,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** 气泡：方向尾 + 发送状态行 */
@Composable
@androidx.compose.foundation.ExperimentalFoundationApi
private fun Bubble(m: Message, onResend: () -> Unit, onNotRight: () -> Unit, onAnalyze: () -> Unit = {}) {
    val isUser = m.origin in setOf("float", "channel")
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        androidx.compose.foundation.layout.Box {
        Surface(
            shape = if (isUser) RoundedCornerShape(18, 18, 4, 18)
            else RoundedCornerShape(18, 18, 18, 4),
            color = if (isUser) scheme.primaryContainer else scheme.surfaceContainerHigh,
            border = if (isUser) null else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.4f)),
            tonalElevation = 0.dp,
            // 2026-10-17 用户：不要整条长按弹菜单 → 文字改真选中（rikkahub 式）；
            // 「这条不对」改为点击 agent 气泡触发（用户气泡点击无效，重发走状态行）
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clickable(enabled = !isUser, onClick = onNotRight),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(m.body, style = MaterialTheme.typography.bodyLarge)
                }
                if (isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            when (m.status) {
                                MsgStatus.PENDING, MsgStatus.SENDING -> "发送中…"
                                MsgStatus.SENT -> "已送达"
                                MsgStatus.FAILED -> "失败，点击重发"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (m.status == MsgStatus.FAILED) scheme.error
                            else scheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = if (m.status == MsgStatus.FAILED) Modifier
                                .clickable { onResend() } else Modifier,
                        )
                    }
                }
            }
        }
        }
    }
}

/** F3：agent 处理中的进行时指示（三点脉冲 + 文字）。 */
@androidx.compose.runtime.Composable
private fun TypingRow() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "typing")
    val pulse by t.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable<Float>(
            animation = androidx.compose.animation.core.tween(durationMillis = 650),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
        Surface(
            shape = RoundedCornerShape(18, 18, 18, 4),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.semantics { contentDescription = "agent处理中" },
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .graphicsLayer { alpha = pulse }
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                    )
                }
            }
        }
        Text(
            "  正在处理…",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 摘要条：弱化（描边+斜体） */
@Composable
private fun SummaryBar(m: Message) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(14),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("摘要", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                m.body,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
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
            PillButton("改写", onClick = { vm.send("帮我改写这条待办：" + m.body) }, filled = false)
        }
    }
}


