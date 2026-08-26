package ru.radioinformator.efir.net

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.radioinformator.efir.model.ContactCard
import ru.radioinformator.efir.model.FeedEntry
import ru.radioinformator.efir.model.SiteLimits
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Клиент сайта НА ЭФИРЕ.
 *
 * Сознательно на HttpURLConnection: три коротких запроса за весь жизненный
 * цикл приложения не стоят ни лишней зависимости в APK, ни ещё одного пула
 * потоков. Наружу уходит только текст — файлы приложение не отправляет вовсе,
 * поэтому и доступ к хранилищу ему не нужен.
 */
class EfirApi {

    data class SiteInfo(
        val name: String,
        val limits: SiteLimits,
        val requiresToken: Boolean,
        val channelMin: Int,
        val channelMax: Int,
        /** Темы каналов в порядке номеров; пусто, если сеть их не прислала. */
        val channelTitles: List<String>,
    )

    data class UploadResult(
        val code: String,
        val url: String,
        val moderation: Boolean,
        /** Попала ли передача в личную ленту — то есть узнал ли сайт отправителя. */
        val inFeed: Boolean,
    )

    data class RegisterResult(
        val handle: String,
        val profileCode: String,
        /** true — позывной только что занят, false — вход в существующий. */
        val created: Boolean,
        val postsCount: Int,
    )

