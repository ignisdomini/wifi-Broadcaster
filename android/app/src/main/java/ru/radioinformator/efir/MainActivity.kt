package ru.radioinformator.efir

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import ru.radioinformator.efir.ui.EfirTileShape
import ru.radioinformator.efir.ui.EfirSectionLabel
import ru.radioinformator.efir.ui.EfirInk
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.border
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import ru.radioinformator.efir.ui.EfirBackdrop
import ru.radioinformator.efir.ui.EfirChipShape
import ru.radioinformator.efir.ui.EfirHairline
import ru.radioinformator.efir.ui.EfirMono
import ru.radioinformator.efir.ui.EfirMuted
import ru.radioinformator.efir.ui.EfirSans
import ru.radioinformator.efir.ui.EfirSwitch
import ru.radioinformator.efir.ui.EfirTag
import ru.radioinformator.efir.ui.GlassCard
import ru.radioinformator.efir.ui.GlowActionButton
import ru.radioinformator.efir.ui.GlowChipButton
import ru.radioinformator.efir.ui.GlowIconButton
import ru.radioinformator.efir.ui.HandleAvatar
import ru.radioinformator.efir.ui.PulseDot
import ru.radioinformator.efir.ui.glass
import ru.radioinformator.efir.ui.halo
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
        // Сквозной режим: подложка со свечением уходит под системные панели,
        // иначе сверху и снизу остаются чёрные полосы поперёк градиента.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
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
    // Системный «назад» на внутреннем экране должен возвращать в ленту, а
    // не закрывать приложение: журнал и расписание — это разделы, а не
    // отдельные входы.
    BackHandler(enabled = screen != Screen.MAIN) { screen = Screen.MAIN }

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

    EfirBackdrop(
        alive = state.isScanning || state.isBroadcasting,
        modifier = Modifier.fillMaxSize(),
    ) {
        Scaffold(
            // Фон рисует подложка со свечением, поэтому сам каркас прозрачен.
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                EfirHeader(
                    state = state,
                    onDirect = { showDirect = true },
                    onFeed = {
                        radio.loadMyFeed()
                        showFeed = true
                    },
                    onContacts = { screen = Screen.CONTACTS },
                    onJournal = { screen = Screen.JOURNAL },
                    onSchedule = { screen = Screen.SCHEDULE },
                    onSettings = { showSettings = true },
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
                Spacer(Modifier.height(6.dp))
                ControlCard(
                    state = state,
                    onToggleScan = { radio.toggleScanning() },
                    onToggleKeepScreenOn = radio::setKeepScreenOn,
                )
                Spacer(Modifier.height(12.dp))

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

/* ------------------------------------------------------------------ шапка */

/**
 * Шапка главного экрана: марка, живая строка состояния и ряд круглых
 * кнопок. Раньше здесь был системный TopAppBar, и шесть иконок рядом
 * с заголовком стояли впритык — теперь у каждой своё стекло и воздух.
 */
@Composable
private fun EfirHeader(
    state: EfirUiState,
    onDirect: () -> Unit,
    onFeed: () -> Unit,
    onContacts: () -> Unit,
    onJournal: () -> Unit,
    onSchedule: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ЭФИР",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 5.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.halo(EfirGreen, alpha = 0.18f, spread = 1.1f),
            )
            Spacer(Modifier.weight(1f))
            LiveStatusPill(state)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlowIconButton(
                icon = Icons.Filled.Lock,
                description = "Написать лично",
                tint = EfirAmber,
                enabled = state.canUseRadio,
                onClick = onDirect,
            )
            GlowIconButton(
                icon = Icons.Filled.Badge,
                description = "Моя лента",
                tint = EfirGreen,
                enabled = state.profileCode.isNotBlank(),
                onClick = onFeed,
            )
            GlowIconButton(
                icon = Icons.Filled.ContactPage,
                description = "Визитка",
                tint = EfirPeach,
                enabled = state.profileCode.isNotBlank(),
                onClick = onContacts,
            )
            GlowIconButton(
                icon = Icons.Filled.History,
                description = "Журнал",
                tint = EfirSky,
                onClick = onJournal,
            )
            GlowIconButton(
                icon = Icons.Filled.Schedule,
                description = "Расписание",
                tint = EfirRose,
                highlighted = state.schedule.enabled,
                onClick = onSchedule,
            )
            GlowIconButton(
                icon = Icons.Filled.Settings,
                description = "Настройки",
                tint = EfirLilac,
                onClick = onSettings,
            )
        }
    }
}

