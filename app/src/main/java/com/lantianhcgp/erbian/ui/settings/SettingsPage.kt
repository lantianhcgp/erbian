package com.lantianhcgp.erbian.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))

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
            Text("用于唤醒设置自检自愈；未授权不影响核心收发", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
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
            Text("耳边 v1.0 · 仓库 lantianhcgp/erbian", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("App 是耳朵，脑子在 Agent", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("erbian-token", text))
}
