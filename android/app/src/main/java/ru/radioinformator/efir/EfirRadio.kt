package ru.radioinformator.efir

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Looper
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import ru.radioinformator.efir.crypto.EfirCrypto
import ru.radioinformator.efir.model.AccountStatus
import ru.radioinformator.efir.model.Attachment
import ru.radioinformator.efir.model.Contact
import ru.radioinformator.efir.model.ContactCard
import ru.radioinformator.efir.model.ContactsStatus
import ru.radioinformator.efir.model.EfirMessage
import ru.radioinformator.efir.model.EfirUiState
import ru.radioinformator.efir.model.FeedStatus
import ru.radioinformator.efir.model.P2pState
import ru.radioinformator.efir.model.ScheduleRule
import ru.radioinformator.efir.model.ScheduleState
import ru.radioinformator.efir.model.SiteStatus
import ru.radioinformator.efir.net.EfirApi
import ru.radioinformator.efir.net.EfirPrefs
import ru.radioinformator.efir.net.Identity
import ru.radioinformator.efir.p2p.EfirBroadcastReceiver
import ru.radioinformator.efir.p2p.EfirPermissions
import ru.radioinformator.efir.p2p.EfirRadioService
import ru.radioinformator.efir.p2p.TxtRecordCodec
import kotlin.coroutines.resume

/**
 * ЭФИР: пейджер без соединения, поверх обнаружения служб Wi-Fi P2P.
 *
 * Весь смысл в том, что [WifiP2pManager.connect] не вызывается никогда.
 * Устройства не образуют пару; по воздуху летит только объявление службы
 * DNS-SD, а текст сообщения едет внутри его TXT-записи. Ответ на запрос
 * обнаружения читает любой, кто рядом, — без группы, без согласования
 * владельца группы и без IP-адреса. Это и есть модель «крикнуть в комнату».
 *
 * Опционально к передаче прикрепляется страница на сайте: длинный текст и
 * картинки уходят по обычному HTTP, а в эфир добавляется только пятисимвольный
 * код страницы.
 *
 * О потоках: обратные вызовы [WifiP2pManager] приходят на тот Looper, который
 * передан в [WifiP2pManager.initialize] — здесь главный. [scope] тоже
 * работает на главном, поэтому изменяемые структуры ниже трогает один поток и
 * синхронизация им не нужна.
 *
 * Живёт один на процесс, а не на экран. Раньше движок был ViewModel и умирал
 * вместе с активностью: экран гас — приём прекращался, и человек узнавал об
 * этом только по пустой ленте. Теперь состояние переживает и поворот, и уход
 * в фон, а не даёт себя усыпить [EfirRadioService].
 */
class EfirRadio private constructor(private val appContext: Context) {

    /**
     * Своя область вместо viewModelScope: работа продолжается, когда активности
     * уже нет. Отменяется только вместе с процессом.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val manager: WifiP2pManager? =
        appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private var channel: WifiP2pManager.Channel? = null
    private var receiver: EfirBroadcastReceiver? = null

    private val prefs = EfirPrefs(appContext)
    private val api = EfirApi()

    private var discoveryJob: Job? = null
    private var advertiseKeepAliveJob: Job? = null
    private var scheduleJob: Job? = null

    /**
     * Два независимых объявления: открытая передача и личное сообщение.
     * Хранятся отдельно, потому что `clearLocalServices` снимает сразу все
     * службы, и после смены одной приходится переобъявлять обе.
     */
    private var publicRecord: Map<String, String>? = null
    private var directRecord: Map<String, String>? = null

    /** Адрес этого устройства в Wi-Fi Direct, если система его показывает. */
    private var ownDeviceAddress: String? = null

    /** Идентификаторы, порождённые этим устройством — чтобы не ловить своё эхо. */
    private val localIds = HashSet<String>()

