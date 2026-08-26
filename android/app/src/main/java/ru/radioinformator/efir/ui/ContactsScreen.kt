package ru.radioinformator.efir.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import ru.radioinformator.efir.model.ContactCard
import ru.radioinformator.efir.model.ContactsStatus
import ru.radioinformator.efir.model.EfirUiState

/**
 * Визитка на странице своей ленты.
 *
 * Сеть анонимна, и ломать это незачем — но кафе, мастеру или рынку прятаться
 * ни к чему: им нужно, чтобы после эфира с ними связались. Поэтому визитка
 * добровольная и пустая по умолчанию: незаполненные строки на сайт не
 * попадают вовсе.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    state: EfirUiState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onSave: (ContactCard) -> Unit,
) {
    // Черновик отдельно от состояния: пока правишь, сеть может ответить на
    // предыдущее сохранение, и подменять текст под пальцами нельзя.
    var draft by rememberSaveable(stateSaver = ContactCardSaver) {
        mutableStateOf(state.contactCard)
    }

    var loadedOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!loadedOnce) {
            loadedOnce = true
            onReload()
        }
    }

    // Пришедшее из сети принимаем, пока человек ничего не набрал.
    LaunchedEffect(state.contactCard) {
        if (state.contactsStatus == ContactsStatus.READY && draft.isEmpty) {
            draft = state.contactCard
        }
    }

    val busy = state.contactsStatus == ContactsStatus.LOADING ||
        state.contactsStatus == ContactsStatus.SAVING

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = EfirGreen,
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "ВИЗИТКА",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            fontSize = 15.sp,
                        )
                    },
                    actions = {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp).padding(end = 4.dp),
                                strokeWidth = 2.dp,
                                color = EfirAmber,
                            )
                        } else {
                            TextButton(onClick = onReload) {
                                Text("обновить", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = "Это единственное место, где сеть что-то о вас знает. " +
                        "Пустые строки на страницу ленты не попадают, а сама страница " +
                        "открывается только по коду из эфира — ни списка людей, ни " +
                        "поиска на сайте нет.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                )

                ContactField(
                    label = "Имя или название",
                    hint = "Кафе на Пушкина",
                    value = draft.name,
                    onChange = { draft = draft.copy(name = it) },
                )
                ContactField(
                    label = "Телефон",
                    hint = "+7 900 000-00-00",
                    value = draft.phone,
                    onChange = { draft = draft.copy(phone = it) },
                    keyboardType = KeyboardType.Phone,
                )
                ContactField(
                    label = "Почта",
                    hint = "name@example.ru",
                    value = draft.email,
                    onChange = { draft = draft.copy(email = it) },
                    keyboardType = KeyboardType.Email,
                )
                ContactField(
                    label = "Сайт",
                    hint = "example.ru",
                    value = draft.site,
                    onChange = { draft = draft.copy(site = it) },
                    keyboardType = KeyboardType.Uri,
                )
                ContactField(
                    label = "Telegram",
                    hint = "имя без собаки",
                    value = draft.telegram,
                    onChange = { draft = draft.copy(telegram = it) },
                )
                ContactField(
                    label = "VK",
                    hint = "короткое имя страницы",
                    value = draft.vk,
                    onChange = { draft = draft.copy(vk = it) },
                )
                ContactField(
                    label = "Другие соцсети",
                    hint = "ссылки через запятую",
                    value = draft.social,
                    onChange = { draft = draft.copy(social = it) },
                    imeAction = ImeAction.Done,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Показывать на сайте",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Выключите, чтобы спрятать визитку, не стирая заполненного",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = draft.public,
                        onCheckedChange = { draft = draft.copy(public = it) },
                    )
                }

                state.contactsError?.let { error ->
                    Text(
                        text = error,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = EfirRose,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                Button(
                    onClick = { onSave(draft) },
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text(
                        text = if (state.contactsStatus == ContactsStatus.SAVING) {
                            "СОХРАНЯЕМ…"
                        } else {
                            "СОХРАНИТЬ"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontSize = 13.sp,
                    )
                }

                TextButton(
                    onClick = { draft = ContactCard(public = draft.public) },
                    enabled = !busy && !draft.isEmpty,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "очистить все поля",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = EfirRose,
                    )
                }

                Text(
                    text = "Пустая визитка со страницы исчезает целиком: сохраните " +
                        "пустые поля — и сеть снова не будет знать о вас ничего.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ContactField(
    label: String,
    hint: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(10.dp),
        label = { Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
        placeholder = {
            Text(
                hint,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
    )
}

/** Черновик визитки должен переживать поворот экрана — иначе набранное пропадёт. */
private val ContactCardSaver = listSaver<ContactCard, Any>(
    save = { listOf(it.name, it.phone, it.email, it.site, it.telegram, it.vk, it.social, it.public) },
    restore = {
        ContactCard(
            name = it[0] as String,
            phone = it[1] as String,
            email = it[2] as String,
            site = it[3] as String,
            telegram = it[4] as String,
            vk = it[5] as String,
            social = it[6] as String,
            public = it[7] as Boolean,
        )
    },
)
