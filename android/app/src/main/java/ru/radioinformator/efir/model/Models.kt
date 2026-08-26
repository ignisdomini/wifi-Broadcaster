package ru.radioinformator.efir.model

/**
 * Одна передача: либо ушедшая с этого устройства, либо пойманная из воздуха.
 *
 * За ней не стоит никакого соединения — это снимок TXT-записи, которая
 * оказалась видна во время очередного круга обнаружения.
 */
data class EfirMessage(
    /** Короткий идентификатор от отправителя, по нему отсеиваются повторы. */
    val id: String,
    val text: String,
    val nick: String,
    /** Код прикреплённой страницы на сайте, если отправитель её приложил. */
    val linkCode: String? = null,
    /** Код личной ленты отправителя — единственный способ узнать о ней. */
    val profileCode: String? = null,
    /** Логический канал, на котором передача была поймана. */
    val channel: Int = 1,

    /** Личное сообщение: его содержимое видел только адресат. */
    val isDirect: Boolean = false,

    /** Для исходящего личного — кому написано; для входящего пусто. */
    val peerHandle: String? = null,
    /** Секунды эпохи по часам отправителя. Часы никто не синхронизирует. */
    val sentAtEpochSeconds: Long,
    /** Локальное время приёма — вот ему верить можно. */
    val receivedAtMillis: Long,
    val origin: Origin,
    /** Имя устройства Wi-Fi Direct отправителя, если система его открыла. */
    val sourceDeviceName: String? = null,
) {
    enum class Origin { LOCAL, REMOTE }
}

/** Состояние стека Wi-Fi P2P, приходит из системных широковещаний. */
enum class P2pState {
    UNKNOWN,

    /** Устройство вообще не умеет Wi-Fi Direct. */
    UNSUPPORTED,

    /** Wi-Fi выключен, а вместе с ним и P2P. */
    DISABLED,

    ENABLED,
}

/** Лимиты, которые задаёт админка сайта. Приложение читает их при запуске. */
data class SiteLimits(
    val maxTextChars: Int = 2000,
    val broadcastMaxBytes: Int = 140,
) {
    companion object {
        /** Пока сайт не ответил, работаем на этих значениях. */
        val DEFAULT = SiteLimits()
    }
}

/**
 * Что приложение прикрепляет к передаче — только текст.
 *
 * Файлы не прикрепляются намеренно: фотография превращает короткую записку
 * в повод потом разбирать архив, а вся затея как раз о том, чтобы не копить.
 */
data class Attachment(
    val text: String = "",
) {
    val isEmpty: Boolean get() = text.isBlank()
}

/** Состояние связи с сайтом — отдельно от радиочасти, они независимы. */
enum class SiteStatus {
    /** Адрес сайта не задан или пуст. */
    NOT_CONFIGURED,
    UNKNOWN,
    CHECKING,
    ONLINE,
    OFFLINE,
}

/**
 * Знакомый: тот, чью открытую передачу мы поймали и вместе с ней получили
 * публичный ключ. Список знакомых — это и есть весь круг, кому можно написать
 * лично. Пополняется он только радиообменом, из сети добавить некого.
 */
data class Contact(
    val handle: String,
    /** Публичный ключ X25519, base64url без выравнивания. */
    val publicKey: String,
    /** Код личной ленты на сайте, если отправитель его прислал. */
    val profileCode: String? = null,
    val lastSeenMillis: Long = 0L,
)

/** Запись своей ленты, какой её знает сеть. */
data class FeedEntry(
    val code: String,
    val broadcast: String,
    val excerpt: String,
    val channel: Int,
    val createdHuman: String,
    val url: String,
    /** Скрыта премодерацией: владельцу видна, остальным нет. */
    val hidden: Boolean = false,
)

/** Что сейчас со своей лентой: её надо загрузить из сети. */
enum class FeedStatus { IDLE, LOADING, READY, FAILED }

/**
 * Визитка на странице своей ленты.
 *
 * Обычно сеть о человеке не знает ничего, и это её главное свойство. Но
 * кафе, мастеру или рынку прятаться незачем — им нужно, чтобы после эфира с
 * ними связались. Поэтому визитка целиком добровольная: пустые поля на сайт
 * не выводятся, и по умолчанию она пуста.
 */
data class ContactCard(
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val site: String = "",
    val telegram: String = "",
    val vk: String = "",
    /** Прочие соцсети одной строкой — перечислять их все смысла нет. */
    val social: String = "",
    /** Показывать ли визитку на сайте, не стирая заполненного. */
    val public: Boolean = true,
) {
    val isEmpty: Boolean
        get() = name.isBlank() && phone.isBlank() && email.isBlank() && site.isBlank() &&
            telegram.isBlank() && vk.isBlank() && social.isBlank()
}

