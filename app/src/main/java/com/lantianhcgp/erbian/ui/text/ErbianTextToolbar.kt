package com.lantianhcgp.erbian.ui.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup

/**
 * 自定义文字选择菜单（2026-10-17 用户反馈：不要整条气泡长按弹菜单，要像系统/rikkahub 那样
 * 「长按→文字进入选中态（高亮+手柄）→菜单」，且菜单里要有「记录到 Agent」）。
 *
 * 接管 [LocalTextToolbar]：Compose 选中文字后的浮动菜单由我们渲染。
 * 菜单弹在**选区上方**（showMenu 的 rect 全局坐标），项：复制 / 全选 / 记录到 Agent。
 * 「记录到 Agent」= 触发系统复制 → 读剪贴板 → 交给 [onRecord] 上行给 agent。
 */
class ErbianTextToolbar(
    private val readClipboard: () -> String?,
    private val onRecord: (String) -> Unit,
) : TextToolbar {

    var visible by mutableStateOf(false)
        private set

    /** 选区上方锚点（全局坐标 px）。 */
    var anchor by mutableStateOf(Offset.Zero)
        private set

    private var copy: (() -> Unit)? = null
    private var selectAll: (() -> Unit)? = null

    override val status: TextToolbarStatus
        get() = if (visible) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        copy = onCopyRequested
        selectAll = onSelectAllRequested
        anchor = Offset(rect.center.x, rect.top)
        visible = true
    }

    override fun hide() {
        visible = false
    }

    fun dismiss() {
        visible = false
    }

    fun copyOnly() {
        copy?.invoke()
        visible = false
    }

    fun selectAllOnly() {
        selectAll?.invoke()
        visible = false
    }

    fun recordSelected() {
        copy?.invoke()
        val text = readClipboard()?.trim().orEmpty()
        if (text.isNotEmpty()) onRecord(text)
        visible = false
    }
}

/** 选择菜单浮层（样式贴近系统浮动菜单：深色胶囊、横排项）。 */
@Composable
fun ErbianSelectionMenu(toolbar: ErbianTextToolbar) {
    if (!toolbar.visible) return
    val touch = toolbar.anchor
    val density = androidx.compose.ui.platform.LocalDensity.current
    val yOff = with(density) { (touch.y - 56.dp.toPx()).toInt() }
    Popup(
        offset = IntOffset(touch.x.toInt(), yOff),
        onDismissRequest = { toolbar.dismiss() },
    ) {
        Surface(
            shape = MaterialTheme.shapes.Medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MenuItem("复制") { toolbar.copyOnly() }
                MenuItem("全选") { toolbar.selectAllOnly() }
                MenuItem("记录到 Agent") { toolbar.recordSelected() }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
