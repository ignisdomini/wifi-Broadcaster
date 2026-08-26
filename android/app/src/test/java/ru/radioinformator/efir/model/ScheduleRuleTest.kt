package ru.radioinformator.efir.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Проверки планировщика.
 *
 * Самое хрупкое здесь — промежуток через полночь и включение последнего дня
 * периода: и то и другое легко сломать, а заметить это можно только вечером
 * или в последний день распродажи.
 */
class ScheduleRuleTest {

    private fun at(
        year: Int, month: Int, day: Int, hour: Int, minute: Int,
    ): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, minute)
    }.timeInMillis

    private fun rule(
        from: Int,
        to: Int,
        days: Set<Int> = emptySet(),
        fromDate: Long = 0L,
        toDate: Long = 0L,
        enabled: Boolean = true,
        text: String = "обед до трёх",
    ) = ScheduleRule(
        id = "r1", text = text, channel = 2,
        fromMinutes = from, toMinutes = to, weekDays = days,
        fromDateMillis = fromDate, toDateMillis = toDate, enabled = enabled,
    )

    @Test
    fun `внутри обычного промежутка правило срабатывает`() {
        val r = rule(from = 11 * 60, to = 15 * 60)
        assertTrue(r.matches(at(2026, Calendar.AUGUST, 8, 12, 30)))
    }

    @Test
    fun `до начала и после конца не срабатывает`() {
        val r = rule(from = 11 * 60, to = 15 * 60)
        assertFalse(r.matches(at(2026, Calendar.AUGUST, 8, 10, 59)))
        assertFalse(r.matches(at(2026, Calendar.AUGUST, 8, 15, 1)))
    }

    @Test
    fun `границы промежутка включены`() {
        val r = rule(from = 11 * 60, to = 15 * 60)
        assertTrue(r.matches(at(2026, Calendar.AUGUST, 8, 11, 0)))
        assertTrue(r.matches(at(2026, Calendar.AUGUST, 8, 15, 0)))
    }

    @Test
    fun `промежуток через полночь захватывает обе стороны суток`() {
        val r = rule(from = 22 * 60, to = 2 * 60)
        assertTrue(r.matches(at(2026, Calendar.AUGUST, 8, 23, 30)))
        assertTrue(r.matches(at(2026, Calendar.AUGUST, 8, 1, 15)))
        assertFalse(r.matches(at(2026, Calendar.AUGUST, 8, 12, 0)))
    }

    @Test
    fun `день недели отсекает лишнее`() {
        // 8 августа 2026 — суббота.
        val onlyWeekend = rule(from = 0, to = 23 * 60 + 59, days = setOf(Calendar.SATURDAY))
        assertTrue(onlyWeekend.matches(at(2026, Calendar.AUGUST, 8, 12, 0)))
        assertFalse(onlyWeekend.matches(at(2026, Calendar.AUGUST, 10, 12, 0)))
    }

    @Test
    fun `пустой список дней означает каждый день`() {
        val r = rule(from = 0, to = 23 * 60 + 59)
        assertTrue(r.matches(at(2026, Calendar.AUGUST, 10, 12, 0)))
        assertEquals("каждый день", r.daysLabel)
    }

    @Test
    fun `последний день периода входит целиком`() {
        val r = rule(
            from = 0, to = 23 * 60 + 59,
            fromDate = at(2026, Calendar.AUGUST, 7, 0, 0),
            toDate = at(2026, Calendar.AUGUST, 9, 0, 0),
        )
        assertTrue(r.matches(at(2026, Calendar.AUGUST, 9, 23, 0)))
        assertFalse(r.matches(at(2026, Calendar.AUGUST, 10, 0, 30)))
        assertFalse(r.matches(at(2026, Calendar.AUGUST, 6, 23, 30)))
    }

    @Test
    fun `выключенное и пустое правило никогда не срабатывает`() {
        val now = at(2026, Calendar.AUGUST, 8, 12, 0)
        assertFalse(rule(from = 0, to = 1439, enabled = false).matches(now))
        assertFalse(rule(from = 0, to = 1439, text = "   ").matches(now))
    }

    @Test
    fun `подписи времени и дней читаемы`() {
        val r = rule(from = 9 * 60 + 5, to = 18 * 60, days = setOf(Calendar.MONDAY, Calendar.FRIDAY))
        assertEquals("09:05–18:00", r.timeLabel)
        assertEquals("пн, пт", r.daysLabel)
    }
}
