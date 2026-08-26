package ru.radioinformator.efir

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ru.radioinformator.efir.model.AccountStatus
import ru.radioinformator.efir.model.EfirMessage
import ru.radioinformator.efir.model.EfirUiState
import ru.radioinformator.efir.model.FeedStatus
import ru.radioinformator.efir.model.P2pState
import ru.radioinformator.efir.model.SiteStatus
import ru.radioinformator.efir.net.EfirPrefs
import ru.radioinformator.efir.p2p.EfirPermissions
import ru.radioinformator.efir.p2p.TxtRecordCodec
import ru.radioinformator.efir.ui.EfirAmber
import ru.radioinformator.efir.ui.EfirGreen
import ru.radioinformator.efir.ui.EfirLilac
import ru.radioinformator.efir.ui.EfirPeach
import ru.radioinformator.efir.ui.EfirRose
import ru.radioinformator.efir.ui.EfirSky
import ru.radioinformator.efir.ui.ContactsScreen
import ru.radioinformator.efir.ui.EfirTheme
import ru.radioinformator.efir.ui.JournalScreen
import ru.radioinformator.efir.ui.ScheduleScreen
import ru.radioinformator.efir.ui.Screen
import ru.radioinformator.efir.ui.WelcomeScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EfirTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    EfirScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EfirScreen() {
    val context = LocalContext.current
    // Движок один на процесс: экран гаснет, а эфир должен идти дальше.
    val radio = remember { EfirRadio.get(context) }
    val state by radio.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    var draft by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAttach by rememberSaveable { mutableStateOf(false) }
    var showDirect by rememberSaveable { mutableStateOf(false) }
    var showFeed by rememberSaveable { mutableStateOf(false) }

    /** Открытый экран. Журнал и расписание — полноценные экраны, не окошки поверх. */
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }

    /** Позывной, которому пишем лично, если пришли сюда из карточки сообщения. */
    var directTo by rememberSaveable { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { radio.refreshEnvironment() }

    // Экран держим на уровне окна: системный флаг гаснет вместе с активностью
    // сам, поэтому забыть выключить его нельзя.
    val view = LocalView.current
    DisposableEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Разрешение и тумблер геолокации могут поменяться, пока мы в фоне,
    // поэтому перечитываем их на каждом возвращении, а не один раз.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> radio.onStart()
                Lifecycle.Event.ON_RESUME -> radio.refreshEnvironment()
                Lifecycle.Event.ON_STOP -> radio.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val missing = EfirPermissions.requestedAtStart()
            .takeIf {
                it.isNotEmpty() &&
                    (!EfirPermissions.allGranted(context) ||
                        !EfirPermissions.notificationsGranted(context))
            }
        if (missing != null) permissionLauncher.launch(missing.toTypedArray())
    }

    LaunchedEffect(state.lastError) {
        state.lastError?.let {
            snackbarHostState.showSnackbar(it)
            radio.consumeError()
        }
    }
    LaunchedEffect(state.lastNotice) {
        state.lastNotice?.let {
            snackbarHostState.showSnackbar(it)
            radio.consumeNotice()
        }
    }

    // Журнал и расписание занимают весь экран: в них листают и правят, а
    // окошко поверх ленты для этого тесное.
    when (screen) {
        Screen.JOURNAL -> {
            JournalScreen(
                entries = state.journal,
                channelTitle = state::channelTitle,
                profileResolver = radio::profileLinkFor,
                onBack = { screen = Screen.MAIN },
                onDelete = radio::deleteJournalEntry,
                onClear = radio::clearJournal,
            )
            return
        }

        Screen.SCHEDULE -> {
            ScheduleScreen(
                state = state,
                onBack = { screen = Screen.MAIN },
                onAdd = radio::addScheduleRule,
                onDelete = radio::deleteScheduleRule,
                onToggleEnabled = radio::toggleSchedule,
            )
            return
        }

        Screen.CONTACTS -> {
            ContactsScreen(
                state = state,
                onBack = { screen = Screen.MAIN },
                onReload = radio::loadContacts,
                onSave = radio::saveContacts,
            )
            return
        }

        Screen.MAIN -> Unit
    }

    // Пока позывной не заведён, показывать эфир бессмысленно: передача без
    // позывного не попадёт ни в чью ленту, да и представиться надо один раз.
    if (state.accountStatus != AccountStatus.READY) {
        WelcomeScreen(
            state = state,
            onRegister = radio::register,
            onOpenSiteSettings = { showSettings = true },
        )
        if (showSettings) {
            SettingsDialog(
                state = state,
                currentToken = radio.apiToken(),
                onDismiss = { showSettings = false },
                onSave = { url, token ->
                    radio.setApiToken(token)
                    radio.setSiteUrl(url)
                    showSettings = false
                },
                onRecheck = { radio.refreshSiteInfo() },
                onChangeTransmitChannel = radio::setTransmitChannel,
                onToggleListen = radio::toggleListenChannel,
                onListenAll = radio::listenToAllChannels,
                onListenOnly = radio::listenToOnly,
                onChangeListenInterval = radio::setListenInterval,
                onChangeTransmitInterval = radio::setTransmitInterval,
                onChangePublish = radio::setPublishToFeed,
                onChangeKeepAwake = radio::setKeepAwake,
                onSignOut = null,
            )
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text(
                        text = "ЭФИР",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        fontSize = 18.sp,
                    )
                },
                actions = {
                    // Каждой кнопке свой цвет: иконок немного, и так они
                    // различаются с одного взгляда, без вчитывания.
                    ToolbarIcon(
                        icon = Icons.Filled.Lock,
                        description = "Написать лично",
                        tint = EfirAmber,
                        enabled = state.canUseRadio,
                        onClick = { showDirect = true },
                    )
                    ToolbarIcon(
                        icon = Icons.Filled.Badge,
                        description = "Моя лента",
                        tint = EfirGreen,
                        enabled = state.profileCode.isNotBlank(),
                        onClick = {
                            radio.loadMyFeed()
                            showFeed = true
                        },
                    )
                    ToolbarIcon(
                        icon = Icons.Filled.ContactPage,
                        description = "Визитка",
                        tint = EfirPeach,
                        enabled = state.profileCode.isNotBlank(),
                        onClick = { screen = Screen.CONTACTS },
                    )
                    ToolbarIcon(
                        icon = Icons.Filled.History,
                        description = "Журнал",
                        tint = EfirSky,
                        onClick = { screen = Screen.JOURNAL },
                    )
                    ToolbarIcon(
                        icon = Icons.Filled.Schedule,
                        description = "Расписание",
                        tint = EfirRose,
                        onClick = { screen = Screen.SCHEDULE },
                    )
                    ToolbarIcon(
                        icon = Icons.Filled.Settings,
                        description = "Настройки",
                        tint = EfirLilac,
                        onClick = { showSettings = true },
                    )
                },
            )
        },
        bottomBar = {
            TransmitBar(
                state = state,
                draft = draft,
                onDraftChange = { draft = it },
                onAttach = { showAttach = true },
                onSend = {
                    radio.transmit(draft)
                    draft = ""
                    keyboard?.hide()
                },
                onStop = { radio.stopTransmitting() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScanBar(
                state = state,
                onToggle = { radio.toggleScanning() },
                onToggleKeepScreenOn = radio::setKeepScreenOn,
            )
            StatusStrip(state)

            if (!state.permissionsGranted && EfirPermissions.required().isNotEmpty()) {
                NoticeCard(
                    title = "Нужно разрешение",
                    body = "«Устройства поблизости» на Android 13 и новее или точная " +
                        "геолокация на Android 12 и старше — без этого чужие передачи не видны.",
                    actionLabel = "Разрешить",
                    onAction = {
                        permissionLauncher.launch(EfirPermissions.required().toTypedArray())
                    },
                )
            }

            if (state.locationServicesRequired && !state.locationServicesEnabled) {
                NoticeCard(
                    title = "Геолокация выключена",
                    body = "До Android 13 системный тумблер геолокации управляет и поиском " +
                        "по Wi-Fi. С выключенным приём молча не приносит ничего.",
                    actionLabel = "Открыть настройки",
                    onAction = { context.startActivity(EfirPermissions.locationSettingsIntent()) },
                )
            }

            if (state.p2pState == P2pState.DISABLED) {
                NoticeCard(
                    title = "Wi-Fi выключен",
                    body = "Wi-Fi Direct живёт на том же радио. Подключаться к сети не нужно — " +
                        "достаточно, чтобы модуль был включён.",
                    actionLabel = "Открыть Wi-Fi",
                    onAction = { context.startActivity(EfirPermissions.wifiSettingsIntent()) },
                )
            }

            // Совет, выполнение которого не проверить: на Xiaomi «Без
            // ограничений» и «Автозапуск» живут в своей подсистеме, и
            // системный признак экономии после них не меняется. Поэтому
            // карточка убирается вручную и больше не возвращается.
            if (state.batteryRestricted && !state.batteryNoticeHidden) {
                NoticeCard(
                    title = "Телефон усыпит приём",
                    body = "Чтобы эфир слышался с погасшим экраном, откройте «Батарея» " +
                        "и выберите «Без ограничений». На Xiaomi там же включите " +
                        "«Автозапуск», иначе система остановит приём через несколько минут.",
                    actionLabel = "Открыть настройки",
                    onAction = {
                        context.startActivity(EfirPermissions.batterySettingsIntent(context))
                    },
                    onDismiss = radio::hideBatteryNotice,
                )
            }

            MessageList(
                messages = state.messages,
                contacts = state.contacts,
                channelTitle = state::channelTitle,
                linkResolver = radio::linkFor,
                profileResolver = radio::profileLinkFor,
                onWriteTo = { handle ->
                    directTo = handle
                    showDirect = true
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showSettings) {
        SettingsDialog(
            state = state,
            currentToken = radio.apiToken(),
            onDismiss = { showSettings = false },
            onSave = { url, token ->
                radio.setApiToken(token)
                radio.setSiteUrl(url)
                showSettings = false
            },
            onRecheck = { radio.refreshSiteInfo() },
            onChangeTransmitChannel = radio::setTransmitChannel,
            onToggleListen = radio::toggleListenChannel,
            onListenAll = radio::listenToAllChannels,
            onListenOnly = radio::listenToOnly,
            onChangeListenInterval = radio::setListenInterval,
            onChangeTransmitInterval = radio::setTransmitInterval,
            onChangePublish = radio::setPublishToFeed,
            onChangeKeepAwake = radio::setKeepAwake,
            onSignOut = {
                radio.signOut()
                showSettings = false
            },
        )
    }

    if (showDirect) {
        DirectDialog(
            state = state,
            preselected = directTo,
            onDismiss = {
                showDirect = false
                directTo = null
            },
            onSend = { handle, text ->
                radio.sendDirect(handle, text)
                keyboard?.hide()
                showDirect = false
                directTo = null
            },
            onStop = {
                radio.stopDirect()
                showDirect = false
                directTo = null
            },
        )
    }


    if (showFeed) {
        MyFeedDialog(
            state = state,
            onDismiss = { showFeed = false },
            onReload = { radio.loadMyFeed() },
            onDelete = radio::deleteMyPost,
            onOpenInBrowser = {
                radio.profileLinkFor(state.profileCode)?.let { url ->
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
        )
    }

    if (showAttach) {
        AttachDialog(
            state = state,
            onDismiss = { showAttach = false },
            onTextChange = radio::setAttachmentText,
            onClear = {
                radio.clearAttachment()
                showAttach = false
            },
        )
    }
}

/* ------------------------------------------------------------------ строка состояния */

/**
 * Кнопка верхнего меню со своим цветом и, при надобности, счётчиком.
 */
@Composable
private fun ToolbarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    badge: Int = 0,
) {
    Box {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (enabled) tint else tint.copy(alpha = 0.28f),
            )
        }
        if (badge > 0) {
            Text(
                text = if (badge > 99) "99+" else badge.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 2.dp)
                    .background(tint, CircleShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * Переключатель приёма вынесен в собственную строку: это главное действие
 * экрана, и в тесноте верхнего меню оно терялось среди иконок.
 */
@Composable
private fun ScanBar(
    state: EfirUiState,
    onToggle: () -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .padding(start = 14.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ПРИЁМ",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = if (state.isScanning) scheme.primary else scheme.onSurfaceVariant,
        )
        Switch(
            checked = state.isScanning,
            onCheckedChange = { onToggle() },
            modifier = Modifier.padding(start = 10.dp),
        )

        // Принудительный режим: экран не гаснет, пока приложение открыто.
        // Стоит рядом с приёмом намеренно — это про то же самое, «ничего не
        // пропустить», только ценой батареи.
        Spacer(Modifier.width(14.dp))
        Text(
            text = "ЭКРАН",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = if (state.keepScreenOn) EfirAmber else scheme.onSurfaceVariant,
        )
        Switch(
            checked = state.keepScreenOn,
            onCheckedChange = onToggleKeepScreenOn,
            modifier = Modifier.padding(start = 6.dp),
        )

        Text(
            text = when {
                !state.isScanning -> "выключен"
                state.listenChannels.size >= EfirPrefs.CHANNEL_MAX -> "все каналы"
                state.listenChannels.size == 1 -> {
                    val only = state.listenChannels.first()
                    "к$only · ${state.channelTitle(only)}"
                }
                else -> "каналов: ${state.listenChannels.size}"
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            color = if (state.isScanning) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
    }
}

@Composable
private fun StatusStrip(state: EfirUiState) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(active = state.isBroadcasting)
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (state.isBroadcasting) "ВЕЩАЮ К${state.transmitChannel}" else "МОЛЧУ",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = state.nick + " · " + state.peersSeen + " рядом",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusDot(active: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            ),
    )
}

@Composable
private fun NoticeCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    /**
     * Кнопка «убрать» для советов, выполнение которых мы проверить не можем.
     * Без неё карточка висит вечно, и человек, уже сделавший всё как сказано,
     * видит упрёк без конца.
     */
    onDismiss: (() -> Unit)? = null,
    dismissLabel: String = "Уже сделал",
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            dismissLabel,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TextButton(onClick = onAction) {
                    Text(actionLabel, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ лента */

@Composable
private fun MessageList(
    messages: List<EfirMessage>,
    contacts: List<ru.radioinformator.efir.model.Contact>,
    channelTitle: (Int) -> String,
    linkResolver: (String) -> String?,
    profileResolver: (String) -> String?,
    onWriteTo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Новое сверху: это список принимаемых передач, а не переписка, и
    // прокручиваться к последнему сообщению вниз здесь незачем.
    val ordered = remember(messages) { messages.asReversed() }

    LaunchedEffect(messages.size) {
        if (ordered.isNotEmpty()) listState.animateScrollToItem(0)
    }

    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = "В ЭФИРЕ ПУСТО\n\nВключите приём и подождите.\nВсё, что передают рядом,\nпоявится здесь.",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(items = ordered, key = { it.id }) { message ->
            val known = contacts.any { it.handle.equals(message.nick, ignoreCase = true) }
            MessageRow(
                message = message,
                channelTitle = channelTitle,
                linkResolver = linkResolver,
                profileResolver = profileResolver,
                canWrite = known,
                onWriteTo = onWriteTo,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * Одна строка списка передач — во всю ширину.
 *
 * Это не переписка, а поток принимаемых сообщений, поэтому пузырей и
 * выравнивания по краям здесь нет: свои и чужие отличаются подписью и
 * цветной полосой слева, а не положением на экране.
 */
@Composable
private fun MessageRow(
    message: EfirMessage,
    channelTitle: (Int) -> String,
    linkResolver: (String) -> String?,
    profileResolver: (String) -> String?,
    canWrite: Boolean,
    onWriteTo: (String) -> Unit,
) {
    val isLocal = message.origin == EfirMessage.Origin.LOCAL
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val stamp = remember(message.receivedAtMillis) {
        SimpleDateFormat("dd.MM HH:mm:ss", Locale("ru")).format(Date(message.receivedAtMillis))
    }
    val accent = when {
        message.isDirect -> scheme.secondary
        isLocal -> scheme.onSurfaceVariant
        else -> scheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isLocal) scheme.surface else scheme.background)
            .height(IntrinsicSize.Min),
    ) {
        // Полоса слева — единственный признак «своё или чужое».
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )

        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (message.isDirect) {
                DirectBadge()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        isLocal && message.peerHandle != null ->
                            "Я → " + message.peerHandle.uppercase(Locale("ru"))
                        isLocal -> "Я"
                        else -> message.nick.uppercase(Locale("ru"))
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "к${message.channel} · ${channelTitle(message.channel)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stamp,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = scheme.onSurfaceVariant,
                )
            }

            if (message.text.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    color = scheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            message.linkCode?.let { code ->
                val url = linkResolver(code)
                RowAction(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    // Без адреса сети код всё равно показываем: его можно
                    // набрать руками, когда адрес будет настроен.
                    label = if (url != null) "открыть /p/$code" else "вложение /p/$code",
                    enabled = url != null,
                    onClick = {
                        url?.let {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(it))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                )
            }

            if (!isLocal) {
                // Код ленты приходит только из эфира: в сети нет ни каталога,
                // ни поиска, так что это единственный путь к чужой ленте.
                message.profileCode?.let { profile ->
                    profileResolver(profile)?.let { profileUrl ->
                        RowAction(
                            icon = Icons.Filled.Badge,
                            label = "лента ${message.nick}",
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                        )
                    }
                }

                // Написать можно только знакомому: ключ приезжает с его
                // передачей, без ключа шифровать нечем.
                if (canWrite) {
                    RowAction(
                        icon = Icons.Filled.Lock,
                        label = "написать сообщение",
                        tint = MaterialTheme.colorScheme.secondary,
                        onClick = { onWriteTo(message.nick) },
                    )
                }
            }

            message.sourceDeviceName?.takeIf { it.isNotBlank() && !isLocal }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Отметка личного сообщения. Одного замка мало: разница между «слышали все
 * вокруг» и «прочитал только адресат» слишком важная, чтобы держать её
 * в мелкой иконке.
 */
@Composable
private fun DirectBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "ЛИЧНОЕ СООБЩЕНИЕ",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

/** Действие под сообщением: иконка и подпись в одну строку. */
@Composable
private fun RowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ------------------------------------------------------------------ передача */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransmitBar(
    state: EfirUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val used = TxtRecordCodec.utf8Length(draft)
    val budget = state.limits.broadcastMaxBytes
    val overBudget = used > budget
    val hasAttachment = !state.attachment.isEmpty
    val canSend = state.canUseRadio &&
        !state.isUploading &&
        (draft.isNotBlank() || hasAttachment) &&
        !overBudget

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAttach) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Notes,
                    contentDescription = "Дописать текст",
                    tint = if (hasAttachment) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                enabled = state.canUseRadio,
                isError = overBudget,
                singleLine = false,
                maxLines = 3,
                placeholder = {
                    Text(
                        "Сообщение всем, кто рядом",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                shape = RoundedCornerShape(8.dp),
            )
            Spacer(Modifier.width(4.dp))
            if (state.isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(10.dp))
            } else {
                IconButton(onClick = onSend, enabled = canSend) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Передать",
                        tint = if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
            }
            if (state.isBroadcasting) {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Filled.StopCircle,
                        contentDescription = "Снять с эфира",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Text(
            text = buildString {
                append("$used/$budget байт")
                if (hasAttachment) {
                    append("  ·  с текстом в сети")
                }
                if (state.isUploading) append("  ·  отправляю в сеть…")
                else if (state.isBroadcasting) append("  ·  в эфире, отправка заменит")
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            // Раньше строка была цвета outline и на тёмном фоне не читалась.
            color = when {
                overBudget -> MaterialTheme.colorScheme.error
                state.isBroadcasting || state.isUploading -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 4.dp, start = 8.dp),
        )
    }
}

/* ------------------------------------------------------------------ диалоги */

@Composable
private fun SettingsDialog(
    state: EfirUiState,
    currentToken: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onRecheck: () -> Unit,
    onChangeTransmitChannel: (Int) -> Unit,
    onToggleListen: (Int) -> Unit,
    onListenAll: () -> Unit,
    onListenOnly: (Int) -> Unit,
    onChangeListenInterval: (Int) -> Unit,
    onChangeTransmitInterval: (Int) -> Unit,
    onChangePublish: (Boolean) -> Unit,
    onChangeKeepAwake: (Boolean) -> Unit,
    /** null — на экране знакомства, выходить ещё неоткуда. */
    onSignOut: (() -> Unit)?,
) {
    var url by remember { mutableStateOf(state.siteUrl) }
    var token by remember { mutableStateOf(currentToken) }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Настройки", fontFamily = FontFamily.Monospace) },
        text = {
            Column(Modifier.verticalScroll(scroll)) {

                if (onSignOut != null) {
                    SettingsSection("Эфир")

                    ChannelPicker(
                        label = "Вещаю на",
                        channel = state.transmitChannel,
                        title = state.channelTitle(state.transmitChannel),
                        onChange = onChangeTransmitChannel,
                    )

                    Spacer(Modifier.height(12.dp))
                    ListenChannels(
                        state = state,
                        onToggle = onToggleListen,
                        onAll = onListenAll,
                        onOnly = onListenOnly,
                    )

                    Spacer(Modifier.height(12.dp))
                    IntervalPicker(
                        label = "Обновлять приём каждые",
                        seconds = state.listenIntervalSeconds,
                        min = EfirPrefs.LISTEN_INTERVAL_MIN,
                        max = EfirPrefs.LISTEN_INTERVAL_MAX,
                        onChange = onChangeListenInterval,
                    )
                    IntervalPicker(
                        label = "Повторять передачу каждые",
                        seconds = state.transmitIntervalSeconds,
                        min = EfirPrefs.TRANSMIT_INTERVAL_MIN,
                        max = EfirPrefs.TRANSMIT_INTERVAL_MAX,
                        onChange = onChangeTransmitInterval,
                    )
                    Text(
                        text = "Чаще — быстрее находите друг друга и быстрее садится батарея.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))
                    SettingsSection("Погасший экран")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = state.keepAwake, onCheckedChange = onChangeKeepAwake)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Слушать эфир при выключенном экране",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = if (state.keepAwake) {
                            "Телефон не будет засыпать, пока идёт приём: объявление соседа " +
                                "дойдёт и из кармана. Батарея при этом садится заметно быстрее."
                        } else {
                            "Телефон засыпает как обычно. Приём в кармане будет урывками — " +
                                "часть передач пройдёт мимо."
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.batteryRestricted) {
                        Text(
                            text = "Система всё равно ограничивает это приложение ради батареи. " +
                                "Откройте «Батарея» в свойствах приложения и поставьте " +
                                "«Без ограничений», на Xiaomi — ещё и «Автозапуск».",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = EfirAmber,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    SettingsSection("Лента")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = state.publishToFeed, onCheckedChange = onChangePublish)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Публиковать передачи в мою ленту",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = "Позывной: ${state.nick}\nКод ленты: ${state.profileCode}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )

                    Spacer(Modifier.height(16.dp))
                    SettingsSection("Сайт")
                }

                Text(
                    text = "Куда складывать записи и откуда брать лимиты. " +
                        "Само общение по воздуху работает и без сайта.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Адрес", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                    placeholder = {
                        Text("https://radioinformator.ru", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                if (state.siteRequiresToken || token.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        singleLine = true,
                        label = { Text("Токен", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = when (state.siteStatus) {
                        SiteStatus.NOT_CONFIGURED -> "Адрес не задан — ленту вести негде."
                        SiteStatus.CHECKING -> "Проверяю связь…"
                        SiteStatus.ONLINE ->
                            "Связь есть: «${state.siteName}». " +
                                "Текст до ${state.limits.maxTextChars} символов."
                        SiteStatus.OFFLINE -> "Сайт не отвечает. Проверьте адрес и интернет."
                        SiteStatus.UNKNOWN -> "Связь ещё не проверялась."
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = when (state.siteStatus) {
                        SiteStatus.ONLINE -> MaterialTheme.colorScheme.primary
                        SiteStatus.OFFLINE -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                TextButton(onClick = onRecheck) {
                    Text("Проверить связь", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }

                onSignOut?.let { signOut ->
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = signOut) {
                        Text(
                            "Забыть позывной на этом телефоне",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url, token) }) {
                Text("Сохранить", fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", fontFamily = FontFamily.Monospace) }
        },
    )
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title.uppercase(Locale("ru")),
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * Выбор канала стрелками, а не списком: каналов немного, а диалог и без того
 * длинный. Частота рядом — чтобы номер канала связывался со шкалой на сайте.
 */
/**
 * Выбор каналов для приёма.
 *
 * Слушать можно сколько угодно сразу: все передачи и так приезжают в
 * приложение, отсев чисто программный. Единственная плата — шум в списке,
 * поэтому решение оставлено человеку.
 */
@Composable
private fun ListenChannels(
    state: EfirUiState,
    onToggle: (Int) -> Unit,
    onAll: () -> Unit,
    onOnly: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Text(
        text = "СЛУШАЮ",
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = scheme.primary,
    )

    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        TextButton(onClick = onAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("все", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        TextButton(
            onClick = { onOnly(EfirPrefs.CHANNEL_DEFAULT) },
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text("только общий", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }

    (EfirPrefs.CHANNEL_MIN..EfirPrefs.CHANNEL_MAX).forEach { channel ->
        val isAlarm = channel == EfirPrefs.ALARM_CHANNEL
        val checked = channel in state.listenChannels

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(channel) }
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle(channel) })
            Spacer(Modifier.width(2.dp))
            Text(
                text = "к$channel",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.width(30.dp),
            )
            Text(
                text = state.channelTitle(channel),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = when {
                    isAlarm -> scheme.error
                    checked -> scheme.onSurface
                    else -> scheme.onSurfaceVariant
                },
            )
        }
    }

    Text(
        text = if (state.alarmMuted) {
            "Тревожный канал выключен вручную. Пока он молчит, просьбу о помощи " +
                "рядом вы не услышите."
        } else {
            "Тревожный канал слушается всегда — в этом его смысл. Снять галочку " +
                "можно, но лучше не надо."
        },
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color = if (state.alarmMuted) scheme.error else scheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun ChannelPicker(
    label: String,
    channel: Int,
    title: String,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onChange(channel - 1) },
            enabled = channel > EfirPrefs.CHANNEL_MIN,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Канал ниже")
        }
        // Тема важнее номера: человек ищет «Еду», а не «канал 2».
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(120.dp),
        ) {
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "канал $channel",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = { onChange(channel + 1) },
            enabled = channel < EfirPrefs.CHANNEL_MAX,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Канал выше")
        }
    }
}

@Composable
private fun IntervalPicker(
    label: String,
    seconds: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(seconds - 5) }, enabled = seconds > min) {
            Icon(Icons.Filled.Remove, contentDescription = "Реже")
        }
        Text(
            text = "$seconds с",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
        IconButton(onClick = { onChange(seconds + 5) }, enabled = seconds < max) {
            Icon(Icons.Filled.Add, contentDescription = "Чаще")
        }
    }
}

/**
 * Написать лично можно только тому, чью передачу мы уже слышали: вместе с ней
 * приезжает его ключ. Списка «всех пользователей» здесь нет и быть не может —
 * круг собеседников набирается ногами.
 */
@Composable
private fun DirectDialog(
    state: EfirUiState,
    preselected: String?,
    onDismiss: () -> Unit,
    onSend: (handle: String, text: String) -> Unit,
    onStop: () -> Unit,
) {
    var recipient by rememberSaveable(preselected) {
        mutableStateOf(preselected ?: state.contacts.firstOrNull()?.handle ?: "")
    }
    var text by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()

    val used = TxtRecordCodec.utf8Length(text)
    val budget = TxtRecordCodec.MAX_DIRECT_BYTES
    val overBudget = used > budget
    val canSend = recipient.isNotBlank() && text.isNotBlank() && !overBudget

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Написать лично", fontFamily = FontFamily.Monospace, fontSize = 15.sp) },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                if (state.contacts.isEmpty()) {
                    Text(
                        text = "Пока некому. Список собеседников пополняется только эфиром: " +
                            "поймайте чью-нибудь передачу — вместе с ней придёт его ключ, " +
                            "и человек появится здесь.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Кому",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))

                    state.contacts.forEach { contact ->
                        val selected = contact.handle.equals(recipient, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { recipient = contact.handle },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = contact.handle,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        isError = overBudget,
                        maxLines = 4,
                        label = {
                            Text("Сообщение", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        supportingText = {
                            Text(
                                "$used/$budget байт",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                            )
                        },
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Текст зашифрован ключом получателя: в эфире виден только факт, " +
                            "что кто-то кому-то написал. Прямой секретности нет — если ваше " +
                            "кодовое слово узнают, старые записки тоже прочтут.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    state.directRecipient?.let { current ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Сейчас в эфире письмо для $current. Новое его заменит.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(recipient, text) }, enabled = canSend) {
                Text("Передать", fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            if (state.directRecipient != null) {
                TextButton(onClick = onStop) {
                    Text("Снять с эфира", fontFamily = FontFamily.Monospace)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Закрыть", fontFamily = FontFamily.Monospace)
                }
            }
        },
    )
}

/**
 * Своя лента в сети: то, что уже опубликовано под вашим позывным.
 *
 * Здесь же записи и удаляются. Управлять лентой можно только отсюда: на сайте
 * для этого пришлось бы вводить кодовое слово, а оно телефон не покидает.
 */
@Composable
private fun MyFeedDialog(
    state: EfirUiState,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
    onDelete: (String) -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    var confirmCode by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Моя лента" + if (state.feedStatus == FeedStatus.READY) {
                    " · ${state.feedTotal}"
                } else {
                    ""
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
            )
        },
        text = {
            when (state.feedStatus) {
                FeedStatus.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Загружаю…", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }

                FeedStatus.FAILED -> Text(
                    text = "Лента не загрузилась: ${state.feedError ?: "нет связи"}. " +
                        "Она живёт в сети, поэтому без интернета её не показать.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> if (state.feed.isEmpty()) {
                    Text(
                        text = "Пока пусто. Сюда попадают ваши передачи, если включена " +
                            "публикация в ленту.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(items = state.feed, key = { it.code }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = entry.createdHuman,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "к${entry.channel}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (entry.hidden) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "НА ПРОВЕРКЕ",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                            )
                                        }
                                    }
                                    Text(
                                        text = entry.broadcast.ifBlank { entry.excerpt },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                IconButton(
                                    onClick = { confirmCode = entry.code },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Удалить из ленты",
                                        modifier = Modifier.size(16.dp),
                                        tint = EfirRose,
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReload) {
                    Text("Обновить", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                TextButton(onClick = onOpenInBrowser) {
                    Text("В браузере", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        },
    )

    // Удаление из ленты необратимо — записи в сети больше не будет.
    confirmCode?.let { code ->
        AlertDialog(
            onDismissRequest = { confirmCode = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Удалить запись?", fontFamily = FontFamily.Monospace, fontSize = 15.sp) },
            text = {
                Text(
                    text = "Запись /p/$code исчезнет из сети навсегда. У тех, кто уже " +
                        "принял её из эфира, она останется в журнале — это их копия.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(code)
                    confirmCode = null
                }) {
                    Text("Удалить", fontFamily = FontFamily.Monospace, color = EfirRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCode = null }) {
                    Text("Отмена", fontFamily = FontFamily.Monospace)
                }
            },
        )
    }
}

@Composable
private fun AttachDialog(
    state: EfirUiState,
    onDismiss: () -> Unit,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val siteReady = state.siteStatus == SiteStatus.ONLINE
    val textLength = state.attachment.text.length
    val overText = textLength > state.limits.maxTextChars

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Дописать к передаче", fontFamily = FontFamily.Monospace, fontSize = 15.sp) },
        text = {
            Column {
                Text(
                    text = if (siteReady) {
                        "Длинный текст уедет на сайт, а в эфир добавится только короткий " +
                            "код страницы — пять символов. Файлы не прикрепляются: " +
                            "НА ЭФИРЕ живёт один текст."
                    } else {
                        "Сайт недоступен. Задайте его адрес в настройках, иначе дописывать " +
                            "некуда — по воздуху проходит лишь короткая строка."
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = if (siteReady) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.attachment.text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = siteReady,
                    isError = overText,
                    maxLines = 8,
                    label = {
                        Text("Полный текст", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    supportingText = {
                        Text(
                            "$textLength/${state.limits.maxTextChars} символов",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !overText) {
                Text("Готово", fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text("Убрать", fontFamily = FontFamily.Monospace)
            }
        },
    )
}
