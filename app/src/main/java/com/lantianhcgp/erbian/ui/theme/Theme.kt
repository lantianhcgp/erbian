package com.lantianhcgp.erbian.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** FlClash 圆角阶（设计文档 §七）。 */
object Corners {
    const val XS = 4f
    const val SM = 8f
    const val MD = 16f
    const val LG = 20f
    const val XL = 24f
    const val XXL = 28f
    const val FULL = 1000f
}

/** 语义色（与主色和谐的 M3 感觉）。 */
object Semantic {
    val SuccessLight = Color(0xFF2E7D32)
    val SuccessDark = Color(0xFF81C784)
    val WarningLight = Color(0xFFB26A00)
    val WarningDark = Color(0xFFFFB74D)
}

private fun ColorScheme.pureBlackShift(): ColorScheme {
    // 纯黑 OLED：surface/背景置黑，容器色压暗（近似 FlClash HCT tone 下移）
    fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f))
    return copy(
        surface = Color.Black,
        background = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = surfaceContainerLow.darken(0.25f),
        surfaceContainer = surfaceContainer.darken(0.25f),
        surfaceContainerHigh = surfaceContainerHigh.darken(0.25f),
        surfaceContainerHighest = surfaceContainerHighest.darken(0.25f),
    )
}

@Composable
fun ErbianTheme(
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val base: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (dark && pureBlack) base.pureBlackShift() else base
    MaterialTheme(colorScheme = scheme, content = content)
}