/**
 * Капсула состояния: дышащая точка и одна строка. Это всё, что нужно
 * знать не глядя — идёт ли передача и сколько устройств рядом.
 */
@Composable
private fun LiveStatusPill(state: EfirUiState) {
    val live = state.isBroadcasting
    val color = if (live) EfirGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .glass(EfirChipShape, fill = 0.05f, stroke = if (live) 0.18f else 0.08f)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        PulseDot(active = live, color = if (live) EfirGreen else EfirMuted, size = 8.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (live) "в эфире · к${state.transmitChannel}" else "молчу",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${state.peersSeen} рядом",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ------------------------------------------------------------------ управление */

/**
 * Карточка управления. Приём — главное действие экрана, поэтому он
 * занимает целую строку с подписью о том, что именно слушаем, а не
 * ютится тумблером в углу.
 */
@Composable
private fun ControlCard(
    state: EfirUiState,
    onToggleScan: () -> Unit,
    onToggleKeepScreenOn: (Boolean) -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        glow = if (state.isScanning) EfirGreen else null,
        glowAlpha = 0.13f,
    ) {
        ControlRow(
            icon = Icons.Filled.Sensors,
            tint = EfirGreen,
            title = "Приём",
            subtitle = when {
                !state.isScanning -> "выключен — чужие передачи не видны"
                state.listenChannels.size >= EfirPrefs.CHANNEL_MAX -> "слушаю все каналы"
                state.listenChannels.size == 1 -> {
                    val only = state.listenChannels.first()
                    "к$only · ${state.channelTitle(only)}"
                }
                else -> "каналов: ${state.listenChannels.size}"
            },
            checked = state.isScanning,
            onCheckedChange = { onToggleScan() },
        )
        EfirHairline(Modifier.padding(horizontal = 16.dp))
        ControlRow(
            icon = Icons.Filled.LightMode,
            tint = EfirAmber,
            title = "Не гасить экран",
            subtitle = if (state.keepScreenOn) {
                "экран горит, пока открыто приложение"
            } else {
                "экран гаснет как обычно"
            },
            checked = state.keepScreenOn,
            onCheckedChange = onToggleKeepScreenOn,
        )
    }
}

/** Строка карточки управления: значок, две подписи и тумблер. */
@Composable
private fun ControlRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .halo(tint, alpha = if (checked) 0.30f else 0f, spread = 1.5f)
                .clip(RoundedCornerShape(11.dp))
                .background(tint.copy(alpha = if (checked) 0.20f else 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) tint else tint.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        EfirSwitch(checked = checked, onCheckedChange = onCheckedChange, tint = tint)
    }
}

/* ------------------------------------------------------------------ подсказки */

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
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        glow = EfirAmber,
        glowAlpha = 0.10f,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .halo(EfirAmber, alpha = 0.22f, spread = 1.5f)
                    .clip(CircleShape)
                    .background(EfirAmber.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PriorityHigh,
                    contentDescription = null,
                    tint = EfirAmber,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.End)
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDismiss != null) {
                GlowChipButton(
                    label = dismissLabel,
                    onClick = onDismiss,
                    tint = EfirMuted,
                )
                Spacer(Modifier.width(8.dp))
            }
            GlowChipButton(
                label = actionLabel,
                onClick = onAction,
                tint = EfirAmber,
                filled = true,
            )
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
        EmptyAir(modifier)
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/**
 * Пустой эфир. Раньше здесь был текст в четыре строки; теперь молчание
 * показано тем же способом, что и работа, — кругом, только погасшим.
 */