    /** Ограниченный по объёму список уже показанных идентификаторов. */
    private val seenIds = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>): Boolean =
            size > DEDUPE_CAPACITY
    }

    private val seenDevices = HashSet<String>()

    private val _uiState = MutableStateFlow(EfirUiState())
    val uiState: StateFlow<EfirUiState> = _uiState.asStateFlow()

    // ---------------------------------------------------------------- слушатели

    /**
     * Срабатывает на каждый найденный экземпляр службы. Прямо мы им не
     * пользуемся — полезное лежит в TXT-записи, — но он показывает, кто в эфире,
     * и подтверждает, что радио вообще что-то приносит.
     */
    private val serviceResponseListener =
        WifiP2pManager.DnsSdServiceResponseListener { instanceName, registrationType, srcDevice ->
            Log.d(TAG, "Найдена служба: $instanceName ($registrationType) от ${srcDevice.deviceName}")
            // Имя теперь с хвостом от сообщения (см. instanceNameFor), поэтому
            // сверяем начало, а не всё имя целиком.
            if (!instanceName.startsWith(SERVICE_INSTANCE, ignoreCase = true)) {
                return@DnsSdServiceResponseListener
            }
            val key = srcDevice.deviceAddress ?: srcDevice.deviceName
                ?: return@DnsSdServiceResponseListener
            if (seenDevices.add(key)) {
                _uiState.update { it.copy(peersSeen = seenDevices.size) }
            }
        }

    /**
     * Собственно приём. Карта TXT приходит уже разобранной системой,
     * дальше начинается наш формат.
     */
    private val txtRecordListener =
        WifiP2pManager.DnsSdTxtRecordListener { fullDomainName, record, srcDevice ->
            // Запрос обнаружения теперь без типа, поэтому сюда попадают TXT
            // всех DNS-SD служб по соседству — принтеров, телевизоров и прочего.
            // Отсеиваем чужое по домену, чтобы и лог не засорять.
            if (!fullDomainName.contains(SERVICE_TYPE, ignoreCase = true)) {
                return@DnsSdTxtRecordListener
            }
            Log.d(TAG, "TXT от $fullDomainName: $record")
            handleTxtRecord(record, srcDevice)
        }

    private val channelListener = WifiP2pManager.ChannelListener {
        // Система разорвала канал (переключили Wi-Fi или перезапустилась служба
        // P2P). Пересоздаём и возвращаемся к тому, чем занимались.
        Log.w(TAG, "Канал P2P разорван — пересоздаём")
        channel = null
        scope.launch {
            delay(1_000L)
            initialiseChannel()
            if (_uiState.value.isScanning) startScanning()
            if (_uiState.value.isBroadcasting) {
                _uiState.value.advertisedText?.let { transmit(it, reuseAttachment = false) }
            }
        }
    }

    // ---------------------------------------------------------------- жизненный цикл

    init {
        val identity = prefs.identity
        _uiState.update {
            it.copy(
                nick = identity.handle,
                profileCode = identity.profileCode,
                accountStatus = if (identity.isRegistered) AccountStatus.READY else AccountStatus.NONE,
                contacts = prefs.contacts(),
                journal = prefs.journal().reversed(),
                channelTitles = prefs.channelTitles,
                schedule = ScheduleState(
                    rules = prefs.scheduleRules(),
                    enabled = prefs.scheduleEnabled,
                ),
                siteUrl = prefs.siteUrl,
                publishToFeed = prefs.publishToFeed,
                transmitChannel = prefs.transmitChannel,
                listenChannels = prefs.listenChannels,
                alarmMuted = prefs.alarmMuted,
                listenIntervalSeconds = prefs.listenIntervalSeconds,
                transmitIntervalSeconds = prefs.transmitIntervalSeconds,
            )
        }

        if (manager == null) {
            Log.e(TAG, "WifiP2pManager недоступен — устройство не умеет Wi-Fi Direct")
            _uiState.update {
                it.copy(
                    p2pState = P2pState.UNSUPPORTED,
                    lastError = "Это устройство не поддерживает Wi-Fi Direct.",
                )
            }
        } else {
            initialiseChannel()
        }
        refreshEnvironment()
        refreshSiteInfo()
    }

    private fun initialiseChannel() {
        val mgr = manager ?: return
        channel = mgr.initialize(appContext, Looper.getMainLooper(), channelListener)
        if (channel == null) {
            Log.e(TAG, "initialize вернул пустой канал")
            _uiState.update {
                it.copy(
                    p2pState = P2pState.UNSUPPORTED,
                    lastError = "Не удалось открыть канал Wi-Fi Direct.",
                )
            }
            return
        }
        // Слушатели DNS-SD ставим заранее: они должны быть на месте до
        // discoverServices, иначе первый круг результатов пропадает впустую.
        mgr.setDnsSdResponseListeners(channel, serviceResponseListener, txtRecordListener)
    }

    /** Вызывается из ON_START активности. */
    fun onStart() {
        registerReceiver()
        refreshEnvironment()
        // Приём восстанавливается сам: его включают и забывают, и молча
        // не вернувшийся после перезапуска приём — это тихо неработающая вещь,
        // о которой хозяин узнает по пустой ленте.
        if (_uiState.value.isScanning || prefs.receiveOn) startScanning()
        if (prefs.scheduleEnabled) startScheduleWatch()
    }

    /**
     * Вызывается из ON_STOP активности — и намеренно почти ничего не делает.
     *
     * Раньше здесь гасились и обнаружение, и вещание: считалось, что фоновому
     * приложению система всё равно не даст работать. Но у радиоинформатора
     * ровно наоборот: телефон лежит в кармане с погасшим экраном, и именно
     * тогда объявление соседа и должно дойти. Работу теперь держит
     * [EfirRadioService], а приёмник широковещаний нужен ему не меньше, чем
     * интерфейсу, — снимаем его, только когда всё выключено.
     */
    fun onStop() {
        if (!isWorking) unregisterReceiver()
    }

    /**
     * Есть ли что удерживать.
     *
     * Включённый планировщик считается работой, даже когда в эфире пока пусто:
     * правило может ждать одиннадцати утра, и если к тому времени процесс
     * выгрузят, оно просто не сработает — а человек будет уверен, что объявление
     * ушло.
     */
    val isWorking: Boolean
        get() = _uiState.value.isScanning ||
            _uiState.value.isBroadcasting ||
            _uiState.value.schedule.enabled

    /**
     * Приводит службу в соответствие состоянию.
     *
     * Служба здесь не ради галочки: без неё система через несколько минут
     * после погасшего экрана усыпляет процесс, и эфир для нас замолкает.
     * Уведомление в шторке — плата за то, что телефон продолжает слушать.
     */
    private fun syncService() {
        if (isWorking) {
            EfirRadioService.start(appContext)
        } else {
            EfirRadioService.stop(appContext)
        }
        EfirRadioService.refresh(appContext, _uiState.value)
    }

    /** Полная остановка: снимает всё с эфира. Дальше движок можно завести снова. */
    fun shutdown() {
        discoveryJob?.cancel(); discoveryJob = null
        advertiseKeepAliveJob?.cancel(); advertiseKeepAliveJob = null
        scheduleJob?.cancel(); scheduleJob = null
        unregisterReceiver()
        // «Выключить» из шторки — осознанное действие, поэтому приём не должен
        // воскреснуть при следующем открытии приложения.
        prefs.receiveOn = false
        _uiState.update { it.copy(isScanning = false, isBroadcasting = false, advertisedText = null) }

        val mgr = manager
        val ch = channel
        if (mgr != null && ch != null) {
            runCatching { mgr.clearLocalServices(ch, loggingListener("clearLocalServices")) }
            runCatching { mgr.clearServiceRequests(ch, loggingListener("clearServiceRequests")) }
            runCatching { mgr.stopPeerDiscovery(ch, loggingListener("stopPeerDiscovery")) }
        }
    }

    private fun registerReceiver() {
        if (receiver != null) return
        val r = EfirBroadcastReceiver(
            onStateChanged = { enabled ->
                _uiState.update {
                    it.copy(p2pState = if (enabled) P2pState.ENABLED else P2pState.DISABLED)
                }
                if (!enabled) {
                    _uiState.update { it.copy(lastError = "Wi-Fi выключен — включите его, чтобы выйти в эфир.") }
                } else if (_uiState.value.isScanning) {
                    startScanning()
                }
            },
            onDiscoveryChanged = { started ->
                // Обнаружение само выключается через пару минут. Если приём
                // всё ещё нужен, цикл ниже запустит его снова.
                if (!started && _uiState.value.isScanning) {
                    Log.d(TAG, "Обнаружение остановлено при включённом приёме — перезапустим")
                }
            },
            onThisDeviceChanged = { device: WifiP2pDevice ->
                ownDeviceAddress = device.deviceAddress
            },
        )
        // Это защищённые системные широковещания; NOT_EXPORTED требуется на API 34.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                r,
                EfirBroadcastReceiver.intentFilter(),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(r, EfirBroadcastReceiver.intentFilter())
        }
        receiver = r
    }

    private fun unregisterReceiver() {
        receiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        receiver = null
    }

    // ------------------------------------------------------------------ настройки

    fun refreshEnvironment() {
        _uiState.update {
            it.copy(
                permissionsGranted = EfirPermissions.allGranted(appContext),
                locationServicesRequired = EfirPermissions.locationServicesRequired(),
                locationServicesEnabled = EfirPermissions.locationServicesEnabled(appContext),
                batteryRestricted = EfirPermissions.batteryOptimized(appContext),
                batteryNoticeHidden = prefs.batteryNoticeHidden,
                keepAwake = prefs.keepAwake,
                keepScreenOn = prefs.keepScreenOn,
            )
        }
    }

    /** Не давать экрану гаснуть, пока приложение открыто. */
    fun setKeepScreenOn(value: Boolean) {
        prefs.keepScreenOn = value
        _uiState.update { it.copy(keepScreenOn = value) }
    }

    /** Убрать совет про экономию батареи. Проверить его выполнение нам нечем. */
    fun hideBatteryNotice() {
        prefs.batteryNoticeHidden = true
        _uiState.update { it.copy(batteryNoticeHidden = true) }
    }

    /**
     * Держать ли процессор разбуженным ради приёма.
     *
     * Меняется на ходу: службу дёргаем, чтобы она сняла или взяла wake lock
     * прямо сейчас, а не после следующего включения приёма.
     */
    fun setKeepAwake(value: Boolean) {
        prefs.keepAwake = value
        _uiState.update { it.copy(keepAwake = value) }
        syncService()
    }

    /**
     * Регистрация или вход — для человека это одно действие: назвать позывной
     * и кодовое слово. Свободное имя занимается, занятое пускает при верном
     * слове; так лента переезжает на новый телефон.
     *
     * Кодовое слово дальше этого метода не идёт: из него сразу считается ключ,
     * а само слово нигде не сохраняется.
     */
    fun register(handle: String, secret: String) {
        val handleError = Identity.validateHandle(handle)
        if (handleError != null) {
            _uiState.update { it.copy(registerError = handleError) }
            return
        }
        val secretError = Identity.validateSecret(secret)
        if (secretError != null) {
            _uiState.update { it.copy(registerError = secretError) }
            return
        }

        val siteUrl = prefs.siteUrl
        if (siteUrl.isBlank()) {
            _uiState.update {
                it.copy(registerError = "Не задан адрес сайта — позывной регистрировать негде.")
            }
            return
        }

        _uiState.update { it.copy(accountStatus = AccountStatus.REGISTERING, registerError = null) }

        scope.launch {
            val authKey = Identity.authKey(handle, secret)
            api.register(siteUrl, prefs.apiToken, handle.trim(), authKey)
                .onSuccess { result ->
                    prefs.saveIdentity(
                        Identity(
                            handle = result.handle,
                            authKey = authKey,
                            profileCode = result.profileCode,
                        )
                    )
                    // Единственный момент, когда кодовое слово ещё известно:
                    // выводим из него ключ шифрования и запоминаем только ключ.
                    // На другом телефоне те же позывной и слово дадут тот же
                    // ключ, поэтому переписка продолжит читаться.
                    prefs.privateKey = Base64.encodeToString(
                        EfirCrypto.derivePrivateKey(result.handle, secret),
                        Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
                    )
                    _uiState.update {
                        it.copy(
                            accountStatus = AccountStatus.READY,
                            nick = result.handle,
                            profileCode = result.profileCode,
                            registerError = null,
                            lastNotice = if (result.created) {
                                "Позывной занят за вами. Запомните кодовое слово — восстановить его нельзя."
                            } else {
                                "С возвращением. Лента на месте: передач — ${result.postsCount}."
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            accountStatus = AccountStatus.NONE,
                            registerError = error.message ?: "Сайт не отвечает",
                        )
                    }
                }
        }
    }

    /**
     * Забыть позывной на этом телефоне. Лента на сайте остаётся, знакомые —
     * тоже: они привязаны к устройству, а не к учётной записи, и терять их
     * при смене позывного было бы обидно.
     */
    fun signOut() {
        prefs.forgetIdentity()
        _uiState.update {
            it.copy(
                accountStatus = AccountStatus.NONE,
                nick = "",
                profileCode = "",
                registerError = null,
            )
        }
    }

    // ------------------------------------------------------------------ планировщик

    fun addScheduleRule(rule: ScheduleRule) {
        val rules = prefs.scheduleRules() + rule
        prefs.saveScheduleRules(rules)
        publishSchedule()
        applyScheduleNow()
    }

    fun deleteScheduleRule(id: String) {
        prefs.saveScheduleRules(prefs.scheduleRules().filterNot { it.id == id })
        publishSchedule()
        applyScheduleNow()
    }

    fun toggleSchedule(enabled: Boolean) {
        prefs.scheduleEnabled = enabled
        publishSchedule()
        if (enabled) {
            applyScheduleNow()
            startScheduleWatch()
        } else {
            scheduleJob?.cancel()
            scheduleJob = null
        }
        // Служба нужна и просто ждущему планировщику: правило может сработать
        // ночью, когда до приложения никому нет дела.
        syncService()
    }

    private fun publishSchedule() {
        _uiState.update {
            it.copy(
                schedule = it.schedule.copy(
                    rules = prefs.scheduleRules(),
                    enabled = prefs.scheduleEnabled,
                )
            )
        }
    }

    /**
     * Следит за расписанием, пока оно включено.
     *
     * Проверять чаще раза в полминуты незачем: правила задаются с точностью до
     * минуты, а лишние пробуждения впустую тратят батарею у того, кто оставил
     * телефон вещать на весь день.
     */
    private fun startScheduleWatch() {
        scheduleJob?.cancel()
        scheduleJob = scope.launch {
            while (isActive) {
                applyScheduleNow()
                delay(SCHEDULE_TICK_MS)
            }
        }
    }

    /**
     * Ставит в эфир то правило, чьё время настало.
     *
     * Если подходящих несколько, берётся первое по списку — порядок правил и
     * есть их приоритет. Если не подходит ни одно, эфир замолкает: это честнее,
     * чем оставить висеть вчерашнее меню.
     */
    private fun applyScheduleNow() {
        if (!prefs.scheduleEnabled) return

        val now = System.currentTimeMillis()
        val rule = prefs.scheduleRules().firstOrNull { it.matches(now) }
        val activeId = _uiState.value.schedule.activeRuleId

        if (rule == null) {
            if (activeId != null) {
                Log.i(TAG, "Расписание: подходящих правил нет, снимаем с эфира")
                _uiState.update { it.copy(schedule = it.schedule.copy(activeRuleId = null)) }
                stopTransmitting()
            }
            return
        }

        // Уже вещаем это правило — трогать эфир незачем.
        if (rule.id == activeId && _uiState.value.isBroadcasting) return

        Log.i(TAG, "Расписание: в эфир идёт правило ${rule.id}")
        _uiState.update { it.copy(schedule = it.schedule.copy(activeRuleId = rule.id)) }
        setTransmitChannel(rule.channel)
        transmit(rule.text, reuseAttachment = false)
    }

    // ------------------------------------------------------------------ своя лента

    /** Подтягивает свою ленту из сети. Без интернета показать её неоткуда. */
    fun loadMyFeed() {
        val siteUrl = prefs.siteUrl
        val authKey = prefs.authKey
        if (siteUrl.isBlank() || authKey.isBlank()) {
            _uiState.update {
                it.copy(feedStatus = FeedStatus.FAILED, feedError = "Нет адреса сети или ключа.")
            }
            return
        }

        _uiState.update { it.copy(feedStatus = FeedStatus.LOADING, feedError = null) }
        scope.launch {
            api.myFeed(siteUrl, authKey)
                .onSuccess { entries ->
                    _uiState.update {
                        it.copy(
                            feed = entries,
                            feedTotal = entries.size,
                            feedStatus = FeedStatus.READY,
                            feedError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            feedStatus = FeedStatus.FAILED,
                            feedError = error.message ?: "сеть не отвечает",
                        )
                    }
                }
        }
    }

    /**
     * Удаляет свою запись из ленты. Из журнала и текущего списка эфира она
     * при этом не пропадает: там она принята, а не опубликована.
     */
    fun deleteMyPost(code: String) {
        val siteUrl = prefs.siteUrl
        val authKey = prefs.authKey
        if (siteUrl.isBlank() || authKey.isBlank()) return

        scope.launch {
            api.deletePost(siteUrl, authKey, code)
                .onSuccess {
                    _uiState.update { state ->
                        val left = state.feed.filterNot { it.code == code }
                        state.copy(feed = left, feedTotal = left.size)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(lastError = "Не удалось удалить: ${error.message ?: "нет связи"}")
                    }
                }
        }
    }

    /**
     * Визитка на странице своей ленты.
     *
     * Живёт она на сайте, а не в телефоне: показывается-то она там. Поэтому
     * перед правкой её каждый раз забираем — на другом устройстве под тем же
     * позывным она могла измениться.
     */
    fun loadContacts() {
        val siteUrl = prefs.siteUrl
        val authKey = prefs.authKey
        if (siteUrl.isBlank() || authKey.isBlank()) {
            _uiState.update {
                it.copy(
                    contactsStatus = ContactsStatus.FAILED,
                    contactsError = "Нет адреса сети или ключа.",
                )
            }
            return
        }

        _uiState.update { it.copy(contactsStatus = ContactsStatus.LOADING, contactsError = null) }
        scope.launch {
            api.loadContacts(siteUrl, authKey)
                .onSuccess { card ->
                    _uiState.update {
                        it.copy(
                            contactCard = card,
                            contactsStatus = ContactsStatus.READY,
                            contactsError = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            contactsStatus = ContactsStatus.FAILED,
                            contactsError = error.message ?: "сеть не отвечает",
                        )
                    }
                }
        }
    }

    /** Сохраняет визитку. Пустые поля стираются — это и есть способ убрать лишнее. */
    fun saveContacts(card: ContactCard) {
        val siteUrl = prefs.siteUrl
        val authKey = prefs.authKey
        if (siteUrl.isBlank() || authKey.isBlank()) {
            _uiState.update { it.copy(contactsError = "Нет адреса сети или ключа.") }
            return
        }

        _uiState.update { it.copy(contactsStatus = ContactsStatus.SAVING, contactsError = null) }
        scope.launch {
            api.saveContacts(siteUrl, authKey, card)
                .onSuccess { saved ->
                    _uiState.update {
                        it.copy(
                            contactCard = saved,
                            contactsStatus = ContactsStatus.READY,
                            contactsError = null,
                            lastNotice = if (saved.isEmpty) {
                                "Визитка убрана со страницы ленты"
                            } else {
                                "Визитка сохранена"
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            contactsStatus = ContactsStatus.FAILED,
                            contactsError = error.message ?: "не удалось сохранить",
                        )
                    }
                }
        }
    }

    /** Забыть всех знакомых. После этого написать лично будет некому. */
    fun forgetContacts() {
        prefs.forgetContacts()
        _uiState.update { it.copy(contacts = emptyList()) }
    }

    fun setPublishToFeed(value: Boolean) {
        prefs.publishToFeed = value
        _uiState.update { it.copy(publishToFeed = value) }
    }

    fun setTransmitChannel(channel: Int) {
        prefs.transmitChannel = channel
        _uiState.update { it.copy(transmitChannel = prefs.transmitChannel) }
    }

    /**
     * Включает или выключает канал в наборе прослушивания. Уже показанное с
     * других каналов не убираем — это была честно принятая передача, фильтр
     * начинает действовать со следующей.
     */
    fun toggleListenChannel(channel: Int) {
        prefs.toggleListenChannel(channel)
        publishListenState()
    }

    fun listenToAllChannels() {
        prefs.listenToAllChannels()
        publishListenState()
    }

    fun listenToOnly(channel: Int) {
        prefs.listenToOnly(channel)
        publishListenState()
    }

    private fun publishListenState() {
        _uiState.update {
            it.copy(listenChannels = prefs.listenChannels, alarmMuted = prefs.alarmMuted)
        }
    }

    fun setListenInterval(seconds: Int) {
        prefs.listenIntervalSeconds = seconds
        _uiState.update { it.copy(listenIntervalSeconds = prefs.listenIntervalSeconds) }
    }

    fun setTransmitInterval(seconds: Int) {
        prefs.transmitIntervalSeconds = seconds
        _uiState.update { it.copy(transmitIntervalSeconds = prefs.transmitIntervalSeconds) }
    }

    fun setSiteUrl(value: String) {
        prefs.siteUrl = value
        _uiState.update { it.copy(siteUrl = prefs.siteUrl) }
        refreshSiteInfo()
    }

    fun setApiToken(value: String) {
        prefs.apiToken = value
    }

    fun apiToken(): String = prefs.apiToken

    /** Спрашивает у сайта лимиты. Они задаются в админке, а не зашиты в APK. */
    fun refreshSiteInfo() {
        val url = prefs.siteUrl
        if (url.isBlank()) {
            _uiState.update { it.copy(siteStatus = SiteStatus.NOT_CONFIGURED) }
            return
        }
        _uiState.update { it.copy(siteStatus = SiteStatus.CHECKING) }
        scope.launch {
            api.fetchLimits(url)
                .onSuccess { info ->
                    // Темы каналов кладём в кэш: без сети подписи должны
                    // остаться на месте, а именно без сети этим и пользуются.
                    if (info.channelTitles.size == EfirPrefs.CHANNEL_MAX) {
                        prefs.channelTitles = info.channelTitles
                    }
                    _uiState.update {
                        it.copy(
                            siteStatus = SiteStatus.ONLINE,
                            siteName = info.name,
                            limits = info.limits,
                            siteRequiresToken = info.requiresToken,
                            channelTitles = prefs.channelTitles,
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state -> state.copy(siteStatus = SiteStatus.OFFLINE) }
                }
        }
    }

    // ------------------------------------------------------------------ вложение

    fun setAttachmentText(value: String) {
        _uiState.update { it.copy(attachment = it.attachment.copy(text = value)) }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(attachment = Attachment()) }
    }

    // ------------------------------------------------------------------ передача

    /**
     * Основное действие: при необходимости выкладывает вложение на сайт, а затем
     * заменяет объявляемую службу новой, с текстом [rawText] и кодом страницы.
     *
     * Очереди отправки и гарантии доставки тут нет: сообщение висит в воздухе,
     * пока его не заменят или не снимут, и его слышит тот, кто в этот момент
     * принимает. Для пейджера без соединения это честная модель.
     */
    fun transmit(rawText: String, reuseAttachment: Boolean = true) {
        val limits = _uiState.value.limits
        val text = TxtRecordCodec.clipToBytes(rawText.trim(), limits.broadcastMaxBytes)
        val attachment = if (reuseAttachment) _uiState.value.attachment else Attachment()

        if (text.isEmpty() && attachment.isEmpty) return

        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) {
            _uiState.update { it.copy(lastError = "Wi-Fi Direct недоступен.") }
            return
        }
        if (!_uiState.value.permissionsGranted) {
            _uiState.update { it.copy(lastError = "Нужно разрешение на устройства поблизости.") }
            return
        }

        scope.launch {
            val identity = prefs.identity
            val siteUrl = prefs.siteUrl
            val hasAttachment = !attachment.isEmpty

            // На сайт идём в двух случаях: есть вложение (тогда без него в эфир
            // нельзя) или включена публикация в личную ленту.
            val wantsUpload = hasAttachment ||
                (prefs.publishToFeed && identity.isRegistered && siteUrl.isNotBlank())

            var linkCode: String? = null

            if (wantsUpload) {
                if (siteUrl.isBlank()) {
                    _uiState.update {
                        it.copy(lastError = "Не задан адрес сайта — вложение отправлять некуда.")
                    }
                    return@launch
                }

                _uiState.update { it.copy(isUploading = true) }
                val result = api.upload(
                    baseUrl = siteUrl,
                    token = prefs.apiToken,
                    authKey = identity.authKey,
                    broadcast = text,
                    text = attachment.text,
                    nick = _uiState.value.nick,
                    channel = prefs.transmitChannel,
                )
                _uiState.update { it.copy(isUploading = false) }

                result.onSuccess { uploaded ->
                    // Ссылку в эфир кладём только ради вложения: для обычного
                    // сообщения страница дублировала бы то же самое.
                    if (hasAttachment) linkCode = uploaded.code
                    if (uploaded.moderation) {
                        _uiState.update {
                            it.copy(lastNotice = "Страница отправлена и ждёт одобрения на сайте.")
                        }
                    }
                }.onFailure { error ->
                    val reason = error.message ?: "нет связи с сайтом"
                    if (hasAttachment) {
                        // В эфир без ссылки не пойдём: получатели увидели бы
                        // сообщение, обещающее вложение, которого нет.
                        _uiState.update { it.copy(lastError = "Вложение не ушло: $reason") }
                        return@launch
                    }
                    // А вот обычное сообщение радио отправит и без сайта —
                    // эфир здесь первичен, лента вторична.
                    _uiState.update {
                        it.copy(lastNotice = "В ленту не попало ($reason), но в эфир ушло.")
                    }
                }
            }

            val id = TxtRecordCodec.newId()
            val nowSeconds = System.currentTimeMillis() / 1000L
            val record = TxtRecordCodec.encode(
                id = id,
                text = text,
                nick = _uiState.value.nick,
                timestampSeconds = nowSeconds,
                linkCode = linkCode,
                profileCode = identity.profileCode.takeIf { it.isNotBlank() },
                channel = prefs.transmitChannel,
                // Ключ едет в каждой открытой передаче: услышать человека и
                // получить возможность написать ему лично — одно событие.
                publicKey = myPublicKey(),
                maxMessageBytes = limits.broadcastMaxBytes,
            )

            publicRecord = record
            if (!republishServices()) {
                _uiState.update { it.copy(lastError = "Не удалось выйти в эфир.") }
                return@launch
            }

            localIds.add(id)
            seenIds[id] = true
            appendMessage(
                EfirMessage(
                    id = id,
                    text = text,
                    nick = _uiState.value.nick,
                    linkCode = linkCode,
                    profileCode = identity.profileCode.takeIf { it.isNotBlank() },
                    channel = prefs.transmitChannel,
                    sentAtEpochSeconds = nowSeconds,
                    receivedAtMillis = System.currentTimeMillis(),
                    origin = EfirMessage.Origin.LOCAL,
                )
            )
            _uiState.update {
                it.copy(
                    isBroadcasting = true,
                    advertisedText = text,
                    attachment = Attachment(),
                )
            }
            startAdvertiseKeepAlive()
            syncService()
            Log.i(TAG, "В эфире id=$id (${TxtRecordCodec.utf8Length(text)} байт), ссылка=$linkCode")
        }
    }

    /** Снимает наши сообщения с эфира. После этого от нас ничего не объявляется. */
    fun stopTransmitting() {
        val mgr = manager ?: return
        val ch = channel ?: return
        advertiseKeepAliveJob?.cancel()
        advertiseKeepAliveJob = null
        publicRecord = null
        directRecord = null
        scope.launch {
            p2pCall("clearLocalServices") { mgr.clearLocalServices(ch, it) }
            _uiState.update {
                it.copy(isBroadcasting = false, advertisedText = null, directRecipient = null)
            }
            syncService()
        }
    }

    // ------------------------------------------------------------------ личные сообщения

    /** Наш публичный ключ; null, если приватный ещё не выведен. */
    private fun myPublicKey(): ByteArray? {
        val raw = prefs.privateKey.takeIf { it.isNotBlank() } ?: return null
        val privateKey = decodeKey(raw) ?: return null
        return runCatching { EfirCrypto.publicKeyOf(privateKey) }.getOrNull()
    }

    /**
     * Личное сообщение конкретному знакомому.
     *
     * В эфир уходит запечатанный блок и короткий адрес получателя: посторонний
     * видит только сам факт, что кто-то кому-то написал. Ни сайт, ни интернет
     * здесь не участвуют — сообщение живёт исключительно в воздухе.
     */
    fun sendDirect(recipientHandle: String, rawText: String) {
        val text = TxtRecordCodec.clipToBytes(rawText.trim(), TxtRecordCodec.MAX_DIRECT_BYTES)
        if (text.isEmpty()) return

        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) {
            _uiState.update { it.copy(lastError = "Wi-Fi Direct недоступен.") }
            return
        }
        if (!_uiState.value.permissionsGranted) {
            _uiState.update { it.copy(lastError = "Нужно разрешение на устройства поблизости.") }
            return
        }

        val privateKeyRaw = prefs.privateKey.takeIf { it.isNotBlank() }
        val privateKey = privateKeyRaw?.let { decodeKey(it) }
        if (privateKey == null) {
            // Ключ выводится из кодового слова в момент регистрации. Если его
            // нет, значит учётная запись заведена старой сборкой приложения.
            _uiState.update {
                it.copy(lastError = "Нет ключа шифрования. Войдите заново — он выведется из кодового слова.")
            }
            return
        }

        val contact = prefs.contacts()
            .firstOrNull { it.handle.equals(recipientHandle, ignoreCase = true) }
        val peerKey = contact?.publicKey?.let { decodeKey(it) }
        if (contact == null || peerKey == null) {
            _uiState.update { it.copy(lastError = "Этого позывного нет среди знакомых.") }
            return
        }

        scope.launch {
            val id = TxtRecordCodec.newId()
            val nowSeconds = System.currentTimeMillis() / 1000L

            val sealed = runCatching {
                val session = EfirCrypto.sessionKey(privateKey, peerKey)
                EfirCrypto.seal(session, text.toByteArray(Charsets.UTF_8))
            }.getOrElse { error ->
                Log.e(TAG, "Не удалось зашифровать личное сообщение", error)
                _uiState.update { it.copy(lastError = "Не удалось зашифровать сообщение.") }
                return@launch
            }

            directRecord = TxtRecordCodec.encodeDirect(
                id = id,
                sealed = sealed,
                recipientAddress = EfirCrypto.addressFor(peerKey),
                senderPublicKey = EfirCrypto.publicKeyOf(privateKey),
                channel = prefs.transmitChannel,
                timestampSeconds = nowSeconds,
            )

            if (!republishServices()) {
                directRecord = null
                _uiState.update { it.copy(lastError = "Не удалось передать сообщение.") }
                return@launch
            }

            localIds.add(id)
            seenIds[id] = true
            appendMessage(
                EfirMessage(
                    id = id,
                    text = text,
                    nick = _uiState.value.nick,
                    channel = prefs.transmitChannel,
                    isDirect = true,
                    peerHandle = contact.handle,
                    sentAtEpochSeconds = nowSeconds,
                    receivedAtMillis = System.currentTimeMillis(),
                    origin = EfirMessage.Origin.LOCAL,
                )
            )
            _uiState.update {
                it.copy(isBroadcasting = true, directRecipient = contact.handle)
            }
            startAdvertiseKeepAlive()
            syncService()
            Log.i(TAG, "Личное сообщение для ${contact.handle} в эфире, id=$id")
        }
    }

    /** Снимает с эфира только личное сообщение, оставляя открытую передачу. */
    fun stopDirect() {
        directRecord = null
        scope.launch {
            republishServices()
            _uiState.update { it.copy(directRecipient = null) }
        }
    }

    /**
     * Переобъявляет оба слота.
     *
     * `clearLocalServices` снимает все службы разом, точечного удаления в
     * этом API по сути нет, поэтому единственный надёжный путь — очистить
     * и выложить заново то, что должно висеть.
     */
    /**
     * Имя объявляемой службы — с хвостом от идентификатора сообщения.
     *
     * Это лекарство от самой обидной поломки: система соседа кеширует ответ
     * обнаружения и потом раз за разом отдаёт приложению **старую** копию.
     * Замерено: приёмник получил двадцать шесть TXT-записей подряд, и все
     * двадцать шесть — с одним и тем же id десятиминутной давности, хотя
     * отправитель к тому времени сменил сообщение. Выглядело это как «одно
     * сообщение прошло, дальше тишина», а очистка данных помогала ровно
     * потому, что заводила новый канал P2P с пустым кешем.
     *
     * Раз кеш ключуется именем службы, пусть у каждого сообщения будет своё:
     * тогда для соседа это новая служба, которой в кеше нет, и запись доходит.
     * Хвост — только ASCII: кириллица в имени службы до соседа не доедет.
     */
    private fun instanceNameFor(prefix: String, record: Map<String, String>): String {
        val id = record[TxtRecordCodec.KEY_ID].orEmpty().filter { it.isLetterOrDigit() }
        return if (id.isEmpty()) prefix else prefix + "-" + id.takeLast(INSTANCE_SUFFIX_LENGTH)
    }

    private suspend fun republishServices(): Boolean {
        val mgr = manager ?: return false
        val ch = channel ?: return false

        p2pCall("clearLocalServices") { mgr.clearLocalServices(ch, it) }

        var ok = true
        publicRecord?.let { record ->
            val info = WifiP2pDnsSdServiceInfo.newInstance(
                instanceNameFor(SERVICE_INSTANCE, record), SERVICE_TYPE, record
            )
            ok = p2pCall("addLocalService (открытая)") { mgr.addLocalService(ch, info, it) } && ok
        }
        directRecord?.let { record ->
            val info = WifiP2pDnsSdServiceInfo.newInstance(
                instanceNameFor(SERVICE_INSTANCE_DIRECT, record), SERVICE_TYPE, record
            )
            ok = p2pCall("addLocalService (личное)") { mgr.addLocalService(ch, info, it) } && ok
        }

        if (publicRecord == null && directRecord == null) return true

        // Само объявление пассивно. discoverPeers переводит интерфейс P2P в
        // режим прослушивания и поиска, и только тогда соседи начинают нас
        // опрашивать и получать ответ обнаружения.
        p2pCall("discoverPeers") { mgr.discoverPeers(ch, it) }
        return ok
    }

    /**
     * Наш ли это адрес.
     *
     * Начиная с Android 6 система прячет настоящий MAC и подсовывает всем
     * `02:00:00:00:00:00`. Если сравнивать в лоб, то при совпадении заглушек
     * приложение примет за своё эхо вообще любое чужое сообщение и тихо
     * выбросит его — поломка, которую в логе не видно. Поэтому обезличенный
     * адрес не считается совпадением.
     */
    private fun isSelfAddress(address: String?): Boolean {
        val own = ownDeviceAddress
        if (address.isNullOrEmpty() || own.isNullOrEmpty()) return false
        if (address == ANONYMIZED_MAC || own == ANONYMIZED_MAC) return false
        return address.equals(own, ignoreCase = true)
    }

    private fun decodeKey(base64: String): ByteArray? = try {
        Base64.decode(base64, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
            .takeIf { it.size == EfirCrypto.PRIVATE_KEY_BYTES }
    } catch (e: IllegalArgumentException) {
        null
    }

    fun clearHistory() {
        seenIds.clear()
        localIds.clear()
        _uiState.update { it.copy(messages = emptyList()) }
    }

    /** Полный адрес прикреплённой страницы для принятого кода. */
    fun linkFor(code: String): String? {
        val base = prefs.siteUrl
        return if (base.isBlank()) null else "$base/p/$code"
    }

    /**
     * Адрес личной ленты отправителя. На сайте нет ни каталога, ни поиска —
     * этот код пришёл из эфира и другого пути к ленте не существует.
     */
    fun profileLinkFor(profileCode: String): String? {
        val base = prefs.siteUrl
        return if (base.isBlank()) null else "$base/u/$profileCode"
    }

    fun consumeError() = _uiState.update { it.copy(lastError = null) }
    fun consumeNotice() = _uiState.update { it.copy(lastNotice = null) }
    fun consumeRegisterError() = _uiState.update { it.copy(registerError = null) }

    // --------------------------------------------------------------- обнаружение

    fun toggleScanning() {
        if (_uiState.value.isScanning) stopScanning() else startScanning()
    }

    private fun startScanning() {
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) {
            _uiState.update { it.copy(lastError = "Wi-Fi Direct недоступен.") }
            return
        }
        if (!_uiState.value.permissionsGranted) {
            _uiState.update { it.copy(lastError = "Нужно разрешение на устройства поблизости.") }
            return
        }
        if (_uiState.value.locationServicesRequired && !_uiState.value.locationServicesEnabled) {
            _uiState.update {
                it.copy(lastError = "Включите геолокацию — до Android 13 без неё обнаружение молчит.")
            }
            return
        }

        _uiState.update { it.copy(isScanning = true) }
        prefs.receiveOn = true
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            var backoff = INITIAL_BACKOFF_MS
            while (isActive) {
                val ok = runDiscoveryRound(mgr, ch)
                if (ok) {
                    backoff = INITIAL_BACKOFF_MS
                    // Период берётся из настроек на каждом круге, чтобы правка
                    // применялась сразу, а не после перезапуска приёма.
                    delay(prefs.listenIntervalSeconds * 1000L)
                } else {
                    // BUSY — обычное дело, когда стек P2P занят своим делом.
                    // Отступаем, а не долбим систему повторами.
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
            }
        }
        syncService()
        Log.i(TAG, "Приём включён")
    }

    private fun stopScanning() {
        discoveryJob?.cancel()
        discoveryJob = null
        prefs.receiveOn = false
        _uiState.update { it.copy(isScanning = false) }
        syncService()
        val mgr = manager
        val ch = channel
        if (mgr != null && ch != null) {
            scope.launch {
                p2pCall("clearServiceRequests") { mgr.clearServiceRequests(ch, it) }
                p2pCall("stopPeerDiscovery") { mgr.stopPeerDiscovery(ch, it) }
            }
        }
        Log.i(TAG, "Приём выключен")
    }

    /**
     * Один полный круг сканирования.
     *
     * Сброс и повторное добавление запроса на каждом круге — не лишняя работа:
     * система помнит, какие службы уже отдавала по этому запросу, и второй раз
     * их не приносит. Без сброса сосед, который держит в эфире одно и то же
     * сообщение, был бы показан один раз и дальше как будто замолчал, а новое
     * сообщение после сорвавшегося круга не всплыло бы вообще никогда.
     *
     * Круг начинается с явной остановки обнаружения — и это не формальность.
     * Пока поиск для системы «идёт», повторный `discoverServices` она принимает
     * с onSuccess и не делает ничего: нового опроса соседей нет, а значит нет и
     * новых ответов. Видно это было так: два телефона отправили по девять
     * сообщений, каждый сделал под три десятка кругов — и принял ровно одно,
     * самое первое, когда обнаружение запускалось с нуля. Дальше эфир для них
     * замолкал навсегда.
     */
    @SuppressLint("MissingPermission")
    private suspend fun runDiscoveryRound(mgr: WifiP2pManager, ch: WifiP2pManager.Channel): Boolean {
        // Слушателей переустанавливаем: часть прошивок теряет их при
        // перезапуске службы P2P, причём молча.
        runCatching { mgr.setDnsSdResponseListeners(ch, serviceResponseListener, txtRecordListener) }

        // Останавливаем прежний поиск, иначе следующий не начнётся.
        p2pCall("stopPeerDiscovery") { mgr.stopPeerDiscovery(ch, it) }

        p2pCall("clearServiceRequests") { mgr.clearServiceRequests(ch, it) }

        // Запрос намеренно без аргументов. Проверено на двух телефонах:
        // newInstance(SERVICE_TYPE) приносит только список экземпляров —
        // DnsSdServiceResponseListener срабатывает, службу видно, а
        // DnsSdTxtRecordListener не вызывается ни разу, то есть сами
        // сообщения не приезжают. Запрос без типа тянет и TXT-записи;
        // чужие службы отсеиваются уже у нас, по домену.
        val request = WifiP2pDnsSdServiceRequest.newInstance()
        if (!p2pCall("addServiceRequest") { mgr.addServiceRequest(ch, request, it) }) return false

        // discoverServices попутно запускает и поиск устройств, поэтому
        // отдельный discoverPeers во время приёма не нужен.
        return p2pCall("discoverServices") { mgr.discoverServices(ch, it) }
    }

    /**
     * Локальная служба доступна, только пока интерфейс P2P в режиме
     * прослушивания, а система даёт этому режиму истечь. Периодически взводим
     * его заново — но лишь тогда, когда цикл обнаружения не греет радио сам.
     *
     * Взводим именно полным кругом обнаружения, а не голым `discoverPeers`.
     * Проверено на двух телефонах: с `discoverPeers` вещающий с выключенным
     * приёмом остаётся неслышимым — сосед крутит обнаружение, всё отвечает
     * onSuccess, а служб не находит ни одной. Стоит тому же телефону начать
     * слушать самому — и его объявление доходит с первого круга. То есть
     * отвечать на запросы обнаружения устройство начинает, только когда само
     * находится в полном режиме поиска служб.
     *
     * Цена — вещающий греет радио так же, как слушающий. Это лучше, чем
     * молчащая в эфир передача, о которой хозяин не догадывается.
     */
    private fun startAdvertiseKeepAlive() {
        advertiseKeepAliveJob?.cancel()
        val mgr = manager ?: return
        val ch = channel ?: return
        advertiseKeepAliveJob = scope.launch {
            // Первый круг — сразу: до него объявление уже висит, но ответить
            // на чужой запрос устройство ещё не готово.
            if (!_uiState.value.isScanning) runDiscoveryRound(mgr, ch)
            while (isActive) {
                delay(prefs.transmitIntervalSeconds * 1000L)
                if (!_uiState.value.isBroadcasting) break
                if (_uiState.value.isScanning) continue
                runDiscoveryRound(mgr, ch)
            }
        }
    }

    // ----------------------------------------------------------------- приём

    private fun handleTxtRecord(record: Map<String, String>, srcDevice: WifiP2pDevice) {
        if (TxtRecordCodec.isDirect(record)) {
            handleDirectRecord(record)
            return
        }

        val message = TxtRecordCodec.decode(
            record = record,
            sourceDeviceName = srcDevice.deviceName,
            receivedAtMillis = System.currentTimeMillis(),
        ) ?: return

        // Канал, который мы не слушаем. Радио одно на всех, разделение чисто
        // логическое, поэтому отсев происходит здесь, а не где-то в железе.
        if (!prefs.isListening(message.channel)) {
            Log.d(TAG, "Пропущено: канал ${message.channel} не в наборе")
            return
        }

        // Некоторые чипсеты возвращают нам наше же объявление. Надёжно ловит
        // это localIds; адрес — лишь подстраховка, и работает она не везде.
        if (message.id in localIds) return
        if (isSelfAddress(srcDevice.deviceAddress)) return

        // Одна и та же служба приходит на каждом круге обнаружения.
        if (seenIds.put(message.id, true) != null) return

        Log.i(TAG, "Принято «${message.text}» от ${message.nick} (id=${message.id})")
        appendMessage(message)
        rememberSender(record, message)

        val key = srcDevice.deviceAddress ?: srcDevice.deviceName
        if (key != null && seenDevices.add(key)) {
            _uiState.update { it.copy(peersSeen = seenDevices.size) }
        }
    }

    /**
     * Пополняет круг знакомых по открытой передаче. Это единственный способ
     * туда попасть: из сети никого не добавить, нужно оказаться рядом.
     */
    private fun rememberSender(record: Map<String, String>, message: EfirMessage) {
        val publicKey = TxtRecordCodec.publicKeyFrom(record) ?: return
        if (message.nick.isBlank() || message.nick == "аноним") return

        val changed = prefs.rememberContact(
            Contact(
                handle = message.nick,
                publicKey = TxtRecordCodec.encodeBytes(publicKey),
                profileCode = message.profileCode,
                lastSeenMillis = message.receivedAtMillis,
            )
        )

        if (!changed) {
            // Ключ для известного позывного не совпал. Либо человек завёл
            // учётную запись заново, либо кто-то представляется его именем.
            // Молча перезаписывать нельзя — это и была бы подмена.
            val known = prefs.contacts().firstOrNull { it.handle.equals(message.nick, true) }
            if (known != null && known.publicKey != TxtRecordCodec.encodeBytes(publicKey)) {
                Log.w(TAG, "У позывного ${message.nick} другой ключ — запись не тронута")
                _uiState.update {
                    it.copy(
                        lastNotice = "У позывного ${message.nick} другой ключ. " +
                            "Прежний оставлен — писать по-старому безопасно."
                    )
                }
            }
            return
        }

        _uiState.update { it.copy(contacts = prefs.contacts()) }
    }

    /**
     * Личное сообщение из эфира.
     *
     * Адрес в записи — только подсказка, стоит ли тратить время на попытку.
     * Настоящее решение принимает проверка тега: расшифровалось — значит наше
     * и не подделано, не расшифровалось — молча проходим мимо.
     */
    private fun handleDirectRecord(record: Map<String, String>) {
        val envelope = TxtRecordCodec.decodeDirect(record, System.currentTimeMillis()) ?: return

        // По каналу личные сообщения не отсеиваются намеренно: письмо
        // адресовано лично вам и зашифровано вашим ключом, канал тут ни при
        // чём. Раньше проверка стояла — и письмо терялось, если собеседник
        // ушёл вещать на другую тему.
        if (envelope.id in localIds) return
        if (seenIds.containsKey(envelope.id)) return

        val privateKey = prefs.privateKey.takeIf { it.isNotBlank() }?.let { decodeKey(it) } ?: return
        val myAddress = runCatching {
            EfirCrypto.addressFor(EfirCrypto.publicKeyOf(privateKey))
        }.getOrNull() ?: return

        if (!envelope.recipientAddress.equals(myAddress, ignoreCase = true)) return

        val plaintext = runCatching {
            val session = EfirCrypto.sessionKey(privateKey, envelope.senderPublicKey)
            EfirCrypto.open(session, envelope.sealed)
        }.getOrNull() ?: return

        seenIds[envelope.id] = true

        val senderKey = TxtRecordCodec.encodeBytes(envelope.senderPublicKey)
        val sender = prefs.contacts().firstOrNull { it.publicKey == senderKey }

        Log.i(TAG, "Личное сообщение от ${sender?.handle ?: "неизвестного"} (id=${envelope.id})")

        appendMessage(
            EfirMessage(
                id = envelope.id,
                text = String(plaintext, Charsets.UTF_8),
                // Имени в записи нет намеренно: его подставляем по ключу, а
                // если человек ещё не знаком — так и говорим.
                nick = sender?.handle ?: "неизвестный",
                channel = envelope.channel,
                isDirect = true,
                sentAtEpochSeconds = envelope.sentAtEpochSeconds,
                receivedAtMillis = System.currentTimeMillis(),
                origin = EfirMessage.Origin.REMOTE,
            )
        )
    }

    private fun appendMessage(message: EfirMessage) {
        _uiState.update { state ->
            val next = state.messages + message
            state.copy(messages = if (next.size > MAX_HISTORY) next.takeLast(MAX_HISTORY) else next)
        }

        // В журнал идёт только принятое: своё и так известно, а смысл журнала —
        // сохранить чужое, которое из эфира уже ушло.
        if (message.origin == EfirMessage.Origin.REMOTE) {
            prefs.addToJournal(message)
            _uiState.update { it.copy(journal = prefs.journal().reversed()) }
        }
    }

    /** Убирает одну запись журнала. Из текущей ленты сообщение при этом остаётся. */
    fun deleteJournalEntry(id: String) {
        prefs.removeFromJournal(id)
        _uiState.update { it.copy(journal = prefs.journal().reversed()) }
    }

    fun clearJournal() {
        prefs.clearJournal()
        _uiState.update { it.copy(journal = emptyList()) }
    }

    // ------------------------------------------------------------------ обвязка

    /**
     * Превращает [WifiP2pManager.ActionListener] в приостанавливаемый вызов.
     *
     * Система иногда не вызывает ни один из колбэков (мёртвый канал, упавшая
     * служба P2P), поэтому каждый вызов ограничен по времени — иначе цикл
     * обнаружения навсегда встал бы на одном неудачном круге.
     */
    private suspend fun p2pCall(
        label: String,
        block: (WifiP2pManager.ActionListener) -> Unit,
    ): Boolean {
        val reason: Int? = withTimeoutOrNull(ACTION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    block(object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            if (cont.isActive) cont.resume(SUCCESS)
                        }

                        override fun onFailure(reason: Int) {
                            if (cont.isActive) cont.resume(reason)
                        }
                    })
                } catch (se: SecurityException) {
                    Log.e(TAG, "$label запрещён: ${se.message}")
                    if (cont.isActive) cont.resume(WifiP2pManager.ERROR)
                } catch (t: Throwable) {
                    Log.e(TAG, "$label упал", t)
                    if (cont.isActive) cont.resume(WifiP2pManager.ERROR)
                }
            }
        }

        return when (reason) {
            null -> {
                Log.w(TAG, "$label: не дождались ответа за ${ACTION_TIMEOUT_MS} мс")
                false
            }

            SUCCESS -> {
                Log.d(TAG, "$label: onSuccess")
                true
            }

            else -> {
                val text = reasonToText(reason)
                Log.w(TAG, "$label: onFailure — $text")
                if (reason == WifiP2pManager.P2P_UNSUPPORTED) {
                    _uiState.update { it.copy(p2pState = P2pState.UNSUPPORTED, lastError = text) }
                }
                false
            }
        }
    }

    private fun loggingListener(label: String) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() {
            Log.d(TAG, "$label: onSuccess")
        }

        override fun onFailure(reason: Int) {
            Log.w(TAG, "$label: onFailure — ${reasonToText(reason)}")
        }
    }

    private fun reasonToText(reason: Int): String = when (reason) {
        WifiP2pManager.P2P_UNSUPPORTED -> "устройство не поддерживает Wi-Fi Direct"
        WifiP2pManager.BUSY -> "стек Wi-Fi Direct занят"
        WifiP2pManager.NO_SERVICE_REQUESTS -> "не зарегистрировано ни одного запроса служб"
        WifiP2pManager.ERROR -> "внутренняя ошибка системы"
        else -> "неизвестная причина ($reason)"
    }

    companion object {
        private const val TAG = "Efir/Radio"

        @Volatile
        private var instance: EfirRadio? = null

        /**
         * Один движок на процесс. Его держат и активность, и служба, поэтому
         * привязать его к экрану нельзя: экран гаснет, а эфир — нет.
         */
        fun get(context: Context): EfirRadio =
            instance ?: synchronized(this) {
                instance ?: EfirRadio(context.applicationContext).also { instance = it }
            }

        /**
         * Имя экземпляра DNS-SD; его объявляет каждый, у кого стоит ЭФИР.
         * Только ASCII — кириллица в имени службы до соседа не доедет.
         */
        /** Сколько знаков идентификатора дописывается к имени службы. */
        private const val INSTANCE_SUFFIX_LENGTH = 6

        const val SERVICE_INSTANCE = "Efir"

        /**
         * Отдельное имя для личных сообщений — чтобы открытая передача и
         * личная записка висели в эфире одновременно, не вытесняя друг друга.
         */
        const val SERVICE_INSTANCE_DIRECT = "EfirDM"

        /** Тип службы DNS-SD. «.local.» система дописывает сама. */
        const val SERVICE_TYPE = "_presence._tcp"

        /** onSuccess как код причины, который не может совпасть с настоящим. */
        private const val SUCCESS = -1

        /** Заглушка вместо настоящего MAC, которую Android отдаёт с версии 6. */
        private const val ANONYMIZED_MAC = "02:00:00:00:00:00"

        // Периоды приёма и переобъявления живут в настройках (EfirPrefs).
        private const val ACTION_TIMEOUT_MS = 8_000L

        /** Правила задаются с точностью до минуты — чаще проверять незачем. */
        private const val SCHEDULE_TICK_MS = 30_000L
        private const val INITIAL_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 30_000L
        private const val DEDUPE_CAPACITY = 256
        private const val MAX_HISTORY = 300
    }
}
