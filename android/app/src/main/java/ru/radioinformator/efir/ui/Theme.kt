package ru.radioinformator.efir.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Палитра ЭФИРа — та же, что на сайте: люминофорный зелёный по тёмному. */
val EfirGreen = Color(0xFF7CFFB2)
val EfirAmber = Color(0xFFFFCF6B)

/** Цвета кнопок верхнего меню — чтобы различать их с одного взгляда. */
val EfirSky = Color(0xFF7CD8FF)
val EfirLilac = Color(0xFFB9A7FF)
val EfirRose = Color(0xFFFF9BB0)
val EfirPeach = Color(0xFFFFB08A)
val EfirBackground = Color(0xFF070A08)
val EfirSurface = Color(0xFF0D120F)
val EfirSurfaceHigh = Color(0xFF131A16)
val EfirOutline = Color(0xFF1E2A23)
val EfirMuted = Color(0xFF6F8A7B)

private val EfirColors = darkColorScheme(
    primary = EfirGreen,
    onPrimary = Color(0xFF05170D),
    primaryContainer = Color(0xFF3E8C60),
    onPrimaryContainer = Color(0xFFDFFFEC),
    secondary = EfirAmber,
    background = EfirBackground,
    onBackground = Color(0xFFCFE3D6),
    surface = EfirSurface,
    onSurface = Color(0xFFCFE3D6),
    surfaceVariant = EfirSurfaceHigh,
    onSurfaceVariant = EfirMuted,
    outline = EfirOutline,
    error = Color(0xFFFF8A80),
)

/** Моноширинный шрифт везде — это приёмник, а не мессенджер. */
private val EfirTypography = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        bodySmall = base.bodySmall.copy(fontFamily = FontFamily.Monospace),
        labelSmall = base.labelSmall.copy(fontFamily = FontFamily.Monospace),
        labelMedium = base.labelMedium.copy(fontFamily = FontFamily.Monospace),
        titleMedium = base.titleMedium.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
        ),
    )
}

@Composable
fun EfirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EfirColors,
        typography = EfirTypography,
        content = content,
    )
}
