package com.binbin.dallasclock

import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixture file is generated from clock.js. A mismatch means this port drifted.
 */
class ClockCoreTest {
    @Test
    fun snapshotsMatchClockJs() {
        val root = loadFixtures()
        val cases = root.getJSONArray("cases")
        assertTrue("fixture should cover the JS tests", cases.length() > 200)
        val seen = HashSet<String>()
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val utc = c.getString("utc")
            seen.add(utc)
            val s = ClockCore.snapshot(Instant.parse(utc))
            val hms = ClockCore.formatHMS(s.remainingSeconds)
            assertSame(utc, "time", c.getString("time"), s.time)
            assertSame(utc, "timeWithSeconds", c.getString("timeWithSeconds"), s.timeWithSeconds)
            assertSame(utc, "periodCN", c.getString("periodCN"), s.periodCN)
            assertSame(utc, "periodEN", c.getString("periodEN"), s.periodEN)
            assertSame(utc, "dateCN", c.getString("dateCN"), s.dateCN)
            assertSame(utc, "dateEN", c.getString("dateEN"), s.dateEN)
            assertSame(utc, "weekdayCN", c.getString("weekdayCN"), s.weekdayCN)
            assertSame(utc, "weekdayEN", c.getString("weekdayEN"), s.weekdayEN)
            assertSame(utc, "zone", c.getString("zone"), s.zone)
            assertSame(utc, "chinaOffsetHours", c.getInt("chinaOffsetHours"), s.chinaOffsetHours)
            assertSame(utc, "holidayCN", c.nullableString("holidayCN"), s.holidayCN)
            assertSame(utc, "holidayEN", c.nullableString("holidayEN"), s.holidayEN)
            assertSame(utc, "isWeekend", c.getBoolean("isWeekend"), s.isWeekend)
            assertSame(utc, "upcomingHolidayCN", c.getString("upcomingHolidayCN"), s.upcomingHolidayCN)
            assertSame(utc, "upcomingHolidayEN", c.getString("upcomingHolidayEN"), s.upcomingHolidayEN)
            assertSame(utc, "daysUntilHoliday", c.getInt("daysUntilHoliday"), s.daysUntilHoliday)
            assertSame(utc, "isWorking", c.getBoolean("isWorking"), s.isWorking)
            assertSame(utc, "isWeekday", c.getBoolean("isWeekday"), s.isWeekday)
            assertSame(utc, "phase", c.getString("phase"), s.phase)
            assertSame(utc, "workMinutesRemaining", c.getLong("workMinutesRemaining"), s.workMinutesRemaining)
            assertSame(utc, "remainingSeconds", c.getLong("remainingSeconds"), s.remainingSeconds)
            assertSame(utc, "workCountdownCN", c.getString("workCountdownCN"), s.workCountdownCN)
            assertSame(utc, "workCountdownEN", c.getString("workCountdownEN"), s.workCountdownEN)
            assertSame(utc, "holidayLabelCN", c.getString("holidayLabelCN"), ClockCore.holidayLabelCN(s))
            assertSame(utc, "holidayLabelEN", c.getString("holidayLabelEN"), ClockCore.holidayLabelEN(s))
            assertSame(utc, "workStatusCN", c.getString("workStatusCN"), ClockCore.workStatusCN(s))
            assertSame(utc, "hmsHours", c.getLong("hmsHours"), hms.hours)
            assertSame(utc, "hmsMinutes", c.getLong("hmsMinutes"), hms.minutes)
            assertSame(utc, "hmsSeconds", c.getLong("hmsSeconds"), hms.seconds)
            assertSame(utc, "hmsText", c.getString("hmsText"), hms.text)
        }
        for (utc in REQUIRED) {
            assertTrue("fixture missing $utc", seen.contains(utc))
        }
    }

    @Test
    fun holidaysMatchClockJs() {
        val years = loadFixtures().getJSONArray("holidayYears")
        assertTrue(years.length() > 40)
        for (i in 0 until years.length()) {
            val yearJson = years.getJSONObject(i)
            val year = yearJson.getInt("year")
            val expected = yearJson.getJSONArray("days")
            val actual = ClockCore.holidaysInYear(year)
            assertEquals("holiday count $year", expected.length(), actual.size)
            for (j in 0 until expected.length()) {
                val e = expected.getJSONObject(j)
                val a = actual[j]
                assertEquals("$year#$j year", e.getInt("year"), a.year)
                assertEquals("$year#$j month", e.getInt("month"), a.month)
                assertEquals("$year#$j day", e.getInt("day"), a.day)
                assertEquals("$year#$j cn", e.getString("nameCN"), a.nameCN)
                assertEquals("$year#$j en", e.getString("nameEN"), a.nameEN)
            }
        }
    }

    private fun loadFixtures(): JSONObject {
        val loader = checkNotNull(javaClass.classLoader) { "clock-fixtures.json missing" }
        val stream = loader.getResourceAsStream("clock-fixtures.json")
            ?: error("clock-fixtures.json missing; run node tests/export-fixtures.mjs")
        return JSONObject(stream.bufferedReader().readText())
    }

    private fun JSONObject.nullableString(key: String): String? {
        return if (isNull(key)) null else getString(key)
    }

    private fun assertSame(utc: String, field: String, expected: Any?, actual: Any?) {
        if (expected != actual) {
            throw AssertionError("$utc $field\n  expected: $expected\n  actual:   $actual")
        }
    }

    companion object {
        private val REQUIRED = listOf(
            "2026-11-15T05:59:00Z",
            "2026-11-23T14:30:00Z",
            "2026-11-23T23:29:30Z",
            "2026-11-26T14:30:00Z",
            "2026-11-27T18:00:00Z",
            "2026-07-03T18:00:00Z",
            "2026-07-04T18:00:00Z",
            "2021-06-18T18:00:00Z",
            "2020-06-19T18:00:00Z",
            "2021-12-31T18:00:00Z",
            "2021-01-20T18:00:00Z",
            "2026-03-02T18:00:00Z",
            "2026-03-08T08:00:00Z",
            "2026-11-01T07:00:00Z",
        )
    }
}
