package com.binbin.dallasclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.widget.RemoteViews

/**
 * Pushes the current Dallas snapshot into every home-screen widget and schedules
 * the next refresh on the following minute.
 *
 * The big countdown is a [android.widget.Chronometer] running inside the launcher,
 * so the seconds keep moving without waking the app. Status, Dallas time, and the
 * Chinese minute phrase are redrawn about once a minute while the phone is awake.
 * Alarms use RTC (not RTC_WAKEUP) so a sleeping phone is not woken every minute.
 */
object WidgetUpdater {
    const val ACTION_TICK: String = "com.binbin.dallasclock.ACTION_TICK"

    private const val TICK_REQUEST = 1001
    private val ACCENT = 0xFFC4D685.toInt()
    private val MUTED = 0xFF9EAB94.toInt()

    private val COMPACT_CLICK_IDS = intArrayOf(
        R.id.widget_root,
        R.id.time,
        R.id.period,
        R.id.status,
        R.id.countdown_label,
        R.id.chrono,
        R.id.holiday,
    )

    private val WIDE_CLICK_IDS = intArrayOf(
        R.id.widget_root,
        R.id.eyebrow,
        R.id.time,
        R.id.period,
        R.id.date,
        R.id.status,
        R.id.countdown_label,
        R.id.chrono,
        R.id.countdown,
        R.id.holiday,
    )

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        val snapshot = ClockCore.snapshot()
        val manager = AppWidgetManager.getInstance(appContext)
        push(appContext, manager, DallasClockCompactProvider::class.java, snapshot)
        push(appContext, manager, DallasClockWideProvider::class.java, snapshot)
        if (hasWidgets(appContext, manager)) {
            schedule(appContext)
        }
    }

    fun stopIfIdle(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        if (!hasWidgets(appContext, manager)) {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(tickIntent(appContext))
        }
    }

    fun canScheduleExact(context: Context): Boolean {
        val alarmManager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun hasWidgets(context: Context, manager: AppWidgetManager): Boolean {
        val compact = manager.getAppWidgetIds(ComponentName(context, DallasClockCompactProvider::class.java))
        val wide = manager.getAppWidgetIds(ComponentName(context, DallasClockWideProvider::class.java))
        return compact.isNotEmpty() || wide.isNotEmpty()
    }

    private fun push(
        context: Context,
        manager: AppWidgetManager,
        provider: Class<*>,
        snapshot: ClockCore.Snapshot,
    ) {
        val component = ComponentName(context, provider)
        if (manager.getAppWidgetIds(component).isEmpty()) return
        val compact = render(context, R.layout.widget_compact, snapshot, wide = false)
        val wide = render(context, R.layout.widget_wide, snapshot, wide = true)
        val views = RemoteViews(
            mapOf(
                SizeF(140f, 80f) to compact,
                SizeF(250f, 110f) to wide,
            ),
        )
        manager.updateAppWidget(component, views)
    }

    private fun render(
        context: Context,
        layout: Int,
        snapshot: ClockCore.Snapshot,
        wide: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        val status = ClockCore.workStatusCN(snapshot)
        val label = if (snapshot.isWorking) "距下班" else "距上班"
        val working = snapshot.phase == "working"
        views.setTextViewText(R.id.time, snapshot.time)
        views.setTextViewText(
            R.id.period,
            if (wide) {
                "${snapshot.periodCN} ${snapshot.periodEN} · ${snapshot.zone}"
            } else {
                "达拉斯 · ${snapshot.periodCN} ${snapshot.zone}"
            },
        )
        views.setTextViewText(R.id.status, status)
        views.setTextColor(R.id.status, if (working) ACCENT else MUTED)
        views.setTextViewText(R.id.countdown_label, label)
        val base = SystemClock.elapsedRealtime() + snapshot.remainingMillis
        views.setChronometer(R.id.chrono, base, null, true)
        views.setChronometerCountDown(R.id.chrono, true)

        val holidayText: String
        val holidayColor: Int
        if (snapshot.holidayCN != null) {
            holidayText = snapshot.holidayCN
            holidayColor = ACCENT
        } else if (wide) {
            holidayText = "下个假日：${snapshot.upcomingHolidayCN}（${snapshot.daysUntilHoliday} 天后）"
            holidayColor = MUTED
        } else {
            holidayText = "${snapshot.upcomingHolidayCN} · ${snapshot.daysUntilHoliday}天"
            holidayColor = MUTED
        }
        views.setTextViewText(R.id.holiday, holidayText)
        views.setTextColor(R.id.holiday, holidayColor)

        if (wide) {
            views.setTextViewText(R.id.date, "${snapshot.dateCN} ${snapshot.weekdayCN}")
            views.setTextViewText(R.id.countdown, snapshot.workCountdownCN)
        }

        val description = "达拉斯 ${snapshot.periodCN} ${snapshot.time} ${snapshot.zone}，$status，${snapshot.workCountdownCN}"
        views.setContentDescription(R.id.widget_root, description)

        val click = openAppIntent(context)
        val ids = if (wide) WIDE_CLICK_IDS else COMPACT_CLICK_IDS
        for (id in ids) {
            views.setOnClickPendingIntent(id, click)
        }
        return views
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun tickIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClockUpdateReceiver::class.java).apply {
            action = ACTION_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            TICK_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = tickIntent(context)
        val now = System.currentTimeMillis()
        val next = (now / 60_000L + 1L) * 60_000L + 150L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, next, pending)
            } else {
                alarmManager.setExact(AlarmManager.RTC, next, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC, next, pending)
        }
    }
}
