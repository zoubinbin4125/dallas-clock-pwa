package com.binbin.dallasclock

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * Dallas Clock core, ported from clock.js (Mac app ClockCore.swift v2.2.0).
 *
 * Timezone: America/Chicago. Work hours: Mon–Fri 08:30–17:30 Dallas time.
 * Federal holidays are displayed and do not by themselves pause the countdown.
 *
 * Keep this file in lockstep with clock.js. android unit tests compare snapshots
 * against fixtures exported from that file.
 */
object ClockCore {
    const val DALLAS_TZ: String = "America/Chicago"
    const val SHANGHAI_TZ: String = "Asia/Shanghai"

    private val DALLAS: ZoneId = ZoneId.of(DALLAS_TZ)
    private val SHANGHAI: ZoneId = ZoneId.of(SHANGHAI_TZ)

    private val MONTHS_EN = arrayOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
    )
    private val WEEKDAYS_EN = arrayOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
    )
    private val WEEKDAYS_CN = arrayOf(
        "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六",
    )

    data class Holiday(
        val year: Int,
        val month: Int,
        val day: Int,
        val nameCN: String,
        val nameEN: String,
    )

    data class Hms(
        val hours: Long,
        val minutes: Long,
        val seconds: Long,
        val text: String,
    )

    data class Snapshot(
        val time: String,
        val timeWithSeconds: String,
        val periodCN: String,
        val periodEN: String,
        val dateCN: String,
        val dateEN: String,
        val weekdayCN: String,
        val weekdayEN: String,
        val zone: String,
        val chinaOffsetHours: Int,
        val holidayCN: String?,
        val holidayEN: String?,
        val isWeekend: Boolean,
        val upcomingHolidayCN: String,
        val upcomingHolidayEN: String,
        val daysUntilHoliday: Int,
        val isWorking: Boolean,
        val isWeekday: Boolean,
        val phase: String,
        val workMinutesRemaining: Long,
        val remainingSeconds: Long,
        /** Exact milliseconds until the work target. Used by the home-screen chronometer. */
        val remainingMillis: Long,
        val workCountdownCN: String,
        val workCountdownEN: String,
    )

    /** Sunday = 1 … Saturday = 7, matching Foundation / clock.js weekdayIndex. */
    fun weekdayIndex(year: Int, month: Int, day: Int): Int {
        val iso = LocalDate.of(year, month, day).dayOfWeek.value // Mon=1 … Sun=7
        return if (iso == 7) 1 else iso + 1
    }

    fun holidaysInYear(year: Int): List<Holiday> {
        val actual = ArrayList<Holiday>(12)
        fun fixed(month: Int, day: Int, nameCN: String, nameEN: String) {
            actual.add(Holiday(year, month, day, nameCN, nameEN))
        }
        fun nth(target: Int, ordinal: Int, month: Int, nameCN: String, nameEN: String) {
            val ymd = nthWeekday(target, ordinal, month, year)
            actual.add(Holiday(ymd.year, ymd.month, ymd.day, nameCN, nameEN))
        }

        fixed(1, 1, "元旦", "New Year's Day")
        nth(2, 3, 2, "华盛顿诞辰日", "Washington's Birthday")
        val memorial = lastMonday(5, year)
        actual.add(Holiday(memorial.year, memorial.month, memorial.day, "阵亡将士纪念日", "Memorial Day"))
        fixed(7, 4, "独立日", "Independence Day")
        nth(2, 1, 9, "劳动节", "Labor Day")
        nth(2, 2, 10, "哥伦布日", "Columbus Day")
        fixed(11, 11, "退伍军人节", "Veterans Day")
        nth(5, 4, 11, "感恩节", "Thanksgiving Day")
        fixed(12, 25, "圣诞节", "Christmas Day")
        if (year >= 1986) {
            nth(2, 3, 1, "马丁·路德·金纪念日", "Birthday of Martin Luther King, Jr.")
        }
        if (year >= 2021) {
            fixed(6, 19, "六月节", "Juneteenth National Independence Day")
        }

        val observed = ArrayList<Holiday>()
        for (holiday in actual) {
            val shift = when (weekdayIndex(holiday.year, holiday.month, holiday.day)) {
                7 -> -1 // Saturday -> preceding Friday
                1 -> 1 // Sunday -> following Monday
                else -> 0
            }
            if (shift == 0) continue
            val shifted = addDays(holiday.year, holiday.month, holiday.day, shift)
            observed.add(
                Holiday(
                    shifted.year,
                    shifted.month,
                    shifted.day,
                    holiday.nameCN + "（调休）",
                    holiday.nameEN + " (observed)",
                ),
            )
        }
        return actual + observed
    }

    fun workCountdownCN(isWorking: Boolean, workMinutesRemaining: Long): String {
        val hours = workMinutesRemaining / 60
        val minutes = pad2(workMinutesRemaining % 60)
        return "距${if (isWorking) "下班" else "上班"} ${hours}小时${minutes}分"
    }

    fun workCountdownEN(isWorking: Boolean, workMinutesRemaining: Long): String {
        val hours = workMinutesRemaining / 60
        val minutes = pad2(workMinutesRemaining % 60)
        return "Work ${if (isWorking) "ends" else "starts"} in ${hours}h ${minutes}m"
    }

    fun snapshot(now: Instant = Instant.now()): Snapshot {
        val zoned = now.atZone(DALLAS)
        val year = zoned.year
        val month = zoned.monthValue
        val day = zoned.dayOfMonth
        val hour = zoned.hour
        val minute = zoned.minute
        val second = zoned.second
        val weekday = weekdayIndex(year, month, day)

        val holidays = allHolidays(year)
        val todayKey = civilKey(year, month, day)
        val current = holidays.firstOrNull { civilKey(it.year, it.month, it.day) == todayKey }
        val upcoming = holidays.firstOrNull { isAfter(it, year, month, day) }
        val daysUntilHoliday = if (upcoming == null) {
            0
        } else {
            calendarDaysBetween(year, month, day, upcoming.year, upcoming.month, upcoming.day)
        }

        val start = dallasLocalInstant(year, month, day, 8, 30, 0)
        val end = dallasLocalInstant(year, month, day, 17, 30, 0)
        val isWeekday = weekday in 2..6
        val isWorking = isWeekday && !now.isBefore(start) && now.isBefore(end)

        val workTarget: Instant = when {
            isWorking -> end
            isWeekday && now.isBefore(start) -> start
            else -> {
                var next = addDays(year, month, day, 1)
                while (weekdayIndex(next.year, next.month, next.day) !in 2..6) {
                    next = addDays(next.year, next.month, next.day, 1)
                }
                dallasLocalInstant(next.year, next.month, next.day, 8, 30, 0)
            }
        }

        val remainingMs = workTarget.toEpochMilli() - now.toEpochMilli()
        val workMinutesRemaining = jsCeilDiv(remainingMs, 60_000L)
        val remainingSeconds = maxOf(0L, jsCeilDiv(remainingMs, 1_000L))

        val dallasOff = DALLAS.rules.getOffset(now).totalSeconds
        val shanghaiOff = SHANGHAI.rules.getOffset(now).totalSeconds
        val chinaOffsetHours = ((dallasOff - shanghaiOff) / 3600.0).roundToInt()

        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        val zone = if (dallasOff == -5 * 3600) "CDT" else "CST"
        val isWeekend = weekday == 1 || weekday == 7
        val phase = when {
            isWorking -> "working"
            isWeekend -> "weekend"
            isWeekday && now.isBefore(start) -> "before"
            else -> "after"
        }

        return Snapshot(
            time = "$hour12:${pad2(minute)}",
            timeWithSeconds = "$hour12:${pad2(minute)}:${pad2(second)}",
            periodCN = if (hour < 12) "上午" else "下午",
            periodEN = if (hour < 12) "AM" else "PM",
            dateCN = "${year}年${month}月${day}日",
            dateEN = "${MONTHS_EN[month - 1]} $day, $year",
            weekdayCN = WEEKDAYS_CN[weekday - 1],
            weekdayEN = WEEKDAYS_EN[weekday - 1],
            zone = zone,
            chinaOffsetHours = chinaOffsetHours,
            holidayCN = current?.nameCN,
            holidayEN = current?.nameEN,
            isWeekend = isWeekend,
            upcomingHolidayCN = upcoming?.nameCN ?: "",
            upcomingHolidayEN = upcoming?.nameEN ?: "",
            daysUntilHoliday = daysUntilHoliday,
            isWorking = isWorking,
            isWeekday = isWeekday,
            phase = phase,
            workMinutesRemaining = workMinutesRemaining,
            remainingSeconds = remainingSeconds,
            remainingMillis = remainingMs.coerceAtLeast(0L),
            workCountdownCN = workCountdownCN(isWorking, workMinutesRemaining),
            workCountdownEN = workCountdownEN(isWorking, workMinutesRemaining),
        )
    }

    fun formatHMS(totalSeconds: Long): Hms {
        val s = maxOf(0L, totalSeconds)
        val hours = s / 3600
        val minutes = (s % 3600) / 60
        val seconds = s % 60
        return Hms(
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            text = "$hours:${pad2(minutes)}:${pad2(seconds)}",
        )
    }

    fun holidayLabelCN(s: Snapshot): String {
        s.holidayCN?.let { return it }
        return if (s.isWeekend) "周末 · 非联邦假日" else "非联邦假日"
    }

    fun holidayLabelEN(s: Snapshot): String {
        s.holidayEN?.let { return it }
        return if (s.isWeekend) "Weekend · No federal holiday" else "No federal holiday"
    }

    fun workStatusCN(s: Snapshot): String {
        return when (s.phase) {
            "working" -> "正在上班"
            "before" -> "即将上班"
            "after" -> "已下班"
            "weekend" -> "周末"
            else -> "休息中"
        }
    }

    private data class Ymd(val year: Int, val month: Int, val day: Int)

    private fun nthWeekday(target: Int, ordinal: Int, month: Int, year: Int): Ymd {
        val firstWeekday = weekdayIndex(year, month, 1)
        val day = 1 + ((target - firstWeekday + 7) % 7) + (ordinal - 1) * 7
        return Ymd(year, month, day)
    }

    private fun lastMonday(month: Int, year: Int): Ymd {
        val lastDay = LocalDate.of(year, month, 1).lengthOfMonth()
        val lastWeekday = weekdayIndex(year, month, lastDay)
        val day = lastDay - ((lastWeekday - 2 + 7) % 7)
        return Ymd(year, month, day)
    }

    private fun addDays(year: Int, month: Int, day: Int, delta: Int): Ymd {
        val date = LocalDate.of(year, month, day).plusDays(delta.toLong())
        return Ymd(date.year, date.monthValue, date.dayOfMonth)
    }

    private fun allHolidays(year: Int): List<Holiday> {
        val list = ArrayList<Holiday>()
        for (y in (year - 1)..(year + 1)) list.addAll(holidaysInYear(y))
        return list.sortedWith(compareBy({ it.year }, { it.month }, { it.day }))
    }

    private fun isAfter(holiday: Holiday, year: Int, month: Int, day: Int): Boolean {
        if (holiday.year != year) return holiday.year > year
        if (holiday.month != month) return holiday.month > month
        return holiday.day > day
    }

    private fun calendarDaysBetween(
        y1: Int,
        m1: Int,
        d1: Int,
        y2: Int,
        m2: Int,
        d2: Int,
    ): Int {
        val a = LocalDate.of(y1, m1, d1).toEpochDay()
        val b = LocalDate.of(y2, m2, d2).toEpochDay()
        return (b - a).toInt()
    }

    private fun civilKey(year: Int, month: Int, day: Int): String {
        return "$year-${pad2(month)}-${pad2(day)}"
    }

    private fun dallasLocalInstant(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Instant {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, DALLAS).toInstant()
    }

    /**
     * Math.ceil(ms / denom) for integer milliseconds, including the negative range
     * clock.js can produce. Truncating integer division is not the same thing.
     */
    private fun jsCeilDiv(ms: Long, denom: Long): Long {
        val q = ms / denom
        val r = ms % denom
        return if (r == 0L || ms < 0) q else q + 1
    }

    /** Matches JavaScript String.prototype.padStart(2, "0"), including the sign. */
    private fun pad2(n: Long): String = n.toString().padStart(2, '0')

    private fun pad2(n: Int): String = pad2(n.toLong())
}
