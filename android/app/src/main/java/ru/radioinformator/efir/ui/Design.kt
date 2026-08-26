package ru.radioinformator.efir.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/* ==================================================================
 * Язык интерфейса ЭФИРа: тёмное стекло, свечение вокруг работающего,
 * пружинный отклик на касание.
 *
 * Смысл один: приёмник должен показывать состояние без чтения. Идёт
 * приём — вокруг тумблера дышит ореол; молчим — свет гаснет. Раньше
 * это были слова «ПРИЁМ ВКЛ», и слова приходилось вычитывать.
 * ================================================================== */

/** Скругления. Крупные — как у карточек в системных приложениях телефона. */
val EfirCardShape = RoundedCornerShape(22.dp)
val EfirTileShape = RoundedCornerShape(18.dp)
val EfirChipShape = RoundedCornerShape(50)

/* ------------------------------------------------------------------ свечение */

/**
 * Ореол вокруг элемента. Рисуется за пределами рамки — Compose не
 * подрезает `drawBehind`, пока не попросили `clip`, поэтому свет
 * выходит наружу, а не упирается в край кнопки.
 *
 * @param spread во сколько раз ореол шире самого элемента.
 */
fun Modifier.halo(
    color: Color,
    alpha: Float = 0.45f,
    spread: Float = 1.9f,
): Modifier = this.drawBehind {
    if (alpha <= 0.001f) return@drawBehind
    val radius = size.minDimension / 2f * spread
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = alpha * 0.30f),
                Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * Мягкое свечение под карточкой — вытянутое по её форме, а не круглое.
 * Рисуется несколькими всё более широкими прямоугольниками: так дешевле
 * размытия и работает начиная с Android 5, где `blur` ещё нет.
 */
fun Modifier.softGlow(
    color: Color,
    alpha: Float = 0.22f,
    spreadDp: Dp = 14.dp,
    corner: Dp = 22.dp,
): Modifier = this.drawBehind {
    if (alpha <= 0.001f) return@drawBehind
    val s = spreadDp.toPx()
    // Ступеней много и они слабые: на пяти по краю видна ступенька, и
    // мягкий свет превращается в рамку.
    val steps = 12
    repeat(steps) { i ->
        val k = (i + 1f) / steps
        drawRoundRect(
            color = color.copy(alpha = alpha * (1f - k) * (1f - k) * 0.34f),
            topLeft = Offset(-s * k, -s * k),
            size = Size(size.width + 2 * s * k, size.height + 2 * s * k),
            cornerRadius = CornerRadius(corner.toPx() + s * k),
        )
    }
}

/* ------------------------------------------------------------------ стекло */

/**
 * Стеклянная поверхность: заливка светом сверху вниз плюс тонкая
 * рамка, которая ярче на верхней кромке. Так плоскость получает объём
 * без единой тени.
 */
fun Modifier.glass(
    shape: Shape = EfirCardShape,
    fill: Float = 0.055f,
    stroke: Float = 0.10f,
    tint: Color = Color.White,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                tint.copy(alpha = fill * 1.7f),
                tint.copy(alpha = fill * 0.45f),
            ),
        ),
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                tint.copy(alpha = stroke),
                tint.copy(alpha = stroke * 0.22f),
            ),
        ),
        shape = shape,
    )

/** Стеклянная карточка с необязательным цветным подсветом снизу. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = EfirCardShape,
    glow: Color? = null,
    glowAlpha: Float = 0.18f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .then(
                if (glow != null) Modifier.softGlow(glow, alpha = glowAlpha) else Modifier
            )
            .glass(shape),
        content = content,
    )
}

/* ------------------------------------------------------------------ отклик */

/**
 * Пружинное сжатие под пальцем — то самое ощущение «нажалось», из-за
 * которого кнопка кажется физической.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressed: Float = 0.93f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "press",
    )
    return this.scale(scale)
}

/* ------------------------------------------------------------------ фон */

/**
 * Живой фон: три медленных цветовых пятна по тёмному. Период — десятки
 * секунд, чтобы движение читалось краем глаза и не отвлекало от ленты.
 *
 * @param alive пятна светятся заметно ярче, пока идёт приём или передача.
 */
@Composable
fun EfirBackdrop(
    alive: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(38_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val lift by animateFloatAsState(
        targetValue = if (alive) 1f else 0.45f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "lift",
    )

    Box(modifier = modifier.background(EfirInk)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun blob(color: Color, cx: Float, cy: Float, r: Float, a: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = a * lift), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(cx, cy),
                )
            }
            blob(
                EfirGreen,
                w * (0.22f + 0.16f * cos(phase)),
                h * (0.14f + 0.06f * sin(phase * 1.3f)),
                w * 0.95f,
                0.16f,
            )
            blob(
                EfirSky,
                w * (0.86f + 0.12f * sin(phase * 0.8f)),
                h * (0.36f + 0.10f * cos(phase * 0.6f)),
                w * 0.85f,
                0.10f,
            )
            blob(
                EfirLilac,
                w * (0.40f + 0.20f * sin(phase * 0.5f)),
                h * (0.92f + 0.05f * cos(phase)),
                w * 1.05f,
                0.09f,
            )
        }
        content()
    }
}

/* ------------------------------------------------------------------ тумблер */