/** Состояние визитки: её надо загрузить из сети и туда же сохранить. */
enum class ContactsStatus { IDLE, LOADING, READY, SAVING, FAILED }

/** Состояние учётной записи. Без неё эфир работает, а лента — нет. */
enum class AccountStatus {
    /** Позывной ещё не заведён — показываем экран знакомства. */
    NONE,
    REGISTERING,
    READY,
}

data class EfirUiState(
    val messages: List<EfirMessage> = emptyList(),
    val nick: String = "",
    val isScanning: Boolean = false,

    /** Идёт объявление локальной службы. */
    val isBroadcasting: Boolean = false,
    val advertisedText: String? = null,

    val p2pState: P2pState = P2pState.UNKNOWN,
    val permissionsGranted: Boolean = false,

    /** true на API < 33, где обнаружение гасит системный тумблер геолокации. */
    val locationServicesRequired: Boolean = false,
    val locationServicesEnabled: Boolean = true,

    /**
     * Система вправе усыпить приложение ради батареи. Пока это так, приём с
     * погасшим экраном рано или поздно замолкает, чего бы мы ни делали.
     */
    val batteryRestricted: Boolean = false,

    /** Совет про экономию батареи убран вручную и больше не показывается. */
    val batteryNoticeHidden: Boolean = false,

    /** Держать процессор разбуженным, пока идёт приём. */
    val keepAwake: Boolean = true,

    /** Не давать экрану гаснуть, пока приложение открыто. */
    val keepScreenOn: Boolean = false,

    /** Сколько разных устройств отметилось за сеанс. */
    val peersSeen: Int = 0,

    // --- сайт ---
    val siteUrl: String = "",
    val siteName: String = "ЭФИР",
    val siteStatus: SiteStatus = SiteStatus.UNKNOWN,
    val siteRequiresToken: Boolean = false,
    val limits: SiteLimits = SiteLimits.DEFAULT,

    // --- учётная запись ---
    val accountStatus: AccountStatus = AccountStatus.NONE,
    val profileCode: String = "",
    val publishToFeed: Boolean = true,
    val registerError: String? = null,

    /** Расписание вещания: правила и то, какое из них сейчас в эфире. */
    val schedule: ScheduleState = ScheduleState(),

    // --- каналы и периоды ---
    /** Темы каналов по порядку номеров: «Общий», «Еда и кафе» и так далее. */
    val channelTitles: List<String> = emptyList(),
    val transmitChannel: Int = 1,
    /** Каналы, которые слушаем. Можно один, несколько или все сразу. */
    val listenChannels: Set<Int> = setOf(1, 6),
    /** Тревожный канал отключён вручную. */
    val alarmMuted: Boolean = false,
    val listenIntervalSeconds: Int = 20,
    val transmitIntervalSeconds: Int = 30,

    /** Черновик вложения: заполняется на экране «дописать». */
    val attachment: Attachment = Attachment(),
    val isUploading: Boolean = false,

    /**
     * Журнал принятых передач. Переживает перезапуск, в отличие от ленты:
     * это единственное место, где сообщение остаётся после того, как ушло
     * из воздуха.
     */
    val journal: List<EfirMessage> = emptyList(),

    // --- визитка ---
    val contactCard: ContactCard = ContactCard(),
    val contactsStatus: ContactsStatus = ContactsStatus.IDLE,
    val contactsError: String? = null,

    // --- своя лента в сети ---
    val feed: List<FeedEntry> = emptyList(),
    val feedStatus: FeedStatus = FeedStatus.IDLE,
    val feedTotal: Int = 0,
    val feedError: String? = null,

    // --- личные сообщения ---
    /** Кого мы слышали в эфире — только им и можно написать лично. */
    val contacts: List<Contact> = emptyList(),
    /** Личное сообщение, которое сейчас висит в эфире, и кому оно. */
    val directRecipient: String? = null,

    val lastError: String? = null,
    val lastNotice: String? = null,
) {
    /** Название канала по номеру; если тем нет — просто номер. */
    fun channelTitle(channel: Int): String =
        channelTitles.getOrNull(channel - 1)?.takeIf { it.isNotBlank() } ?: "Канал $channel"

    /** Радиочасть готова передавать и принимать. */
    val canUseRadio: Boolean
        get() = permissionsGranted &&
            p2pState == P2pState.ENABLED &&
            (!locationServicesRequired || locationServicesEnabled)
}
