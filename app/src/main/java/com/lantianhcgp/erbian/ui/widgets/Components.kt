package com.lantianhcgp.erbian.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lantianhcgp.erbian.ui.theme.ErbianShapes
import com.lantianhcgp.erbian.ui.theme.Semantic

/** 药丸按钮（FlClash：全部按钮=full 圆角）。 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
) {
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = ErbianShapes.Pill,
            modifier = modifier,
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) { Text(text, fontSize = 14.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = ErbianShapes.Pill,
            modifier = modifier,
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) { Text(text, fontSize = 14.sp) }
    }
}

/** 超椭圆卡片。 */
@Composable
fun ErbianCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(shape = ErbianShapes.Card, tonalElevation = 1.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** 状态点：绿=正常 红=异常。 */
@Composable
fun StatusDot(ok: Boolean, label: String, modifier: Modifier = Modifier) {
    val dotColor = when {
        ok && androidx.compose.foundation.isSystemInDarkTheme() -> Semantic.SuccessDark
        ok -> Semantic.SuccessLight
        else -> MaterialTheme.colorScheme.error
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .background(dotColor, ErbianShapes.Pill)
        )
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 空态：图标 + 主文案 + 副文案。 */
@Composable
fun EmptyState(title: String, subtitle: String = "", modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("💬", fontSize = 34.sp)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (subtitle.isNotEmpty()) {
            Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}
