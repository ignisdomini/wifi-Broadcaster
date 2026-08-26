package ru.radioinformator.efir.p2p

import android.util.Base64
import ru.radioinformator.efir.model.EfirMessage
import java.util.concurrent.atomic.AtomicInteger

/**
 * Упаковывает сообщение в TXT-запись DNS-SD и разбирает обратно.
 *
 * Формат диктуют два ограничения:
 *
 *  1. TXT-запись — это список строк «ключ=значение», каждая не длиннее 255
 *     байт. wpa_supplicant дробит ответ на фрагменты, и чем запись короче,
 *     тем меньше шансов, что она приедет по частям или не приедет вовсе.
 *     Отсюда пейджерный размер сообщения.
 *
 *  2. Разборщик на приёмной стороне (AOSP) делит строку по '=' и не церемонится
 *     с не-ASCII. Кириллица, эмодзи или случайный '=' в тексте приезжают битыми.
 *     Поэтому всё, что написал человек, кодируется в base64 из URL-безопасного
 *     алфавита без выравнивания (A-Za-z0-9-_): в воздухе только ASCII и ни
 *     одного '='.
 *
 * [decodeText] откатывается к сырой строке, если значение не похоже на base64 —
 * так мы остаёмся совместимы с наивным отправителем, шлющим открытый текст.
 */
object TxtRecordCodec {

    const val KEY_VERSION = "v"
    const val KEY_ID = "id"
    const val KEY_MESSAGE = "msg"
    const val KEY_NICK = "nick"
    const val KEY_TIMESTAMP = "ts"

    /**
     * Код прикреплённой страницы на сайте. В воздух уходит только код (5
     * символов), а не полный адрес: адрес у всех одинаковый и хранится в
     * настройках приложения, тратить на него дефицитные байты незачем.
     */
    const val KEY_LINK = "u"

    /**
     * Код личной ленты отправителя. На сайте нет ни каталога, ни поиска, так
     * что этот код — единственная дорога к чужой ленте, и приходит он только
     * тем, кто оказался в радиусе приёма.
     */
    const val KEY_PROFILE = "p"

    /** Логический канал: приёмник отбрасывает всё, что пришло не на его канале. */
    const val KEY_CHANNEL = "c"

    /**
     * Публичный ключ отправителя. Едет в каждой открытой передаче, поэтому
     * знакомство и обмен ключами — одно и то же событие: поймали человека в
     * эфире, значит уже можете ему написать. Сайт в этом не участвует, и
     * написать тому, с кем не пересекались, физически нельзя.
     */
    const val KEY_PUBKEY = "k"

    /** Тип записи: пусто — открытая передача, "dm" — личное сообщение. */
    const val KEY_TYPE = "t"
    const val TYPE_DIRECT = "dm"

    /** Короткий адрес получателя личного сообщения. */
    const val KEY_TO = "to"

    /** Запечатанный блок личного сообщения. */
    const val KEY_SEALED = "b"

    const val PROTOCOL_VERSION = "1"

    /** Запас на сообщение *до* base64, в байтах UTF-8. 140 -> 188 символов ASCII. */
    const val MAX_MESSAGE_BYTES = 140

    /** Позывной до 16 символов; кириллица занимает по два байта, отсюда 32. */
    const val MAX_NICK_BYTES = 32

    /**
     * Запас на личное сообщение до шифрования, в байтах UTF-8.
     *
     * Считалось так: строка TXT держит 255 байт, шифрование добавляет 28
     * (nonce и тег), а base64 растит всё в 4/3 раза. Формально влезло бы 161
     * байт, но вся запись целиком дробится на кадры действий P2P, и чем она
     * короче, тем надёжнее доезжает. 120 байт — это около шестидесяти букв
     * кириллицей, чего для записки хватает.
     */
    const val MAX_DIRECT_BYTES = 120

    private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE

    private val counter = AtomicInteger((System.nanoTime() and 0xFFF).toInt())

    /**
     * Короткий идентификатор: секунды эпохи в 36-ричной системе плюс счётчик.
     * Укладывается в 12 символов, что внутри TXT-записи существенно.
     */
    fun newId(): String {
        val seconds = System.currentTimeMillis() / 1000L
        val salt = counter.incrementAndGet() and 0xFFF
        return seconds.toString(36) + "-" + salt.toString(36)
    }

    fun encode(
        id: String,
        text: String,
        nick: String,
        timestampSeconds: Long,
        linkCode: String? = null,
        profileCode: String? = null,
        channel: Int = 1,
        publicKey: ByteArray? = null,
        maxMessageBytes: Int = MAX_MESSAGE_BYTES,
    ): Map<String, String> {
        val record = mutableMapOf(
            KEY_VERSION to PROTOCOL_VERSION,
            KEY_ID to id,
            KEY_MESSAGE to encodeText(clipToBytes(text, maxMessageBytes)),
            KEY_NICK to encodeText(clipToBytes(nick, MAX_NICK_BYTES)),
            KEY_TIMESTAMP to timestampSeconds.toString(36),
            KEY_CHANNEL to channel.toString(),
        )
        if (!linkCode.isNullOrBlank()) {
            // Коды уже ASCII, кодировать их в base64 смысла нет.
            record[KEY_LINK] = linkCode.trim()
        }
        if (!profileCode.isNullOrBlank()) {
            record[KEY_PROFILE] = profileCode.trim()
        }
        if (publicKey != null) {
            record[KEY_PUBKEY] = encodeBytes(publicKey)
        }
        return record
    }

