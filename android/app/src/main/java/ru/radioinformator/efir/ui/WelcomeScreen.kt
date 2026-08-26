package ru.radioinformator.efir.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.radioinformator.efir.model.AccountStatus
import ru.radioinformator.efir.model.EfirUiState
import ru.radioinformator.efir.net.Identity

/**
 * Первый экран: назвать позывной и кодовое слово.
 *
 * Это единственная «регистрация» в приложении. Ни почты, ни телефона, ни
 * подтверждений — и об этом честно написано прямо на экране, потому что
 * последствие серьёзное: забытое кодовое слово не восстанавливается.
 */
@Composable
fun WelcomeScreen(
    state: EfirUiState,
    onRegister: (handle: String, secret: String) -> Unit,
    onOpenSiteSettings: () -> Unit,
) {
    var handle by rememberSaveable { mutableStateOf("") }
    var secret by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()
    val busy = state.accountStatus == AccountStatus.REGISTERING

    val handleError = remember(handle) {
        if (handle.isBlank()) null else Identity.validateHandle(handle)
    }
    val secretError = remember(secret) {
        if (secret.isBlank()) null else Identity.validateSecret(secret)
    }
    val canSubmit = !busy &&
        handle.isNotBlank() && secret.isNotBlank() &&
        handleError == null && secretError == null

    EfirBackdrop(alive = true, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BeaconMark()

            Spacer(Modifier.height(22.dp))
            Text(
                text = "ЭФИР",
                fontWeight = FontWeight.Black,
                letterSpacing = 10.sp,
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.halo(EfirGreen, alpha = 0.22f, spread = 1.1f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "автономный радиоинформатор",
                fontSize = 13.sp,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(34.dp))

            Text(
                text = "Придумайте позывной и кодовое слово. Больше о вас ничего " +
                    "не спросят: ни почты, ни номера, ни имени.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 340.dp),
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = handle,
                onValueChange = { handle = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 340.dp),
                enabled = !busy,
                singleLine = true,
                isError = handleError != null,
                shape = RoundedCornerShape(16.dp),
                label = { Text("Позывной", fontSize = 12.sp) },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = welcomeFieldColors(),
                supportingText = {
                    Text(
                        handleError ?: "Его увидят все, кто поймает вашу передачу",
                        fontSize = 11.sp,
                    )
                },
            )

            Spacer(Modifier.height(6.dp))

            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 340.dp),
                enabled = !busy,
                singleLine = true,
                isError = secretError != null,
                shape = RoundedCornerShape(16.dp),
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("Кодовое слово", fontSize = 12.sp) },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = welcomeFieldColors(),
                supportingText = {
                    Text(
                        secretError ?: "Не покидает телефон. На сайт уходит только его отпечаток",
                        fontSize = 11.sp,
                    )
                },
            )

            Spacer(Modifier.height(14.dp))

            GlassCard(
                modifier = Modifier.widthIn(max = 340.dp),
                glow = EfirAmber,
                glowAlpha = 0.10f,
            ) {
                Text(
                    text = "Запишите кодовое слово где-нибудь. Восстановить его нельзя " +
                        "никому — ни вам, ни владельцу сайта: его там попросту нет. " +
                        "Тот же позывной и то же слово на другом телефоне откроют ту же ленту.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(14.dp),
                )
            }

            state.registerError?.let { error ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.widthIn(max = 340.dp),
                )
            }

            Spacer(Modifier.height(22.dp))

            if (busy) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            } else {
                GlowWideButton(
                    label = "Выйти в эфир",
                    enabled = canSubmit,
                    onClick = { onRegister(handle, secret) },
                    modifier = Modifier.widthIn(max = 340.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            TextButton(onClick = onOpenSiteSettings, enabled = !busy) {
                Text(
                    text = if (state.siteUrl.isBlank()) {
                        "Указать адрес сети"
                    } else {
                        "Сеть: ${state.siteUrl.removePrefix("https://").removePrefix("http://")}"
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Знак приложения на первом экране: расходящиеся круги вокруг значка.
 * Показывает суть раньше любого текста — телефон, который зовёт вокруг.
 */
@Composable
private fun BeaconMark() {
    val transition = rememberInfiniteTransition(label = "beacon")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "beaconWave",
    )
    Box(
        modifier = Modifier
            .size(132.dp)
            .drawBehind {
                repeat(3) { i ->
                    val p = ((wave + i / 3f) % 1f)
                    drawCircle(
                        color = EfirGreen.copy(alpha = 0.30f * (1f - p)),
                        radius = size.minDimension / 2f * (0.32f + p * 0.68f),
                        center = center,
                        style = Stroke(width = 1.6.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .halo(EfirGreen, alpha = 0.35f, spread = 1.7f)
                .glass(RoundedCornerShape(22.dp), fill = 0.09f, stroke = 0.18f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Sensors,
                contentDescription = null,
                tint = EfirGreen,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun welcomeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = EfirGreen.copy(alpha = 0.55f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
    focusedContainerColor = Color.White.copy(alpha = 0.04f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
    focusedLabelColor = EfirGreen,
    cursorColor = EfirGreen,
)