@Composable
private fun EmptyAir(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "empty")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "emptyWave",
    )
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .drawBehind {
                        repeat(3) { i ->
                            val p = ((wave + i / 3f) % 1f)
                            drawCircle(
                                color = EfirGreen.copy(alpha = 0.20f * (1f - p)),
                                radius = size.minDimension / 2f * (0.35f + p * 0.65f),
                                center = center,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = EfirGreen.copy(alpha = 0.55f),
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "В эфире пусто",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Включите приём и подождите.\nВсё, что передают рядом, появится здесь.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Одна принятая передача.
 *
 * Это не переписка, а поток принимаемых сообщений, поэтому пузырей и
 * выравнивания по краям здесь нет: своё, чужое и личное различаются
 * цветом кружка с буквой и ярлыком, а не положением на экране.
 */
@Composable
private fun MessageRow(
    message: EfirMessage,
    channelTitle: (Int) -> String,
    linkResolver: (String) -> String?,
    profileResolver: (String) -> String?,
    canWrite: Boolean,
    onWriteTo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLocal = message.origin == EfirMessage.Origin.LOCAL
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val stamp = remember(message.receivedAtMillis) {
        SimpleDateFormat("HH:mm:ss", Locale("ru")).format(Date(message.receivedAtMillis))
    }
    val day = remember(message.receivedAtMillis) {
        SimpleDateFormat("dd.MM", Locale("ru")).format(Date(message.receivedAtMillis))
    }
    val accent = when {
        message.isDirect -> EfirAmber
        isLocal -> EfirSky
        else -> EfirGreen
    }
    val title = when {
        isLocal && message.peerHandle != null -> "Я → " + message.peerHandle.uppercase(Locale("ru"))
        isLocal -> "Я"
        else -> message.nick.uppercase(Locale("ru"))
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glow = if (message.isDirect) EfirAmber else null,
        glowAlpha = 0.12f,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HandleAvatar(
                    handle = if (isLocal) "Я" else message.nick,
                    color = accent,
                    glow = message.isDirect,
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "к${message.channel} · ${channelTitle(message.channel)}",
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stamp,
                        fontFamily = EfirMono,
                        fontSize = 12.sp,
                        color = scheme.onSurface.copy(alpha = 0.75f),
                    )
                    Text(
                        text = day,
                        fontFamily = EfirMono,
                        fontSize = 10.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            if (message.isDirect) {
                Spacer(Modifier.height(10.dp))
                EfirTag(
                    text = "личное сообщение",
                    color = EfirAmber,
                    icon = Icons.Filled.Lock,
                    solid = true,
                )
            }

            if (message.text.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message.text,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = scheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val actions = mutableListOf<@Composable () -> Unit>()

            message.linkCode?.let { code ->
                val url = linkResolver(code)
                actions += {
                    GlowChipButton(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        // Без адреса сети код всё равно показываем: его можно
                        // набрать руками, когда адрес будет настроен.
                        label = if (url != null) "открыть /p/$code" else "вложение /p/$code",
                        enabled = url != null,
                        tint = EfirSky,
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
            }

            if (!isLocal) {
                // Код ленты приходит только из эфира: в сети нет ни каталога,
                // ни поиска, так что это единственный путь к чужой ленте.
                message.profileCode?.let { profile ->
                    profileResolver(profile)?.let { profileUrl ->
                        actions += {
                            GlowChipButton(
                                icon = Icons.Filled.Badge,
                                label = "лента",
                                tint = EfirGreen,
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                },
                            )
                        }
                    }
                }

                // Написать можно только знакомому: ключ приезжает с его
                // передачей, без ключа шифровать нечем.
                if (canWrite) {
                    actions += {
                        GlowChipButton(
                            icon = Icons.Filled.Lock,
                            label = "написать",
                            tint = EfirAmber,
                            filled = true,
                            onClick = { onWriteTo(message.nick) },
                        )
                    }
                }
            }

            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { it() }
                }
            }

            message.sourceDeviceName?.takeIf { it.isNotBlank() && !isLocal }?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    fontFamily = EfirMono,
                    fontSize = 10.sp,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
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

    val fill by animateFloatAsState(
        targetValue = (used.toFloat() / budget.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(220),
        label = "budget",
    )
    val fillColor = when {
        overBudget -> MaterialTheme.colorScheme.error
        fill > 0.8f -> EfirAmber
        else -> EfirGreen
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.03f)),
                ),
            )
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            GlowIconButton(
                icon = Icons.AutoMirrored.Filled.Notes,
                description = "Дописать текст",
                tint = if (hasAttachment) EfirAmber else EfirMuted,
                highlighted = hasAttachment,
                onClick = onAttach,
                size = 42.dp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .glass(RoundedCornerShape(22.dp), fill = 0.05f, stroke = 0.10f),
            ) {
                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canUseRadio,
                    isError = overBudget,
                    singleLine = false,
                    maxLines = 4,
                    placeholder = {
                        Text(
                            "Сообщение всем рядом",
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        cursorColor = EfirGreen,
                    ),
                )
            }
            Spacer(Modifier.width(10.dp))
            if (state.isUploading) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = EfirAmber,
                    )
                }
            } else {
                GlowActionButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    description = "Передать",
                    enabled = canSend,
                    onClick = onSend,
                )
            }
            if (state.isBroadcasting) {
                Spacer(Modifier.width(8.dp))
                GlowIconButton(
                    icon = Icons.Filled.StopCircle,
                    description = "Снять с эфира",
                    tint = MaterialTheme.colorScheme.error,
                    highlighted = true,
                    onClick = onStop,
                    size = 42.dp,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Полоса бюджета: 140 байт — жёсткий потолок радиообъявления, и
        // видеть, сколько осталось, важнее, чем читать цифры.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(EfirChipShape)
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .clip(EfirChipShape)
                        .background(fillColor),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$used/$budget",
                fontFamily = EfirMono,
                fontSize = 11.sp,
                color = if (overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val hint = buildString {
            if (hasAttachment) append("с текстом в сети")
            if (state.isUploading) {
                if (isNotEmpty()) append("  ·  ")
                append("отправляю в сеть…")
            } else if (state.isBroadcasting) {
                if (isNotEmpty()) append("  ·  ")
                append("в эфире, отправка заменит")
            }
        }
        if (hint.isNotEmpty()) {
            Text(
                text = hint,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
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
        title = { Text("Настройки", fontFamily = EfirMono) },
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
                        fontFamily = EfirSans,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(16.dp))
                    SettingsSection("Погасший экран")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EfirSwitch(checked = state.keepAwake, onCheckedChange = onChangeKeepAwake)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Слушать эфир при выключенном экране",
                            fontFamily = EfirSans,
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
                        fontFamily = EfirSans,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.batteryRestricted) {
                        Text(
                            text = "Система всё равно ограничивает это приложение ради батареи. " +
                                "Откройте «Батарея» в свойствах приложения и поставьте " +
                                "«Без ограничений», на Xiaomi — ещё и «Автозапуск».",
                            fontFamily = EfirSans,
                            fontSize = 10.sp,
                            color = EfirAmber,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    SettingsSection("Лента")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EfirSwitch(checked = state.publishToFeed, onCheckedChange = onChangePublish, tint = EfirSky)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Публиковать передачи в мою ленту",
                            fontFamily = EfirSans,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = "Позывной: ${state.nick}\nКод ленты: ${state.profileCode}",
                        fontFamily = EfirSans,
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
                    fontFamily = EfirSans,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Адрес", fontFamily = EfirSans, fontSize = 11.sp) },
                    placeholder = {
                        Text("https://radioinformator.ru", fontFamily = EfirSans, fontSize = 12.sp)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = EfirMono),
                )
                if (state.siteRequiresToken || token.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        singleLine = true,
                        label = { Text("Токен", fontFamily = EfirSans, fontSize = 11.sp) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = EfirMono),
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
                    fontFamily = EfirSans,
                    fontSize = 11.sp,
                    color = when (state.siteStatus) {
                        SiteStatus.ONLINE -> MaterialTheme.colorScheme.primary
                        SiteStatus.OFFLINE -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                TextButton(onClick = onRecheck) {
                    Text("Проверить связь", fontFamily = EfirSans, fontSize = 11.sp)
                }

                onSignOut?.let { signOut ->
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = signOut) {
                        Text(
                            "Забыть позывной на этом телефоне",
                            fontFamily = EfirSans,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(url, token) }) {
                Text("Сохранить", fontFamily = EfirMono)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", fontFamily = EfirMono) }
        },
    )
}

@Composable
private fun SettingsSection(title: String) {
    EfirSectionLabel(
        text = title,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
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

    EfirSectionLabel(text = "Слушаю", modifier = Modifier.padding(top = 8.dp))

    Row(
        modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlowChipButton(
            label = "все каналы",
            tint = EfirGreen,
            filled = state.listenChannels.size >= EfirPrefs.CHANNEL_MAX,
            onClick = onAll,
        )
        GlowChipButton(
            label = "только общий",
            tint = EfirSky,
            filled = state.listenChannels.size == 1 &&
                state.listenChannels.contains(EfirPrefs.CHANNEL_DEFAULT),
            onClick = { onOnly(EfirPrefs.CHANNEL_DEFAULT) },
        )
    }

    (EfirPrefs.CHANNEL_MIN..EfirPrefs.CHANNEL_MAX).forEach { channel ->
        val isAlarm = channel == EfirPrefs.ALARM_CHANNEL
        val checked = channel in state.listenChannels
        val accent = if (isAlarm) scheme.error else EfirGreen

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(EfirTileShape)
                .clickable { onToggle(channel) }
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Своя галочка вместо материаловского квадрата: круг со
            // свечением попадает в общий язык остальных экранов.
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .halo(accent, alpha = if (checked) 0.30f else 0f, spread = 1.7f)
                    .clip(CircleShape)
                    .background(if (checked) accent else Color.White.copy(alpha = 0.06f))
                    .border(
                        1.dp,
                        if (checked) Color.Transparent else Color.White.copy(alpha = 0.14f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = EfirInk,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "к$channel",
                fontFamily = EfirMono,
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp),
            )
            Text(
                text = state.channelTitle(channel),
                fontSize = 14.sp,
                fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
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
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = if (state.alarmMuted) scheme.error else scheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun ChannelPicker(
    label: String,
    channel: Int,
    title: String,
    onChange: (Int) -> Unit,
) {
    // Подпись отдельной строкой: в одну строку со стрелками она ломалась
    // пополам и разъезжалась с регулятором.
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(EfirTileShape, fill = 0.05f, stroke = 0.09f)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(
                icon = Icons.Filled.Remove,
                description = "Канал ниже",
                enabled = channel > EfirPrefs.CHANNEL_MIN,
                onClick = { onChange(channel - 1) },
            )
            // Тема важнее номера: человек ищет «Еду», а не «канал 2».
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "канал $channel",
                    fontFamily = EfirMono,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StepButton(
                icon = Icons.Filled.Add,
                description = "Канал выше",
                enabled = channel < EfirPrefs.CHANNEL_MAX,
                onClick = { onChange(channel + 1) },
            )
        }
    }
}

/** Круглая кнопка «минус/плюс» для регуляторов настроек. */
@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    GlowIconButton(
        icon = icon,
        description = description,
        tint = EfirSky,
        enabled = enabled,
        onClick = onClick,
        size = 38.dp,
    )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        StepButton(
            icon = Icons.Filled.Remove,
            description = "Реже",
            enabled = seconds > min,
            onClick = { onChange(seconds - 5) },
        )
        Text(
            text = "$seconds с",
            fontFamily = EfirMono,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        StepButton(
            icon = Icons.Filled.Add,
            description = "Чаще",
            enabled = seconds < max,
            onClick = { onChange(seconds + 5) },
        )
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
        title = { Text("Написать лично", fontFamily = EfirSans, fontSize = 15.sp) },
        text = {
            Column(Modifier.verticalScroll(scroll)) {
                if (state.contacts.isEmpty()) {
                    Text(
                        text = "Пока некому. Список собеседников пополняется только эфиром: " +
                            "поймайте чью-нибудь передачу — вместе с ней придёт его ключ, " +
                            "и человек появится здесь.",
                        fontFamily = EfirSans,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "Кому",
                        fontFamily = EfirSans,
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
                                fontFamily = EfirSans,
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
                            Text("Сообщение", fontFamily = EfirSans, fontSize = 11.sp)
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = EfirSans,
                        ),
                        supportingText = {
                            Text(
                                "$used/$budget байт",
                                fontFamily = EfirSans,
                                fontSize = 10.sp,
                            )
                        },
                    )

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Текст зашифрован ключом получателя: в эфире виден только факт, " +
                            "что кто-то кому-то написал. Прямой секретности нет — если ваше " +
                            "кодовое слово узнают, старые записки тоже прочтут.",
                        fontFamily = EfirSans,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    state.directRecipient?.let { current ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Сейчас в эфире письмо для $current. Новое его заменит.",
                            fontFamily = EfirSans,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(recipient, text) }, enabled = canSend) {
                Text("Передать", fontFamily = EfirMono)
            }
        },
        dismissButton = {
            if (state.directRecipient != null) {
                TextButton(onClick = onStop) {
                    Text("Снять с эфира", fontFamily = EfirMono)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Закрыть", fontFamily = EfirMono)
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
                fontFamily = EfirSans,
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
                    Text("Загружаю…", fontFamily = EfirSans, fontSize = 12.sp)
                }

                FeedStatus.FAILED -> Text(
                    text = "Лента не загрузилась: ${state.feedError ?: "нет связи"}. " +
                        "Она живёт в сети, поэтому без интернета её не показать.",
                    fontFamily = EfirSans,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> if (state.feed.isEmpty()) {
                    Text(
                        text = "Пока пусто. Сюда попадают ваши передачи, если включена " +
                            "публикация в ленту.",
                        fontFamily = EfirSans,
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
                                            fontFamily = EfirSans,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "к${entry.channel}",
                                            fontFamily = EfirSans,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (entry.hidden) {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "НА ПРОВЕРКЕ",
                                                fontFamily = EfirSans,
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                            )
                                        }
                                    }
                                    Text(
                                        text = entry.broadcast.ifBlank { entry.excerpt },
                                        fontFamily = EfirSans,
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
                Text("Закрыть", fontFamily = EfirMono)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReload) {
                    Text("Обновить", fontFamily = EfirSans, fontSize = 12.sp)
                }
                TextButton(onClick = onOpenInBrowser) {
                    Text("В браузере", fontFamily = EfirSans, fontSize = 12.sp)
                }
            }
        },
    )

    // Удаление из ленты необратимо — записи в сети больше не будет.
    confirmCode?.let { code ->
        AlertDialog(
            onDismissRequest = { confirmCode = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Удалить запись?", fontFamily = EfirSans, fontSize = 15.sp) },
            text = {
                Text(
                    text = "Запись /p/$code исчезнет из сети навсегда. У тех, кто уже " +
                        "принял её из эфира, она останется в журнале — это их копия.",
                    fontFamily = EfirSans,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(code)
                    confirmCode = null
                }) {
                    Text("Удалить", fontFamily = EfirSans, color = EfirRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCode = null }) {
                    Text("Отмена", fontFamily = EfirMono)
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
        title = { Text("Дописать к передаче", fontFamily = EfirSans, fontSize = 15.sp) },
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
                    fontFamily = EfirSans,
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
                        Text("Полный текст", fontFamily = EfirSans, fontSize = 11.sp)
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = EfirMono),
                    supportingText = {
                        Text(
                            "$textLength/${state.limits.maxTextChars} символов",
                            fontFamily = EfirSans,
                            fontSize = 10.sp,
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !overText) {
                Text("Готово", fontFamily = EfirMono)
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text("Убрать", fontFamily = EfirMono)
            }
        },
    )
}
