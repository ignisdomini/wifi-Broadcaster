package ru.radioinformator.efir.net

import java.security.MessageDigest
import java.util.Locale

/**
 * Учётная запись НА ЭФИРЕ: позывной и кодовое слово, больше ничего.
 *
 * Кодовое слово не покидает телефон. На сервер уходит только
 * `sha256(позывной в нижнем регистре + ':' + кодовое слово)`, и уже из него
 * сервер делает свой медленный хеш. Поэтому:
 *
 *  - сервер никогда не видел кодового слова и не может его показать;
 *  - тот же позывной с тем же кодовым словом на другом телефоне открывает ту
 *    же ленту — это и есть весь механизм «входа»;
 *  - забытое кодовое слово означает потерянную ленту. Восстанавливать нечем:
 *    ни почты, ни телефона у нас не спрашивают, и в этом суть.
 */
data class Identity(
    val handle: String,
    /** sha256(позывной:кодовое слово) в шестнадцатеричном виде. */
    val authKey: String,
    /** Код личной ленты, выданный сайтом. Он же едет в эфир. */
    val profileCode: String,
) {
    val isRegistered: Boolean get() = handle.isNotBlank() && profileCode.isNotBlank()

    companion object {
        const val HANDLE_MIN = 2
        const val HANDLE_MAX = 16
        const val SECRET_MIN = 4

        /**
         * Позывной в нижнем регистре — чтобы «Костя» и «костя» были одним
         * человеком, и ключ у них совпадал.
         */
        fun authKey(handle: String, secret: String): String {
            val material = handle.trim().lowercase(Locale.ROOT) + ":" + secret
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(material.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /** @return текст ошибки или null, если позывной годится. */
        fun validateHandle(handle: String): String? {
            val value = handle.trim()
            if (value.length < HANDLE_MIN || value.length > HANDLE_MAX) {
                return "Позывной — от $HANDLE_MIN до $HANDLE_MAX символов"
            }
            // Тот же набор, что проверяет сайт: буквы, цифры, точка, дефис,
            // подчёркивание. Пробелов нет — позывной мелькает в служебных строках.
            if (!value.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) {
                return "Можно буквы, цифры, точку, дефис и подчёркивание"
            }
            return null
        }

        fun validateSecret(secret: String): String? {
            if (secret.length < SECRET_MIN) {
                return "Кодовое слово — не короче $SECRET_MIN символов"
            }
            return null
        }
    }
}
