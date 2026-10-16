package com.lantianhcgp.erbian.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantianhcgp.erbian.ui.AppViewModel
import com.lantianhcgp.erbian.ui.widgets.CardTitle
import com.lantianhcgp.erbian.ui.widgets.ErbianCard
import com.lantianhcgp.erbian.ui.widgets.PillButton
import com.lantianhcgp.erbian.ui.widgets.StatusDot

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsPage(vm: AppViewModel) {
    val context = LocalContext.current
    var showLog by remember { mutableStateOf(false) }

    if (showLog) { LogScreen(vm) { showLog = false }; return }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(bottom = com.lantianhcgp.erbian.ui.dockBottomInset()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
        )

        // 唤醒状态
        ErbianCard {
            CardTitle("唤醒状态")
            val s = vm.wakeSettings
            if (s == null) {
                Text("读不到设置（Shizuku 未授权？）", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatusDot(ok = vm.wakeHealthy, label = if (vm.wakeHealthy) "长按电源键已指向耳边" else "未指向耳边，需修复")
                }
                Text("长按电源键规则: ${s.singleKeyRule ?: "未设置"}", fontSize = 12.sp)
                Text("AOSP 行为: ${s.aospBehavior?.toString() ?: "未设置"}", fontSize = 12.sp)
                Text("assistant: ${s.assistant ?: "未设置"}", fontSize = 12.sp, maxLines = 2)
                Text("voice_interaction: ${s.voiceInteraction ?: "未设置"}", fontSize = 12.sp, maxLines = 2)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("修复", onClick = { vm.repairWake() }, enabled = !vm.wakeHealthy)
                    PillButton("刷新", onClick = { vm.refresh() }, filled = false)
                }
                vm.repairResult?.let {
                    Text("修复结果: ${it.entries.joinToString { e -> "${e.key}=${e.value}" }}", fontSize = 12.sp)
                }
            }
        }

        // 权限
        ErbianCard {
            CardTitle("权限")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Shizuku", fontSize = 13.sp)
                Text(
                    when {
                        !vm.shizukuAvailable -> "未运行（打开 Shizuku 启动）"
                        !vm.shizukuPermission -> "未授权（弹窗允许耳边）"
                        else -> "已授权"
                    },
                    fontSize = 13.sp,
                )
            }
            val ctx = LocalContext.current
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("通知", fontSize = 13.sp)
                Text(
                    if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) "已授权"
                    else "未授权（重要消息不弹通知）",
                    fontSize = 13.sp,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("日历读取", fontSize = 13.sp)
                Text(
                    if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED) "已授权"
                    else "未授权（日程页降级显示）",
                    fontSize = 13.sp,
                )
            }
            Text("Shizuku 用于唤醒设置自检自愈；未授权不影响核心收发", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }

        // 日志（用户可见，完整运行日志）
        ErbianCard {
            CardTitle("日志")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("运行日志 · 网络/队列/自检全记录", fontSize = 13.sp)
                PillButton("查看", onClick = { showLog = true }, filled = false)
            }
        }

        // MCP
        ErbianCard {
            CardTitle("MCP")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("启用（0.0.0.0:${vm.config.serverPort}）", fontSize = 13.sp)
                Switch(checked = vm.config.mcpEnabled, onCheckedChange = { vm.config.mcpEnabled = it })
            }
            Text(
                "Token: ${vm.config.serverToken.take(12)}…（长按复制）",
                fontSize = 12.sp,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { copyToClipboard(context, vm.config.serverToken) },
                ),
            )
        }

        // 通道
        ErbianCard {
            CardTitle("通道")
            var url by remember { mutableStateOf(vm.config.uplinkUrl) }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("webhook 上行地址") },
                singleLine = true,
            )
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("保存", onClick = { vm.config.uplinkUrl = url })
                PillButton("测试连通", onClick = { vm.config.uplinkUrl = url; vm.testUplink() }, filled = false)
            }
            vm.uplinkTest?.let { Text(it, fontSize = 12.sp) }
        }

        // 外观
        ErbianCard {
            CardTitle("外观")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("动态取色（Material You）", fontSize = 13.sp)
                Switch(checked = vm.config.dynamicColor, onCheckedChange = { vm.config.dynamicColor = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("纯黑模式（OLED）", fontSize = 13.sp)
                Switch(checked = vm.config.pureBlack, onCheckedChange = { vm.config.pureBlack = it })
            }
        }

        // 关于
        ErbianCard {
            CardTitle("关于")
            Text("耳边 v3.1 · 仓库 lantianhcgp/erbian", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("App 是耳朵，脑子在 Agent", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("erbian-token", text))
}

/**
 * 用户可见的运行日志页（设置 → 日志 → 查看）。
 * 展示 Logger 完整环形缓冲（最近 500 条，网络/队列/自检/上行全记录）+ 落盘文件清单。
 * 与 MCP log_tail 同一数据源，供无 MCP 时在端上直接排查。
 */
@Composable
private fun LogScreen(vm: AppViewModel, onBack: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    val lines = remember(tick) { vm.logger.tail(500) }
    val files = remember(tick) {
        vm.logger.logDir().listFiles()
            ?.sortedByDescending { it.name }
            ?.joinToString("\n") { "${it.name}  ${it.length() / 1024}KB" }
            ?: "（暂无日志文件）"
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("运行日志", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("刷新", onClick = { tick++ }, filled = false)
                PillButton("返回", onClick = onBack, filled = false)
            }
        }
        Text(
            "最近 ${lines.size} 条（缓冲上限 500）",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            files,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Spacer(Modifier.height(2.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(lines) { l ->
                Text(
                    l,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(com.lantianhcgp.erbian.ui.dockBottomInset()))
    }
}
