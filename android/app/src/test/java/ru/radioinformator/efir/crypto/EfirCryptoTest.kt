package ru.radioinformator.efir.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Проверяется не библиотека, а наша схема поверх неё: вывод ключа из кодового
 * слова, совпадение сеансового ключа у обеих сторон и поведение при чужом
 * ключе или испорченных данных.
 */
class EfirCryptoTest {

    private val kostyaKey = EfirCrypto.derivePrivateKey("Костя", "тихий омут")
    private val lidaKey = EfirCrypto.derivePrivateKey("Лида", "зелёный рюкзак")

    @Test
    fun `ключ выводится одинаково при каждом вызове`() {
        val again = EfirCrypto.derivePrivateKey("Костя", "тихий омут")
        assertArrayEquals(kostyaKey, again)
    }

    @Test
    fun `позывной нечувствителен к регистру, кодовое слово чувствительно`() {
        assertArrayEquals(
            kostyaKey,
            EfirCrypto.derivePrivateKey("КОСТЯ", "тихий омут"),
        )
        assertNotEquals(
            kostyaKey.toList(),
            EfirCrypto.derivePrivateKey("Костя", "Тихий омут").toList(),
        )
    }

    @Test
    fun `у разных людей разные ключи`() {
        assertNotEquals(kostyaKey.toList(), lidaKey.toList())
    }

    @Test
    fun `ключ имеет правильную длину и обработан по правилам X25519`() {
        assertEquals(EfirCrypto.PRIVATE_KEY_BYTES, kostyaKey.size)
        // Требования curve25519 к скаляру: младшие три бита обнулены,
        // старший бит сброшен, следующий за ним установлен.
        assertEquals(0, kostyaKey[0].toInt() and 7)
        assertEquals(0, kostyaKey[31].toInt() and 0x80)
        assertEquals(0x40, kostyaKey[31].toInt() and 0x40)
    }

    @Test
    fun `обе стороны приходят к одному сеансовому ключу`() {
        val kostyaPub = EfirCrypto.publicKeyOf(kostyaKey)
        val lidaPub = EfirCrypto.publicKeyOf(lidaKey)

        val fromKostya = EfirCrypto.sessionKey(kostyaKey, lidaPub)
        val fromLida = EfirCrypto.sessionKey(lidaKey, kostyaPub)

        assertArrayEquals(fromKostya, fromLida)
    }

    @Test
    fun `сообщение доходит до адресата`() {
        val lidaPub = EfirCrypto.publicKeyOf(lidaKey)
        val kostyaPub = EfirCrypto.publicKeyOf(kostyaKey)
        val text = "Жду у второго вагона, не задерживайся"

        val sealed = EfirCrypto.seal(
            EfirCrypto.sessionKey(kostyaKey, lidaPub),
            text.toByteArray(Charsets.UTF_8),
        )
        val opened = EfirCrypto.open(EfirCrypto.sessionKey(lidaKey, kostyaPub), sealed)

        assertEquals(text, String(opened!!, Charsets.UTF_8))
    }

    @Test
    fun `посторонний не прочитает`() {
        val lidaPub = EfirCrypto.publicKeyOf(lidaKey)
        val kostyaPub = EfirCrypto.publicKeyOf(kostyaKey)
        val chuzhoy = EfirCrypto.derivePrivateKey("Антенна", "чужое слово")

        val sealed = EfirCrypto.seal(
            EfirCrypto.sessionKey(kostyaKey, lidaPub),
            "Только для Лиды".toByteArray(Charsets.UTF_8),
        )

        assertNull(EfirCrypto.open(EfirCrypto.sessionKey(chuzhoy, kostyaPub), sealed))
    }

    @Test
    fun `испорченный в эфире блок отбраковывается`() {
        val lidaPub = EfirCrypto.publicKeyOf(lidaKey)
        val kostyaPub = EfirCrypto.publicKeyOf(kostyaKey)
        val session = EfirCrypto.sessionKey(kostyaKey, lidaPub)

        val sealed = EfirCrypto.seal(session, "Целое сообщение".toByteArray(Charsets.UTF_8))
        sealed[sealed.size - 1] = (sealed[sealed.size - 1] + 1).toByte()

        assertNull(EfirCrypto.open(EfirCrypto.sessionKey(lidaKey, kostyaPub), sealed))
    }

    @Test
    fun `слишком короткий блок не разбирается`() {
        val session = EfirCrypto.sessionKey(kostyaKey, EfirCrypto.publicKeyOf(lidaKey))
        assertNull(EfirCrypto.open(session, ByteArray(EfirCrypto.OVERHEAD_BYTES)))
    }

    @Test
    fun `накладные расходы ровно те, на которые рассчитан бюджет записи`() {
        val session = EfirCrypto.sessionKey(kostyaKey, EfirCrypto.publicKeyOf(lidaKey))
        val plaintext = ByteArray(64)
        val sealed = EfirCrypto.seal(session, plaintext)
        assertEquals(plaintext.size + EfirCrypto.OVERHEAD_BYTES, sealed.size)
    }

    @Test
    fun `один и тот же текст каждый раз выглядит по-разному`() {
        val session = EfirCrypto.sessionKey(kostyaKey, EfirCrypto.publicKeyOf(lidaKey))
        val text = "Одинаковый текст".toByteArray(Charsets.UTF_8)
        assertNotEquals(
            EfirCrypto.seal(session, text).toList(),
            EfirCrypto.seal(session, text).toList(),
        )
    }

    @Test
    fun `адрес короткий, стабильный и разный у разных людей`() {
        val lidaPub = EfirCrypto.publicKeyOf(lidaKey)
        val kostyaPub = EfirCrypto.publicKeyOf(kostyaKey)

        val address = EfirCrypto.addressFor(lidaPub)
        assertEquals(EfirCrypto.ADDRESS_HEX_LENGTH, address.length)
        assertEquals(address, EfirCrypto.addressFor(lidaPub))
        assertNotEquals(address, EfirCrypto.addressFor(kostyaPub))
        assertTrue(address.all { it.isDigit() || it in 'a'..'f' })
    }
}
