package ru.radioinformator.efir.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Палитра ЭФИРа. Люминофорный зелёный остаётся фирменным — по нему
 * приложение узнают на сайте, в значке и в магазине. Всё остальное
 * переехало на тёмное стекло: подложка холоднее и глубже, чтобы цветное
 * свечение поверх неё читалось как свет, а не как заливка.
 */
val EfirGreen = Color(0xFF6BFFB0)
val EfirAmber = Color(0xFFFFC96B)

/** Цвета кнопок верхнего ряда — чтобы различать их с одного взгляда. */
val EfirSky = Color(0xFF6FD8FF)
val EfirLilac = Color(0xFFB49BFF)
val EfirRose = Color(0xFFFF8FA8)
val EfirPeach = Color(0xFFFFA97F)

/** Основа. `EfirInk` — самый нижний слой, на нём живёт всё остальное. */
val EfirInk = Color(0xFF05070A)
val EfirBackground = EfirInk
val EfirSurface = Color(0xFF0B1015)
val EfirSurfaceHigh = Color(0xFF121A20)
val EfirOutline = Color(0xFF1E2A33)
val EfirMuted = Color(0xFF8399A8)
val EfirText = Color(0xFFE7F0F5)

private val EfirColors = darkColorScheme(
    primary = EfirGreen,
    onPrimary = EfirInk,
    primaryContainer = Color(0xFF14352A),
    onPrimaryContainer = Color(0xFFDFFFEC),
    secondary = EfirAmber,
    onSecondary = EfirInk,
    background = EfirBackground,
    onBackground = EfirText,
    surface = EfirSurface,
    onSurface = EfirText,
    surfaceVariant = EfirSurfaceHigh,
    onSurfaceVariant = EfirMuted,
    outline = EfirOutline,
    error = Color(0xFFFF7A8A),
    onError = EfirInk,
)

/**
 * Шрифт интерфейса — обычный системный гротеск с плотным набором: так
 * приложение выглядит как остальные на телефоне, а не как терминал.
 *
 * Моноширинный остался только там, где он несёт смысл: время, счётчик
 * байтов, позывные и коды. Их читают посимвольно и сравнивают глазами,
 * и цифры должны стоять в колонку.
 */
val EfirSans = FontFamily.SansSerif
val EfirMono = FontFamily.Monospace

/** Готовый стиль для технических данных. */
val EfirMonoSmall = TextStyle(
    fontFamily = EfirMono,
    fontSize = 11.sp,
    letterSpacing = 0.sp,
)

private val EfirTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(
            fontFamily = EfirSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = EfirSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = EfirSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp,
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = EfirSans,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = EfirSans,
            fontWeight = FontWeight.SemiBold,
        ),
        titleSmall = base.titleSmall.copy(
            fontFamily = EfirSans,
            fontWeight = FontWeight.SemiBold,
        ),
        bodyLarge = base.bodyLarge.copy(fontFamily = EfirSans, fontSize = 16.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = EfirSans, fontSize = 14.sp),
        bodySmall = base.bodySmall.copy(fontFamily = EfirSans, fontSize = 12.sp),
        labelLarge = base.labelLarge.copy(fontFamily = EfirSans, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = EfirSans),
        labelSmall = base.labelSmall.copy(fontFamily = EfirSans),
    )
}

/**
 * Скругления крупнее материаловских: диалоги и поля должны выглядеть
 * мягкими карточками, как в системных приложениях телефона.
 */
private val EfirShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun EfirTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EfirColors,
        typography = EfirTypography,
        shapes = EfirShapes,
        content = content,
    )
}