    /** Сайт задаёт лимиты сам — приложение только спрашивает и подчиняется. */
    suspend fun fetchLimits(baseUrl: String): Result<SiteInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson("$baseUrl/api/limits.php")
            if (!json.optBoolean("ok")) {
                throw IOException(json.optString("error", "сайт ответил отказом"))
            }
            val limits = json.getJSONObject("limits")
            SiteInfo(
                name = json.optJSONObject("site")?.optString("name").orEmpty().ifEmpty { "ЭФИР" },
                limits = SiteLimits(
                    maxTextChars = limits.optInt("max_text_chars", 2000),
                    broadcastMaxBytes = limits.optInt("broadcast_max_bytes", 140),
                ),
                requiresToken = json.optBoolean("require_token", false),
                channelMin = json.optJSONObject("channels")?.optInt("min", 1) ?: 1,
                channelMax = json.optJSONObject("channels")?.optInt("max", 16) ?: 16,
                channelTitles = json.optJSONObject("channels")
                    ?.optJSONArray("list")
                    ?.let { list ->
                        (0 until list.length()).mapNotNull {
                            list.optJSONObject(it)?.optString("title")?.takeIf { t -> t.isNotBlank() }
                        }
                    }
                    .orEmpty(),
            )
        }.onFailure { Log.w(TAG, "Не удалось получить лимиты с $baseUrl", it) }
    }

    /**
     * Регистрация и вход — одно и то же обращение.
     *
     * Свободный позывной занимается, занятый пускает при верном кодовом слове.
     * Так лента возвращается на новом телефоне без всякого «восстановления
     * доступа», которого при полной анонимности взять неоткуда.
     */
    suspend fun register(
        baseUrl: String,
        token: String,
        handle: String,
        authKey: String,
    ): Result<RegisterResult> = withContext(Dispatchers.IO) {
        runCatching {
            val boundary = "----efir" + System.currentTimeMillis().toString(16)
            val connection = (URL("$baseUrl/api/register.php").openConnection() as HttpURLConnection)
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
                useCaches = false
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
                if (token.isNotBlank()) setRequestProperty("X-Efir-Token", token)
            }

            try {
                DataOutputStream(BufferedOutputStream(connection.outputStream)).use { out ->
                    // Позывной идёт полем формы, а не заголовком: он кириллический,
                    // а не-ASCII в HTTP-заголовке не выживает.
                    out.writeField(boundary, "handle", handle)
                    out.writeField(boundary, "auth_key", authKey)
                    out.writeBytes("--$boundary--\r\n")
                    out.flush()
                }

                val json = readJson(connection)
                if (!json.optBoolean("ok")) {
                    throw IOException(json.optString("error", "сайт не принял позывной"))
                }

                RegisterResult(
                    handle = json.optString("handle", handle),
                    profileCode = json.getString("profile_code"),
                    created = json.optBoolean("created", false),
                    postsCount = json.optInt("posts_count", 0),
                )
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "Регистрация на $baseUrl не удалась", it) }
    }

    /**
     * Отправляет вложение и возвращает короткий код страницы.
     * Картинки ужимаются здесь же — в сеть уходит уже готовый JPEG.
     */
    suspend fun upload(
        baseUrl: String,
        token: String,
        authKey: String,
        broadcast: String,
        text: String,
        nick: String,
        channel: Int,
    ): Result<UploadResult> = withContext(Dispatchers.IO) {
        runCatching {
            val boundary = "----efir" + System.currentTimeMillis().toString(16)
            val connection = (URL("$baseUrl/api/upload.php").openConnection() as HttpURLConnection)

            connection.apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 60_000
                useCaches = false
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
                if (token.isNotBlank()) {
                    setRequestProperty("X-Efir-Token", token)
                }
                // Позывной в заголовок не кладём — он кириллический. Сайт находит
                // владельца по одному ключу, а имя берёт из своей базы.
                if (authKey.isNotBlank()) {
                    setRequestProperty("X-Efir-Auth", authKey)
                }
            }

            try {
                DataOutputStream(BufferedOutputStream(connection.outputStream)).use { out ->
                    out.writeField(boundary, "broadcast", broadcast)
                    out.writeField(boundary, "text", text)
                    out.writeField(boundary, "nick", nick)
                    out.writeField(boundary, "channel", channel.toString())
                    out.writeBytes("--$boundary--\r\n")
                    out.flush()
                }

                val json = readJson(connection)
                if (!json.optBoolean("ok")) {
                    // Сайт присылает человеческий текст ошибки — показываем его как есть.
                    throw IOException(json.optString("error", "загрузка не удалась"))
                }

                UploadResult(
                    code = json.getString("code"),
                    url = json.optString("url"),
                    moderation = json.optBoolean("moderation", false),
                    inFeed = json.optBoolean("in_feed", false),
                )
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "Загрузка на $baseUrl не удалась", it) }
    }

    /** Своя лента: записи владельца ключа, включая скрытые модерацией. */
    suspend fun myFeed(baseUrl: String, authKey: String): Result<List<FeedEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL("$baseUrl/api/mine.php").openConnection() as HttpURLConnection)
                    .apply {
                        requestMethod = "GET"
                        connectTimeout = 10_000
                        readTimeout = 20_000
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("X-Efir-Auth", authKey)
                    }
                try {
                    val json = readJson(connection)
                    if (!json.optBoolean("ok")) {
                        throw IOException(json.optString("error", "лента не отдалась"))
                    }
                    val array = json.optJSONArray("posts") ?: return@runCatching emptyList()
                    (0 until array.length()).mapNotNull { index ->
                        val item = array.optJSONObject(index) ?: return@mapNotNull null
                        FeedEntry(
                            code = item.optString("code"),
                            broadcast = item.optString("broadcast"),
                            excerpt = item.optString("excerpt"),
                            channel = item.optInt("channel", 1),
                            createdHuman = item.optString("created_human"),
                            url = item.optString("url"),
                            hidden = item.optBoolean("hidden", false),
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure { Log.w(TAG, "Не удалось получить свою ленту", it) }
        }

    /** Визитка со страницы своей ленты — то, что сеть о вас показывает. */
    suspend fun loadContacts(baseUrl: String, authKey: String): Result<ContactCard> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL("$baseUrl/api/profile.php").openConnection() as HttpURLConnection)
                    .apply {
                        requestMethod = "GET"
                        connectTimeout = 10_000
                        readTimeout = 20_000
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("X-Efir-Auth", authKey)
                    }
                try {
                    val json = readJson(connection)
                    if (!json.optBoolean("ok")) {
                        throw IOException(json.optString("error", "визитка не отдалась"))
                    }
                    json.toContactCard()
                } finally {
                    connection.disconnect()
                }
            }.onFailure { Log.w(TAG, "Не удалось получить визитку", it) }
        }

    /**
     * Сохраняет визитку. Пустые поля уходят тоже — так стирается лишнее:
     * не приславшее поле сеть считает пустым.
     */
    suspend fun saveContacts(
        baseUrl: String,
        authKey: String,
        card: ContactCard,
    ): Result<ContactCard> = withContext(Dispatchers.IO) {
        runCatching {
            val boundary = "----efir" + System.currentTimeMillis().toString(16)
            val connection = (URL("$baseUrl/api/profile.php").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    useCaches = false
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Efir-Auth", authKey)
                }
            try {
                DataOutputStream(BufferedOutputStream(connection.outputStream)).use { out ->
                    out.writeField(boundary, "contact_name", card.name)
                    out.writeField(boundary, "contact_phone", card.phone)
                    out.writeField(boundary, "contact_email", card.email)
                    out.writeField(boundary, "contact_site", card.site)
                    out.writeField(boundary, "contact_telegram", card.telegram)
                    out.writeField(boundary, "contact_vk", card.vk)
                    out.writeField(boundary, "contact_social", card.social)
                    out.writeField(boundary, "public", if (card.public) "1" else "0")
                    out.writeBytes("--$boundary--\r\n")
                    out.flush()
                }
                val json = readJson(connection)
                if (!json.optBoolean("ok")) {
                    throw IOException(json.optString("error", "визитка не сохранилась"))
                }
                json.toContactCard()
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "Не удалось сохранить визитку", it) }
    }

    /** Ответы чтения и сохранения устроены одинаково — разбор общий. */
    private fun JSONObject.toContactCard(): ContactCard {
        val contacts = optJSONObject("contacts") ?: JSONObject()
        return ContactCard(
            name = contacts.optString("contact_name"),
            phone = contacts.optString("contact_phone"),
            email = contacts.optString("contact_email"),
            site = contacts.optString("contact_site"),
            telegram = contacts.optString("contact_telegram"),
            vk = contacts.optString("contact_vk"),
            social = contacts.optString("contact_social"),
            public = optBoolean("public", true),
        )
    }

    /** Удаляет свою запись. Чужую сеть не отдаст даже с верным ключом. */
    suspend fun deletePost(
        baseUrl: String,
        authKey: String,
        code: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val boundary = "----efir" + System.currentTimeMillis().toString(16)
            val connection = (URL("$baseUrl/api/delete.php").openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    useCaches = false
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Efir-Auth", authKey)
                }
            try {
                DataOutputStream(BufferedOutputStream(connection.outputStream)).use { out ->
                    out.writeField(boundary, "code", code)
                    out.writeBytes("--$boundary--\r\n")
                    out.flush()
                }
                val json = readJson(connection)
                if (!json.optBoolean("ok")) {
                    throw IOException(json.optString("error", "не удалось удалить"))
                }
            } finally {
                connection.disconnect()
            }
        }.onFailure { Log.w(TAG, "Удаление записи $code не удалось", it) }
    }

    // ------------------------------------------------------------------ низкий уровень

    /** Тело ответа читается и при ошибке: там лежит человеческий текст причины. */
    private fun readJson(connection: HttpURLConnection): JSONObject {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.readText().orEmpty()
        return runCatching { JSONObject(body) }.getOrElse {
            throw IOException("Сайт ответил не по-нашему (код $code)")
        }
    }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.readText().orEmpty()
            return runCatching { JSONObject(body) }.getOrElse {
                throw IOException("Ответ не похож на JSON (код $code)")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun DataOutputStream.writeField(boundary: String, name: String, value: String) {
        writeBytes("--$boundary\r\n")
        // Имя поля — ASCII, а значение может быть кириллицей, поэтому пишем
        // его байтами UTF-8, а не через writeBytes (тот режет до одного байта).
        writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n")
        writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
        write(value.toByteArray(StandardCharsets.UTF_8))
        writeBytes("\r\n")
    }

    companion object {
        private const val TAG = "Efir/Api"
    }
}
