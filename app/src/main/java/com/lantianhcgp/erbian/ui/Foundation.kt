package com.lantianhcgp.erbian.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI 地基（docs/ui-redo-spec.md §1/§2）：
 * - 柔影：抄 FlClash _dockShadows（iOS 式双层，暗色×3 强度）
 * - dock：边距21 / 底=max(21, 手势条) / 高62 —— 根治"导航栏错误"
 * - hairline：outlineVariant×0.5 统一描边语言
 */

// ---- 描边 ----

val HairlineBorder @Composable get() = androidx.compose.foundation.BorderStroke(
    1.dp,
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
)

@Composable
fun hairlineModifier(shape: Shape): Modifier =
    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape)

// ---- Dock 指标（页面必须用同一组常量做 inset） ----

object Dock {
    const val HeightDp = 62          // FlClash _barHeight
    const val EdgeDp = 21            // FlClash _edgeMargin
    const val BottomMinDp = 21       // 与手势条取大
    const val ShadowRoomDp = 8
}

/** 页面底部应避开 dock 的总高度（含边距与手势条），页面级统一取用。 */
@Composable
fun dockBottomInset(): androidx.compose.ui.unit.Dp {
    val navPad = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .getBottom(androidx.compose.ui.platform.LocalDensity.current)
        .let { with(androidx.compose.ui.platform.LocalDensity.current) { it.toDp() } }
    val bottom = maxOf(Dock.BottomMinDp.dp, navPad)
    return Dock.HeightDp.dp + bottom + 10.dp
}

// ---- 按压缩放 ----

fun Modifier.pressableScale(onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

// ---- 浮动 Dock ----

private data class DockTab(val label: String, val on: ImageVector, val off: ImageVector)

private val dockTabs = listOf(
    DockTab("频道", Icons.Filled.Chat, Icons.Outlined.Chat),
    DockTab("日程", Icons.Filled.Event, Icons.Outlined.Event),
    DockTab("画像", Icons.Filled.Person, Icons.Outlined.Person),
    DockTab("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)

/**
 * 浮动药丸导航（spec §2）：边距21、底距 max(21,手势条)、双柔影、按压缩放、
 * 选中=透镜 pill（颜色渐变+图标缩放），contentDescription=页索引（测试契约）。
 */
@Composable
fun ErbianDock(page: Int, onSelected: (Int) -> Unit) {
    val navPad = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .getBottom(androidx.compose.ui.platform.LocalDensity.current)
        .let { with(androidx.compose.ui.platform.LocalDensity.current) { it.toDp() } }
    val bottom = maxOf(Dock.BottomMinDp.dp, navPad)
    val shape = RoundedCornerShape(50)
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = Dock.EdgeDp.dp, end = Dock.EdgeDp.dp, bottom = bottom)
            .heightIn(min = Dock.HeightDp.dp)  // 抗裁：内容撑开而非切（大字体/高密度安全）
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .clip(shape)
            .background(container)
            .border(HairlineBorder, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // 禁用 fillMaxHeight：在 heightIn(min) 盒子里它会把药丸撑到全屏高（2026-10-16 真机截图实锤）
                .padding(horizontal = Dock.EdgeDp.dp / 2),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dockTabs.forEachIndexed { i, tab ->
                DockItem(
                    tab = tab,
                    selected = page == i,
                    onClick = { onSelected(i) },
                    desc = i.toString(),
                )
            }
        }
    }
}

@Composable
private fun DockItem(tab: DockTab, selected: Boolean, onClick: () -> Unit, desc: String) {
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "lens",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "iconScale",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tint",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.StiffnessMediumLow), label = "press")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .semantics { contentDescription = desc }
            .graphicsLayer { scaleX = press; scaleY = press }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 52.dp, height = 30.dp),
        ) {
            // 背景 pill 单独一层：alpha/缩放只作用于背景。
            // 旧版把 graphicsLayer{alpha=pillAlpha} 挂在含 Icon 的 Box 上，
            // 未选中 pillAlpha=0 → 整层连图标一起透明，只有当前页图标可见
            // （真机+CI 帧实测：其余三槽图标区 0 像素，2026-10-16）。
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = pillAlpha
                        scaleX = 0.7f + 0.3f * pillAlpha
                        scaleY = 0.7f + 0.3f * pillAlpha
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50),
                    ),
            )
            Icon(
                imageVector = if (selected) tab.on else tab.off,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = tab.label,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = tint,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