    /**
     * Личное сообщение. Ни текста, ни позывного открытым текстом здесь нет:
     * посторонний видит только то, что кто-то кому-то написал.
     *
     * Публичный ключ отправителя вложен намеренно — получатель вычисляет по
     * нему сеансовый ключ, даже если видит этого человека впервые.
     */
    fun encodeDirect(
        id: String,
        sealed: ByteArray,
        recipientAddress: String,
        senderPublicKey: ByteArray,
        channel: Int,
        timestampSeconds: Long,
    ): Map<String, String> = mapOf(
        KEY_VERSION to PROTOCOL_VERSION,
        KEY_TYPE to TYPE_DIRECT,
        KEY_ID to id,
        KEY_TO to recipientAddress,
        KEY_SEALED to encodeBytes(sealed),
        KEY_PUBKEY to encodeBytes(senderPublicKey),
        KEY_CHANNEL to channel.toString(),
        KEY_TIMESTAMP to timestampSeconds.toString(36),
    )

    /** Разобранное личное сообщение до расшифровки. */
    data class DirectEnvelope(
        val id: String,
        val recipientAddress: String,
        val senderPublicKey: ByteArray,
        val sealed: ByteArray,
        val channel: Int,
        val sentAtEpochSeconds: Long,
    )

    fun isDirect(record: Map<String, String>): Boolean =
        record.lookup(KEY_TYPE)?.equals(TYPE_DIRECT, ignoreCase = true) == true

    /** @return конверт либо null, если запись не наша или повреждена. */
    fun decodeDirect(record: Map<String, String>, receivedAtMillis: Long): DirectEnvelope? {
        if (!isDirect(record)) return null

        val id = record.lookup(KEY_ID)?.takeIf { it.isNotBlank() } ?: return null
        val to = record.lookup(KEY_TO)?.takeIf { it.isNotBlank() } ?: return null
        val senderKey = record.lookup(KEY_PUBKEY)?.let { decodeBytes(it) } ?: return null
        if (senderKey.size != 32) return null
        val sealed = record.lookup(KEY_SEALED)?.let { decodeBytes(it) } ?: return null

        val channel = record.lookup(KEY_CHANNEL)?.toIntOrNull()?.takeIf { it in 1..16 } ?: 1
        val sentAt = record.lookup(KEY_TIMESTAMP)
            ?.let { runCatching { java.lang.Long.parseLong(it, 36) }.getOrNull() }
            ?: (receivedAtMillis / 1000L)

        return DirectEnvelope(id, to, senderKey, sealed, channel, sentAt)
    }

    /** Публичный ключ из открытой передачи; null, если отправитель его не прислал. */
    fun publicKeyFrom(record: Map<String, String>): ByteArray? =
        record.lookup(KEY_PUBKEY)?.let { decodeBytes(it) }?.takeIf { it.size == 32 }

    /**
     * Возвращает null, если запись не наша: в эфире полно чужих объявлений
     * `_presence._tcp`, и их нужно молча пропускать.
     */
    fun decode(
        record: Map<String, String>,
        sourceDeviceName: String?,
        receivedAtMillis: Long,
    ): EfirMessage? {
        // Личные сообщения разбирает decodeDirect: у них другой состав полей
        // и обязательная расшифровка.
        if (isDirect(record)) return null

        val id = record.lookup(KEY_ID)?.takeIf { it.isNotBlank() } ?: return null
        val rawMessage = record.lookup(KEY_MESSAGE) ?: return null
        val text = decodeText(rawMessage).trim()
        val link = record.lookup(KEY_LINK)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 16 }

        // Пустое сообщение имеет смысл, только если к нему приложена страница.
        if (text.isEmpty() && link == null) return null

        val nick = record.lookup(KEY_NICK)?.let { decodeText(it).trim() }.orEmpty()
        val profile = record.lookup(KEY_PROFILE)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 24 }
        val channel = record.lookup(KEY_CHANNEL)?.toIntOrNull()?.takeIf { it in 1..16 } ?: 1
        val sentAt = record.lookup(KEY_TIMESTAMP)
            ?.let { runCatching { java.lang.Long.parseLong(it, 36) }.getOrNull() }
            ?: (receivedAtMillis / 1000L)

        return EfirMessage(
            id = id,
            text = text,
            nick = nick.ifEmpty { "аноним" },
            linkCode = link,
            profileCode = profile,
            channel = channel,
            sentAtEpochSeconds = sentAt,
            receivedAtMillis = receivedAtMillis,
            origin = EfirMessage.Origin.REMOTE,
            sourceDeviceName = sourceDeviceName,
        )
    }

    /** По RFC 6763 ключи регистронезависимы, и прошивки этим пользуются по-разному. */
    private fun Map<String, String>.lookup(key: String): String? =
        this[key] ?: entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    private fun encodeText(value: String): String =
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), BASE64_FLAGS)

    fun encodeBytes(value: ByteArray): String = Base64.encodeToString(value, BASE64_FLAGS)

    private fun decodeBytes(value: String): ByteArray? = try {
        Base64.decode(value, BASE64_FLAGS)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun decodeText(value: String): String = try {
        val bytes = Base64.decode(value, BASE64_FLAGS)
        // Обратное кодирование отсекает обычные слова, которые случайно
        // оказались валидным base64 — например, «привет» латиницей.
        if (Base64.encodeToString(bytes, BASE64_FLAGS) == value) {
            String(bytes, Charsets.UTF_8)
        } else {
            value
        }
    } catch (e: IllegalArgumentException) {
        value
    }

    /** Обрезает по границе символа UTF-8, чтобы не разрубить многобайтовую букву. */
    fun clipToBytes(text: String, maxBytes: Int): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return text
        var end = maxBytes
        // Отступаем назад по продолжающим байтам (10xxxxxx).
        while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
        return String(bytes, 0, end, Charsets.UTF_8).trimEnd()
    }

    fun utf8Length(text: String): Int = text.toByteArray(Charsets.UTF_8).size
}
