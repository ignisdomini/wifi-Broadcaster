package ru.radioinformator.efir.net

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.radioinformator.efir.BuildConfig
import ru.radioinformator.efir.model.Contact
import ru.radioinformator.efir.model.EfirMessage
import ru.radioinformator.efir.model.ScheduleRule

/**
 * Настройки приложения: учётная запись, адрес сайта, каналы и периоды работы
 * с эфиром. Хранятся в SharedPreferences — состояния мало, база тут была бы
 * лишней.
 *
 * Кодовое слово намеренно не сохраняется: из него один раз считается ключ,
 * и дальше живёт только ключ. Показать кодовое слово потом нельзя ни нам,
 * ни тому, кто доберётся до памяти телефона.
 */
class EfirPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("efir", Context.MODE_PRIVATE)

    // --- учётная запись ---

    var handle: String
        get() = prefs.getString(KEY_HANDLE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_HANDLE, value.trim()).apply()

    var authKey: String
        get() = prefs.getString(KEY_AUTH_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_AUTH_KEY, value).apply()

    var profileCode: String
        get() = prefs.getString(KEY_PROFILE_CODE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PROFILE_CODE, value).apply()

    /**
     * Приватный ключ шифрования, base64url. Выводится из кодового слова один
     * раз при регистрации — в этот момент слово ещё известно — и дальше живёт
     * только здесь: само слово мы не храним принципиально.
     */
    var privateKey: String
        get() = prefs.getString(KEY_PRIVATE_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_PRIVATE_KEY, value).apply()

    val identity: Identity
        get() = Identity(handle, authKey, profileCode)

    fun saveIdentity(identity: Identity) {
        prefs.edit()
            .putString(KEY_HANDLE, identity.handle)
            .putString(KEY_AUTH_KEY, identity.authKey)
            .putString(KEY_PROFILE_CODE, identity.profileCode)
            .apply()
    }

    fun forgetIdentity() {
        prefs.edit()
            .remove(KEY_HANDLE)
            .remove(KEY_AUTH_KEY)
            .remove(KEY_PROFILE_CODE)
            .remove(KEY_PRIVATE_KEY)
            .apply()
    }

    // --- знакомые ---

    /**
     * Список тех, кого слышали в эфире. Хранится обычным JSON: записей
     * десятки, ради них заводить базу незачем.
     */
    fun contacts(): List<Contact> {
        val raw = prefs.getString(KEY_CONTACTS, "").orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val handle = item.optString("h")
                val key = item.optString("k")
                if (handle.isBlank() || key.isBlank()) return@mapNotNull null
                Contact(
                    handle = handle,
                    publicKey = key,
                    profileCode = item.optString("p").takeIf { it.isNotBlank() },
                    lastSeenMillis = item.optLong("s", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Запоминает или обновляет знакомого. Ключ считается неизменным: если для
     * известного позывного пришёл другой ключ, запись не трогаем и сообщаем
     * об этом — так подмена не проходит молча.
     *
     * @return true, если список изменился.
     */
    fun rememberContact(contact: Contact): Boolean {
        val current = contacts()
        val existing = current.firstOrNull { it.handle.equals(contact.handle, ignoreCase = true) }

        if (existing != null && existing.publicKey != contact.publicKey) {
            return false
        }

        val merged = current.filterNot { it.handle.equals(contact.handle, ignoreCase = true) } +
            contact.copy(profileCode = contact.profileCode ?: existing?.profileCode)

        val array = JSONArray()
        merged.sortedByDescending { it.lastSeenMillis }.take(MAX_CONTACTS).forEach {
            array.put(
                JSONObject()
                    .put("h", it.handle)
                    .put("k", it.publicKey)
                    .put("p", it.profileCode.orEmpty())
                    .put("s", it.lastSeenMillis)
            )
        }
        prefs.edit().putString(KEY_CONTACTS, array.toString()).apply()
        return existing == null || existing.lastSeenMillis != contact.lastSeenMillis
    }

    fun forgetContacts() {
        prefs.edit().remove(KEY_CONTACTS).apply()
    }

    // --- планировщик вещания ---

    var scheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULE_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_SCHEDULE_ON, value).apply()

    fun scheduleRules(): List<ScheduleRule> {
        val raw = prefs.getString(KEY_SCHEDULE, "").orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("i")
                if (id.isBlank()) return@mapNotNull null

                val days = item.optJSONArray("d")
                ScheduleRule(
                    id = id,
                    text = item.optString("t"),
                    channel = item.optInt("c", CHANNEL_DEFAULT),
                    fromMinutes = item.optInt("f", 0),
                    toMinutes = item.optInt("u", 1439),
                    weekDays = buildSet {
                        if (days != null) {
                            for (k in 0 until days.length()) add(days.optInt(k))
                        }
                    },
                    fromDateMillis = item.optLong("fd", 0L),
                    toDateMillis = item.optLong("td", 0L),
                    enabled = item.optBoolean("e", true),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveScheduleRules(rules: List<ScheduleRule>) {
        val array = JSONArray()
        rules.take(MAX_SCHEDULE_RULES).forEach { rule ->
            val days = JSONArray()
            rule.weekDays.forEach { days.put(it) }
            array.put(
                JSONObject()
                    .put("i", rule.id)
                    .put("t", rule.text)
                    .put("c", rule.channel)
                    .put("f", rule.fromMinutes)
                    .put("u", rule.toMinutes)
                    .put("d", days)
                    .put("fd", rule.fromDateMillis)
                    .put("td", rule.toDateMillis)
                    .put("e", rule.enabled)
            )
        }
        prefs.edit().putString(KEY_SCHEDULE, array.toString()).apply()
    }

    // --- журнал принятых передач ---

    /**
     * Всё принятое из эфира, с датой и временем. В отличие от текущей ленты
     * журнал переживает перезапуск: это единственное место, где сообщение
     * остаётся после того, как ушло из воздуха.
     */
    fun journal(): List<EfirMessage> {
        val raw = prefs.getString(KEY_JOURNAL, "").orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("i")
                if (id.isBlank()) return@mapNotNull null
                EfirMessage(
                    id = id,
                    text = item.optString("t"),
                    nick = item.optString("n"),
                    linkCode = item.optString("l").takeIf { it.isNotBlank() },
                    profileCode = item.optString("p").takeIf { it.isNotBlank() },
                    channel = item.optInt("c", 1),
                    isDirect = item.optBoolean("d", false),
                    sentAtEpochSeconds = item.optLong("s", 0L),
                    receivedAtMillis = item.optLong("r", 0L),
                    origin = EfirMessage.Origin.REMOTE,
                    sourceDeviceName = item.optString("v").takeIf { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun addToJournal(message: EfirMessage) {
        val current = journal()
        if (current.any { it.id == message.id }) return
        writeJournal(current + message)
    }

    fun removeFromJournal(id: String) {
        writeJournal(journal().filterNot { it.id == id })
    }

    fun clearJournal() {
        prefs.edit().remove(KEY_JOURNAL).apply()
    }

    private fun writeJournal(entries: List<EfirMessage>) {
        val array = JSONArray()
        entries.takeLast(MAX_JOURNAL).forEach {
            array.put(
                JSONObject()
                    .put("i", it.id)
                    .put("t", it.text)
                    .put("n", it.nick)
                    .put("l", it.linkCode.orEmpty())
                    .put("p", it.profileCode.orEmpty())
                    .put("c", it.channel)
                    .put("d", it.isDirect)
                    .put("s", it.sentAtEpochSeconds)
                    .put("r", it.receivedAtMillis)
                    .put("v", it.sourceDeviceName.orEmpty())
            )
        }
        prefs.edit().putString(KEY_JOURNAL, array.toString()).apply()
    }

    // --- сайт ---

    var siteUrl: String
        get() = prefs.getString(KEY_SITE_URL, BuildConfig.DEFAULT_SITE_URL).orEmpty()
        set(value) = prefs.edit().putString(KEY_SITE_URL, normalizeUrl(value)).apply()

    var apiToken: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    /** Публиковать ли передачи в личную ленту. Радио работает и без этого. */
    var publishToFeed: Boolean
        get() = prefs.getBoolean(KEY_PUBLISH, true)
        set(value) = prefs.edit().putBoolean(KEY_PUBLISH, value).apply()

    /**
     * Держать ли процессор разбуженным, пока идёт приём.
     *
     * Включено по умолчанию: без этого в глубоком сне не срабатывают таймеры,
     * круги обнаружения не запускаются, и телефон в кармане перестаёт слышать
     * эфир — то есть перестаёт делать единственное, ради чего он тут лежит.
     * Кому дороже батарея, тот выключит и будет ловить урывками.
     */
    var keepAwake: Boolean
        get() = prefs.getBoolean(KEY_KEEP_AWAKE, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_AWAKE, value).apply()

    /**
     * Был ли включён приём.
     *
     * Радиоинформатор — вещь, которую включают и забывают. Если после
     * перезагрузки телефона или выгрузки приложения приём молча не вернётся,
     * человек будет уверен, что слушает эфир, а на деле нет. Поэтому состояние
     * запоминается и восстанавливается при следующем запуске.
     */
    /**
     * Не давать экрану гаснуть, пока приложение открыто.
     *
     * Принудительная мера для случаев, когда важно ничего не пропустить:
     * дежурство по тревожному каналу, торговля с прилавка, поход. Экран —
     * самый прожорливый потребитель в телефоне, поэтому по умолчанию выключено.
     */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var receiveOn: Boolean
        get() = prefs.getBoolean(KEY_RECEIVE_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_RECEIVE_ON, value).apply()

    /**
     * Совет про экономию батареи убран хозяином.
     *
     * Проверить, что он и правда снял ограничение, нельзя: на Xiaomi «Без
     * ограничений» и «Автозапуск» живут в своей подсистеме, а системный
     * признак экономии после них не меняется. Значит, единственный честный
     * выход — дать закрыть карточку руками.
     */
    var batteryNoticeHidden: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_NOTICE_HIDDEN, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_NOTICE_HIDDEN, value).apply()

    // --- эфир ---

    /**
     * Темы каналов, как их называет сеть. Кэшируются, чтобы подписи были
     * видны и без интернета: радиоинформатор обязан работать в лесу и в
     * отключённом от связи районе, а имена каналов там нужны не меньше.
     */
    var channelTitles: List<String>
        get() {
            val raw = prefs.getString(KEY_CHANNEL_TITLES, "").orEmpty()
            if (raw.isBlank()) return DEFAULT_CHANNEL_TITLES
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).map { array.optString(it) }
                    .takeIf { it.size == CHANNEL_MAX }
                    ?: DEFAULT_CHANNEL_TITLES
            }.getOrDefault(DEFAULT_CHANNEL_TITLES)
        }
        set(value) {
            if (value.size != CHANNEL_MAX) return
            val array = JSONArray()
            value.forEach { array.put(it) }
            prefs.edit().putString(KEY_CHANNEL_TITLES, array.toString()).apply()
        }

    fun channelTitle(channel: Int): String {
        val index = channel.coerceIn(CHANNEL_MIN, CHANNEL_MAX) - 1
        return channelTitles.getOrElse(index) { "Канал ${index + 1}" }
    }

    /** Канал, на котором мы вещаем. */
    var transmitChannel: Int
        get() = prefs.getInt(KEY_TX_CHANNEL, CHANNEL_DEFAULT).coerceIn(CHANNEL_MIN, CHANNEL_MAX)
        set(value) = prefs.edit()
            .putInt(KEY_TX_CHANNEL, value.coerceIn(CHANNEL_MIN, CHANNEL_MAX)).apply()

    /**
     * Каналы, которые мы слушаем.
     *
     * Слушать хоть все шестнадцать не стоит ничего: передачи со всех каналов
     * и так приезжают в приложение, фильтр чисто программный. Единственная
     * цена — шум в списке, поэтому выбор оставлен человеку.
     *
     * Тревожный канал добавляется всегда, кроме случая, когда его отключили
     * осознанно: смысл шестого канала в том, что его слышат все, и ушедший
     * слушать «Еду» не должен пропустить просьбу о помощи.
     */
    var listenChannels: Set<Int>
        get() {
            val raw = prefs.getString(KEY_RX_CHANNELS, null)
                ?: return setOf(CHANNEL_DEFAULT, ALARM_CHANNEL)

            val chosen = raw.split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in CHANNEL_MIN..CHANNEL_MAX }
                .toMutableSet()

            if (!alarmMuted) chosen += ALARM_CHANNEL
            return chosen.ifEmpty { setOf(CHANNEL_DEFAULT) }
        }
        set(value) {
            val cleaned = value.filter { it in CHANNEL_MIN..CHANNEL_MAX }.toSortedSet()
            prefs.edit().putString(KEY_RX_CHANNELS, cleaned.joinToString(",")).apply()
        }

    /** Тревожный канал выключён вручную. Отдельный флаг, чтобы не путать с выбором. */
    var alarmMuted: Boolean
        get() = prefs.getBoolean(KEY_ALARM_MUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_ALARM_MUTED, value).apply()

    fun isListening(channel: Int): Boolean = channel in listenChannels

    fun toggleListenChannel(channel: Int) {
        if (channel == ALARM_CHANNEL) {
            alarmMuted = !alarmMuted
            return
        }
        val current = listenChannels.toMutableSet()
        if (!current.remove(channel)) current += channel
        listenChannels = current
    }

    fun listenToAllChannels() {
        alarmMuted = false
        listenChannels = (CHANNEL_MIN..CHANNEL_MAX).toSet()
    }

    fun listenToOnly(channel: Int) {
        listenChannels = setOf(channel)
    }

    /** Как часто перезапускается круг обнаружения, секунды. */
    var listenIntervalSeconds: Int
        get() = prefs.getInt(KEY_RX_INTERVAL, LISTEN_INTERVAL_DEFAULT)
            .coerceIn(LISTEN_INTERVAL_MIN, LISTEN_INTERVAL_MAX)
        set(value) = prefs.edit()
            .putInt(KEY_RX_INTERVAL, value.coerceIn(LISTEN_INTERVAL_MIN, LISTEN_INTERVAL_MAX))
            .apply()

    /** Как часто переобъявляется своё сообщение, секунды. */
    var transmitIntervalSeconds: Int
        get() = prefs.getInt(KEY_TX_INTERVAL, TRANSMIT_INTERVAL_DEFAULT)
            .coerceIn(TRANSMIT_INTERVAL_MIN, TRANSMIT_INTERVAL_MAX)
        set(value) = prefs.edit()
            .putInt(KEY_TX_INTERVAL, value.coerceIn(TRANSMIT_INTERVAL_MIN, TRANSMIT_INTERVAL_MAX))
            .apply()

    companion object {
        private const val KEY_HANDLE = "handle"
        private const val KEY_AUTH_KEY = "auth_key"
        private const val KEY_PROFILE_CODE = "profile_code"
        private const val KEY_PRIVATE_KEY = "private_key"
        private const val KEY_CONTACTS = "contacts"
        private const val KEY_JOURNAL = "journal"
        private const val KEY_CHANNEL_TITLES = "channel_titles"
        private const val KEY_SCHEDULE = "schedule"
        private const val KEY_SCHEDULE_ON = "schedule_on"

        /** Больше и не нужно: расписание кафе или рынка укладывается в единицы правил. */
        private const val MAX_SCHEDULE_RULES = 50

        /**
         * Имена каналов на случай, когда сеть ещё ни разу не отвечала.
         * Должны совпадать с lib/channels.php на сайте: расхождение приведёт
         * к тому, что собеседники будут звать один канал по-разному.
         */
        val DEFAULT_CHANNEL_TITLES = listOf(
            "Общий",
            "Еда и кафе",
            "Скидки и промокоды",
            "Рынок и торговля",
            "Туризм и походы",
            "Тревога",
            "Объявления",
            "Знакомства",
            "Сообщества",
            "События",
            "Транспорт",
            "Работа",
            "Свободный 13",
            "Свободный 14",
            "Свободный 15",
            "Свободный 16",
        )

        /** Больше и не нужно: круг ограничен теми, с кем реально пересекались. */
        private const val MAX_CONTACTS = 200

        /** Журнал не бесконечный: старое вытесняется, чтобы не пухли настройки. */
        private const val MAX_JOURNAL = 500
        private const val KEY_SITE_URL = "site_url"
        private const val KEY_TOKEN = "api_token"
        private const val KEY_PUBLISH = "publish_to_feed"
        private const val KEY_KEEP_AWAKE = "keep_awake"
        private const val KEY_RECEIVE_ON = "receive_on"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_BATTERY_NOTICE_HIDDEN = "battery_notice_hidden"
        private const val KEY_TX_CHANNEL = "tx_channel"
        private const val KEY_RX_CHANNELS = "rx_channels"
        private const val KEY_ALARM_MUTED = "alarm_muted"
        private const val KEY_RX_INTERVAL = "rx_interval"
        private const val KEY_TX_INTERVAL = "tx_interval"

        const val CHANNEL_MIN = 1
        const val CHANNEL_MAX = 16

        /** На первом канале все слышат друг друга без всякой настройки. */
        const val CHANNEL_DEFAULT = 1

        /** Тревожный. Слушается всегда, пока его не отключили вручную. */
        const val ALARM_CHANNEL = 6

        /**
         * Чаще 10 секунд перезапускать обнаружение вредно: система и так
         * не успевает отработать круг, а батарея садится заметно.
         */
        const val LISTEN_INTERVAL_MIN = 10
        const val LISTEN_INTERVAL_MAX = 120
        const val LISTEN_INTERVAL_DEFAULT = 20

        const val TRANSMIT_INTERVAL_MIN = 15
        const val TRANSMIT_INTERVAL_MAX = 180
        const val TRANSMIT_INTERVAL_DEFAULT = 30

        /**
         * Пользователь набирает адрес руками, поэтому мирится с
         * «radioinformator.ru», «radioinformator.ru/» и полным адресом со
         * схемой. Приводим к виду без хвостового слеша и со схемой.
         */
        fun normalizeUrl(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) return ""
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "https://$value"
            }
            return value.trimEnd('/')
        }
    }
}
