package ru.radioinformator.efir.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "ЭФИР",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 12.sp,
            fontSize = 30.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "автономный радиоинформатор",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(36.dp))

        Text(
            text = "Придумайте позывной и кодовое слово. Больше о вас ничего " +
                "не спросят: ни почты, ни номера, ни имени.",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 340.dp),
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it },
            modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp),
            enabled = !busy,
            singleLine = true,
            isError = handleError != null,
            label = { Text("Позывной", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            supportingText = {
                Text(
                    handleError ?: "Его увидят все, кто поймает вашу передачу",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            },
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it },
            modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp),
            enabled = !busy,
            singleLine = true,
            isError = secretError != null,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("Кодовое слово", fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            supportingText = {
                Text(
                    secretError ?: "Не покидает телефон. На сайт уходит только его отпечаток",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            },
        )

        Spacer(Modifier.height(18.dp))

        Text(
            text = "Запишите кодовое слово где-нибудь. Восстановить его нельзя " +
                "никому — ни вам, ни владельцу сайта: его там попросту нет. " +
                "Тот же позывной и то же слово на другом телефоне откроют ту же ленту.",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.widthIn(max = 340.dp),
        )

        state.registerError?.let { error ->
            Spacer(Modifier.height(14.dp))
            Text(
                text = error,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
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
            Button(
                onClick = { onRegister(handle, secret) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp),
            ) {
                Text("Выйти в эфир", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(10.dp))

        TextButton(onClick = onOpenSiteSettings, enabled = !busy) {
            Text(
                text = if (state.siteUrl.isBlank()) {
                    "Указать адрес сети"
                } else {
                    "Сеть: ${state.siteUrl.removePrefix("https://").removePrefix("http://")}"
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }
}
