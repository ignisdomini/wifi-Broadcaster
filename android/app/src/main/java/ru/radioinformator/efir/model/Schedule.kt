package ru.radioinformator.efir.model

import java.util.Calendar

/**
 * Правило планировщика: что вещать и когда.
 *
 * Сделано для тех, кто вещает по расписанию, а не по случаю: кафе с завтраком
 * до одиннадцати и обедом до трёх, рынок, который работает по выходным,
 * распродажа на три дня. Держать телефон в руках и менять текст руками они не
 * будут.
 *
 * Правило срабатывает, когда совпало всё сразу: сегодняшний день недели
 * разрешён, текущее время попало в промежуток, и сегодняшняя дата не вышла
 * за границы периода. Пустые границы означают «всегда».
 */
data class ScheduleRule(
    val id: String,
    /** Текст, который уходит в эфир. */
    val text: String,
    val channel: Int,

    /** Минуты от полуночи. Начало может быть больше конца — тогда через ночь. */
    val fromMinutes: Int,
    val toMinutes: Int,

    /**
     * Дни недели по [Calendar.DAY_OF_WEEK] (1 — воскресенье). Пусто — любой день.
     */
    val weekDays: Set<Int> = emptySet(),

    /** Границы периода в миллисекундах; 0 — без границы. */
    val fromDateMillis: Long = 0L,
    val toDateMillis: Long = 0L,

    val enabled: Boolean = true,
) {
    /** Подходит ли правило к моменту [nowMillis]. */
    fun matches(nowMillis: Long): Boolean {
        if (!enabled || text.isBlank()) return false

        if (fromDateMillis > 0 && nowMillis < fromDateMillis) return false
        // Верхняя граница включает весь последний день.
        if (toDateMillis > 0 && nowMillis > toDateMillis + DAY_MILLIS - 1) return false

        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }

        if (weekDays.isNotEmpty() && calendar.get(Calendar.DAY_OF_WEEK) !in weekDays) {
            return false
        }

        val minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return if (fromMinutes <= toMinutes) {
            minutes >= fromMinutes && minutes <= toMinutes
        } else {
            // Промежуток через полночь: с 22:00 до 02:00.
            minutes >= fromMinutes || minutes <= toMinutes
        }
    }

    val timeLabel: String
        get() = formatMinutes(fromMinutes) + "–" + formatMinutes(toMinutes)

    val daysLabel: String
        get() {
            if (weekDays.isEmpty()) return "каждый день"
            return DAY_ORDER
                .filter { it in weekDays }
                .joinToString(", ") { DAY_NAMES[it] ?: "?" }
        }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** Порядок с понедельника: в календаре неделя начинается с воскресенья. */
        val DAY_ORDER = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
        )

        val DAY_NAMES = mapOf(
            Calendar.MONDAY to "пн",
            Calendar.TUESDAY to "вт",
            Calendar.WEDNESDAY to "ср",
            Calendar.THURSDAY to "чт",
            Calendar.FRIDAY to "пт",
            Calendar.SATURDAY to "сб",
            Calendar.SUNDAY to "вс",
        )

        fun formatMinutes(minutes: Int): String {
            val safe = ((minutes % 1440) + 1440) % 1440
            return "%02d:%02d".format(safe / 60, safe % 60)
        }

        fun newId(): String =
            System.currentTimeMillis().toString(36) + "-" + (0..999).random().toString(36)
    }
}

/** Состояние планировщика для интерфейса. */
data class ScheduleState(
    val rules: List<ScheduleRule> = emptyList(),
    /** Планировщик включён: правила сами меняют то, что висит в эфире. */
    val enabled: Boolean = false,
    /** Идентификатор правила, которое сейчас в эфире. */
    val activeRuleId: String? = null,
)
