package ru.radioinformator.efir.crypto

import com.google.crypto.tink.subtle.ChaCha20Poly1305
import com.google.crypto.tink.subtle.X25519
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрование личных сообщений.
 *
 * Задача узкая: адресат находится в радиусе Wi-Fi, соединения между
 * устройствами нет и не будет, а всё сообщение должно уместиться в TXT-запись
 * рядом со служебными полями. Отсюда выбор схемы.
 *
 * Ключи статические у обеих сторон (X25519 static-static): общий секрет
 * считается один раз на пару собеседников, и в эфир уходит только запечатанный
 * блок — 28 байт накладных расходов (nonce и тег) вместо 60, как было бы с
 * эфемерным ключом в каждой записке. При бюджете в 255 байт на строку TXT эта
 * разница решает, поместится ли осмысленный текст.
 *
 * Плата названа честно: **прямой секретности нет**. Кто раздобудет приватный
 * ключ, прочитает всю прошлую переписку, которую успел записать из эфира. Для
 * записок «жду у второго вагона» это приемлемо, для переписки, которую страшно
 * потерять, — нет, и в интерфейсе об этом сказано прямо.
 *
 * Приватный ключ нигде не хранится: он выводится из позывного и кодового
 * слова. Поэтому тот же позывной с тем же словом на новом телефоне даёт тот же
 * ключ и продолжает читать сообщения — ровно как и ленту. Обратная сторона та
 * же: слабое кодовое слово означает слабый ключ.
 *
 * В модуле сознательно нет ни одной ссылки на android.*, чтобы схему можно
 * было прогнать обычным unit-тестом на JVM.
 */
object EfirCrypto {

    const val PUBLIC_KEY_BYTES = 32
    const val PRIVATE_KEY_BYTES = 32

    /**
     * Сколько байт добавляется к открытому тексту: 12 на nonce и 16 на тег.
     * Nonce генерирует Tink и кладёт его в начало результата — своего мы не
     * задаём намеренно. Вывести nonce из идентификатора сообщения было бы на
     * 12 байт экономнее, но тогда любой повтор идентификатора ломает шифр
     * целиком, а случайный nonce такой ошибки не прощает молча.
     */
    const val OVERHEAD_BYTES = 28

    /** Длина адресного префикса в символах шестнадцатеричной записи. */
    const val ADDRESS_HEX_LENGTH = 8

    private const val KEY_INFO = "efir-x25519-v1"
    private const val SESSION_INFO = "efir-dm-key-v1"
    private const val ADDRESS_INFO = "efir-dm-address-v1"

    /**
     * Приватный ключ из позывного и кодового слова.
     *
     * Материал тот же, что и для входа на сайт, но метка другая — чтобы из
     * перехваченного ключа входа нельзя было получить ключ шифрования
     * и наоборот.
     */
    fun derivePrivateKey(handle: String, secret: String): ByteArray {
        val material = (handle.trim().lowercase() + ":" + secret).toByteArray(Charsets.UTF_8)
        val seed = sha256(material)
        val key = hkdf(seed, KEY_INFO.toByteArray(Charsets.UTF_8), PRIVATE_KEY_BYTES)
        return clamp(key)
    }

    fun publicKeyOf(privateKey: ByteArray): ByteArray = X25519.publicFromPrivate(privateKey)

    /**
     * Сеансовый ключ пары. Общий секрет напрямую как ключ не берём: из него
     * выводится ключ, причём в вывод замешаны оба публичных ключа в
     * фиксированном порядке — иначе A→B и B→A дали бы разные ключи.
     */
    fun sessionKey(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val shared = X25519.computeSharedSecret(privateKey, peerPublicKey)
        val mine = publicKeyOf(privateKey)

        // Порядок сторон должен совпасть у отправителя и получателя,
        // поэтому сортируем ключи, а не берём «мой, потом его».
        val (first, second) = if (compareBytes(mine, peerPublicKey) <= 0) {
            mine to peerPublicKey
        } else {
            peerPublicKey to mine
        }

        val info = SESSION_INFO.toByteArray(Charsets.UTF_8) + first + second
        return hkdf(shared, info, 32)
    }

    /** @return запечатанный блок: nonce, шифротекст и тег, всего +[OVERHEAD_BYTES]. */
    fun seal(sessionKey: ByteArray, plaintext: ByteArray): ByteArray =
        ChaCha20Poly1305(sessionKey).encrypt(plaintext, null)

    /**
     * @return открытый текст либо null, если сообщение адресовано не нам,
     *         испорчено в эфире или подделано. Отличать эти случаи незачем:
     *         реакция во всех одна — молча пропустить.
     */
    fun open(sessionKey: ByteArray, sealed: ByteArray): ByteArray? {
        if (sealed.size <= OVERHEAD_BYTES) return null
        return try {
            ChaCha20Poly1305(sessionKey).decrypt(sealed, null)
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    /**
     * Короткий адрес получателя: по нему приёмник решает, стоит ли вообще
     * пробовать расшифровать. Это подсказка, а не доказательство — последнее
     * слово всегда за успешной проверкой тега.
     *
     * Полный публичный ключ в записи не указываем: он длинный, а главное —
     * позволил бы кому угодно составлять список, кто кому пишет.
     */
    fun addressFor(peerPublicKey: ByteArray): String {
        val digest = hkdf(peerPublicKey, ADDRESS_INFO.toByteArray(Charsets.UTF_8), 4)
        return digest.joinToString("") { "%02x".format(it) }.take(ADDRESS_HEX_LENGTH)
    }

    // ------------------------------------------------------------------ примитивы

    private fun clamp(key: ByteArray): ByteArray {
        val out = key.copyOf()
        out[0] = (out[0].toInt() and 248).toByte()
        out[31] = (out[31].toInt() and 127).toByte()
        out[31] = (out[31].toInt() or 64).toByte()
        return out
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    /** HKDF по RFC 5869 с пустой солью. */
    private fun hkdf(input: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = hmac(ByteArray(32), input)
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            previous = hmac(prk, previous + info + byteArrayOf(counter.toByte()))
            val take = minOf(previous.size, length - offset)
            previous.copyInto(out, offset, 0, take)
            offset += take
            counter++
        }
        return out
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }
}
