package com.lantianhcgp.erbian.ui.text

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
import androidx.compose.ui.text.TextSelectionColors
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row

/**
 * 自定义文字选择菜单（2026-10-17 用户反馈：要的不是整条气泡长按弹菜单，
 * 而是像系统/rikkahub 那样「长按→文字进入选中态（青色高亮+手柄）→菜单」，
 * 且菜单里要有「记录到 Agent」）。
 *
 * 实现：接管 [LocalTextToolbar] —— Compose 选中文字后的浮动菜单由我们渲染，
 * 菜单弹在**手指长按位置**（[lastTouch]），项：复制 / 全选 / 记录到 Agent。
 * 「记录到 Agent」= 触发系统复制（onCopyRequested）→ 读剪贴板 → 交给 [onRecord] 上行。
 */
class ErbianTextToolbar(
    private val readClipboard: () -> String?,
    private val onRecord: (String) -> Unit,
) : TextToolbar {

    /** 手指最后按下的位置（Compose 坐标系），菜单位于其上方。 */
    var lastTouch: Offset = Offset.Zero

    var visible by mutableStateOf(false)
        private set
    var anchor by mutableStateOf(Offset.Zero)
        private set

    private var copy: (() -> Unit)? = null
    private var selectAll: (() -> Unit)? = null

    override val menuBounds: DpRect
        get() = DpRect(anchor, androidx.compose.ui.geometry.Size.Zero)

    override fun showMenu(
        textSelectionColors: TextSelectionColors,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        copy = onCopyRequested
        selectAll = onSelectAllRequested
        anchor = lastTouch
        visible = true
    }

    override fun hide() {
        visible = false
    }

    fun dismiss() {
        visible = false
    }

    /** 仅复制（菜单「复制」）。 */
    fun copyOnly() {
        copy?.invoke()
        visible = false
    }

    /** 仅全选（菜单「全选」）。 */
    fun selectAllOnly() {
        selectAll?.invoke()
        visible = false
    }

    /** 记录到 Agent：先复制选中文本，再读剪贴板上行。 */
    fun recordSelected() {
        copy?.invoke()
        val text = readClipboard()?.trim().orEmpty()
        if (text.isNotEmpty()) onRecord(text)
        visible = false
    }
}

/**
 * 选择菜单浮层（由 ErbianTextToolbar.visible 驱动；样式贴近系统浮动菜单）。
 */
@Composable
fun ErbianSelectionMenu(toolbar: ErbianTextToolbar) {
    if (!toolbar.visible) return
    val scheme = MaterialTheme.colorScheme
    val touch = toolbar.anchor
    val density = androidx.compose.ui.platform.LocalDensity.current
    val yOff = with(density) { (touch.y - 52.dp.toPx()).toInt() }
    Popup(
        offset = IntOffset(touch.x.toInt(), yOff),
        onDismissRequest = { toolbar.dismiss() },
    ) {
        Surface(
            shape = MaterialTheme.shapes.Medium,
            color = scheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MenuItem("复制") {
                    toolbar.dismiss().also { }
                    // 复制后菜单收起：copy 回调已在 dismiss 前捕获
                    toolbar.copyOnly()
                }
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
