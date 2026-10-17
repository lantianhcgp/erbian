package com.lantianhcgp.erbian.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.lantianhcgp.erbian.Locator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 系统级「记录到 Agent」（2026-10-17 用户：要系统级全局效果，不是只在耳边里能用）。
 *
 * Android 标准机制 = ACTION_PROCESS_TEXT：任何 App 里长按选中文字 → 系统选择菜单
 * 会自动列出所有声明了该 intent 的应用（本页即菜单里那项「记录到 Agent」）。
 * 点击 → EXTRA_PROCESS_TEXT 走既有上行队列（HMAC/重试）交给 agent → 立即结束无感。
 */
class ProcessTextActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_PROCESS_TEXT)?.trim().orEmpty()
        if (text.isNotEmpty()) {
            scope.launch {
                runCatching {
                    Locator.sendUserRecord("记录到 Agent（用户在其他应用选中的文字）：$text", "global")
                }.onSuccess {
                    Locator.logger.i("global", "process_text recorded ${text.length} chars")
                }.onFailure {
                    Locator.logger.i("global", "process_text FAIL: $it")
                }
            }
            Toast.makeText(this, "已记录，Agent 处理中", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