/**
 * Тумблер в духе телефонной системы: капсула, белый бегунок, цветной
 * след и ореол во включённом состоянии.
 */
@Composable
fun EfirSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = EfirGreen,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val trackWidth = 50.dp
    val trackHeight = 30.dp
    val thumb = 24.dp

    val shift by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "thumb",
    )
    val track by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.06f)
            checked -> tint.copy(alpha = 0.90f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        animationSpec = tween(220),
        label = "track",
    )

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .halo(
                color = tint,
                alpha = 0.50f * shift * (if (enabled) 1f else 0f),
                spread = 2.2f,
            )
            .pressScale(interaction, pressed = 0.90f)
            .clip(CircleShape)
            .background(track)
            .border(
                1.dp,
                Color.White.copy(alpha = if (checked) 0f else 0.10f),
                CircleShape,
            )
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        val travel = trackWidth - thumb - 6.dp
        Box(
            Modifier
                .padding(start = 3.dp)
                .offset(x = travel * shift)
                .size(thumb)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE7EFEA)))),
        )
    }
}

/* ------------------------------------------------------------------ живая точка */

/**
 * Точка состояния с дыханием. Пока элемент активен, вокруг расходится
 * волна — как индикатор записи.
 */
@Composable
fun PulseDot(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 9.dp,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave",
    )
    val shown = if (active) wave else 0f
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                if (shown > 0f) {
                    val r = this.size.minDimension / 2f * (1f + 2.6f * shown)
                    drawCircle(
                        color = color.copy(alpha = 0.38f * (1f - shown)),
                        radius = r,
                        center = center,
                    )
                }
            }
            .halo(color, alpha = if (active) 0.55f else 0f, spread = 2.4f)
            .clip(CircleShape)
            .background(if (active) color else Color.White.copy(alpha = 0.20f)),
    )
}

/* ------------------------------------------------------------------ кнопки */

/**
 * Круглая стеклянная кнопка верхнего ряда. Цвет иконки — единственный
 * способ различить их с одного взгляда, поэтому свечение берёт тот же
 * цвет.
 */
@Composable
fun GlowIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    size: Dp = 44.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val glow by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            pressed -> 0.60f
            highlighted -> 0.38f
            else -> 0.16f
        },
        animationSpec = tween(200),
        label = "iconGlow",
    )
    Box(
        modifier = modifier
            .size(size)
            .halo(tint, alpha = glow, spread = 1.7f)
            .pressScale(interaction)
            .glass(CircleShape, fill = 0.07f, stroke = if (highlighted) 0.20f else 0.10f)
            .toggleable(
                value = false,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) tint else tint.copy(alpha = 0.25f),
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/** Главная кнопка действия: заливка цветом, свечение, пружина. */
@Composable
fun GlowActionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = EfirGreen,
    size: Dp = 46.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val glow by animateFloatAsState(
        targetValue = if (enabled) 0.55f else 0f,
        animationSpec = tween(240),
        label = "sendGlow",
    )
    Box(
        modifier = modifier
            .size(size)
            .halo(tint, alpha = glow, spread = 1.9f)
            .pressScale(interaction, pressed = 0.88f)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.72f)))
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.07f),
                            Color.White.copy(alpha = 0.04f),
                        ),
                    )
                },
            )
            .toggleable(
                value = false,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) EfirInk else Color.White.copy(alpha = 0.30f),
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/** Кнопка-капсула с подписью — для действий внутри карточек. */
@Composable
fun GlowChipButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = EfirGreen,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val glow by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            pressed -> 0.45f
            filled -> 0.28f
            else -> 0.10f
        },
        animationSpec = tween(180),
        label = "chipGlow",
    )
    Row(
        modifier = modifier
            .softGlow(tint, alpha = glow * 0.8f, spreadDp = 10.dp, corner = 50.dp)
            .pressScale(interaction, pressed = 0.95f)
            .clip(EfirChipShape)
            .background(
                if (filled && enabled) tint.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                (if (enabled) tint else Color.White).copy(alpha = if (filled) 0.35f else 0.12f),
                EfirChipShape,
            )
            .toggleable(
                value = false,
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onClick() },
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) tint else Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) tint else Color.White.copy(alpha = 0.28f),
        )
    }
}

/* ------------------------------------------------------------------ мелочи */

/** Ярлычок-капсула: канал, признак, счётчик. */
@Composable
fun EfirTag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    solid: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(EfirChipShape)
            .background(color.copy(alpha = if (solid) 0.20f else 0.10f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** Тонкий разделитель — почти невидимый, только чтобы карточки не слипались. */
@Composable
fun EfirHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

/** Заголовок раздела — прописными, разреженно, приглушённо. */
@Composable
fun EfirSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Кружок с буквой вместо аватара: позывной без картинки надо чем-то держать. */
@Composable
fun HandleAvatar(
    handle: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    glow: Boolean = false,
) {
    val letter = handle.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .halo(color, alpha = if (glow) 0.35f else 0f, spread = 1.6f)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.10f)),
                ),
            )
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

/** Верхняя шапка внутренних экранов: назад, крупный заголовок, действия. */
@Composable
fun EfirScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    accent: Color = EfirGreen,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlowIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = "Назад",
                tint = accent,
                onClick = onBack,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}
