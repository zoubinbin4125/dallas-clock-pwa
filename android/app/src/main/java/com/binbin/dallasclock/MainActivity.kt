package com.binbin.dallasclock

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

/** Full-screen Dallas clock. Same labels and layout idea as the PWA. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastWidgetMinute = Long.MIN_VALUE

    private lateinit var time: TextView
    private lateinit var period: TextView
    private lateinit var date: TextView
    private lateinit var weekday: TextView
    private lateinit var holiday: TextView
    private lateinit var china: TextView
    private lateinit var statusRow: View
    private lateinit var statusDot: View
    private lateinit var status: TextView
    private lateinit var countdownLabel: TextView
    private lateinit var hours: TextView
    private lateinit var minutes: TextView
    private lateinit var seconds: TextView
    private lateinit var countdownCn: TextView
    private lateinit var countdownEn: TextView
    private lateinit var upcoming: TextView
    private lateinit var card: View
    private lateinit var alarmBanner: View

    private val tick = object : Runnable {
        override fun run() {
            render()
            val delay = 1000 - (System.currentTimeMillis() % 1000) + 20
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bind()
        applyInsets()
        findViewById<View>(R.id.open_about).setOnClickListener { showAbout() }
        alarmBanner.setOnClickListener { requestExactAlarm() }
    }

    override fun onStart() {
        super.onStart()
        updateAlarmBanner()
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        super.onStop()
    }

    private fun bind() {
        time = findViewById(R.id.time)
        period = findViewById(R.id.period)
        date = findViewById(R.id.date)
        weekday = findViewById(R.id.weekday)
        holiday = findViewById(R.id.holiday)
        china = findViewById(R.id.china)
        statusRow = findViewById(R.id.status_row)
        statusDot = findViewById(R.id.status_dot)
        status = findViewById(R.id.status)
        countdownLabel = findViewById(R.id.countdown_label)
        hours = findViewById(R.id.hours)
        minutes = findViewById(R.id.minutes)
        seconds = findViewById(R.id.seconds)
        countdownCn = findViewById(R.id.countdown_cn)
        countdownEn = findViewById(R.id.countdown_en)
        upcoming = findViewById(R.id.upcoming)
        card = findViewById(R.id.card)
        alarmBanner = findViewById(R.id.alarm_banner)
    }

    private fun render() {
        val s = ClockCore.snapshot(Instant.now())
        val hms = ClockCore.formatHMS(s.remainingSeconds)
        val statusText = ClockCore.workStatusCN(s)
        val holidayText = ClockCore.holidayLabelCN(s)
        val working = s.phase == "working"
        val season = if (s.zone == "CDT") "夏令时" else "标准时"

        time.text = s.timeWithSeconds
        period.text = "${s.periodCN} ${s.periodEN} · ${s.zone}"
        date.text = s.dateCN
        weekday.text = "${s.weekdayCN} · ${s.weekdayEN.uppercase(Locale.US)}"
        holiday.text = holidayText
        holiday.setTextColor(if (s.holidayCN != null) accent() else holidayIdle())
        china.text = "比中国慢 ${abs(s.chinaOffsetHours)} 小时 · $season"
        status.text = statusText
        status.setTextColor(if (working) accent() else muted())
        statusRow.setBackgroundResource(if (working) R.drawable.bg_pill else R.drawable.bg_pill_off)
        statusDot.background.mutate().setTint(if (working) accent() else muted())
        countdownLabel.text = if (s.isWorking) "距下班" else "距上班"
        hours.text = hms.hours.toString()
        minutes.text = hms.minutes.toString().padStart(2, '0')
        seconds.text = hms.seconds.toString().padStart(2, '0')
        countdownCn.text = s.workCountdownCN
        countdownEn.text = s.workCountdownEN
        upcoming.text = "下个假日：${s.upcomingHolidayCN}（${s.daysUntilHoliday} 天后）"
        title = "${s.time} ${s.periodEN} · ${countdownLabel.text} ${hms.text}"
        card.contentDescription = "达拉斯 ${s.periodCN} ${s.timeWithSeconds} ${s.periodEN} ${s.zone}，${s.dateCN} ${s.weekdayCN}，$holidayText，$statusText，${s.workCountdownCN}"

        val minute = System.currentTimeMillis() / 60_000L
        if (minute != lastWidgetMinute) {
            lastWidgetMinute = minute
            WidgetUpdater.refresh(this)
        }
    }

    private fun updateAlarmBanner() {
        alarmBanner.visibility = if (WidgetUpdater.canScheduleExact(this)) View.GONE else View.VISIBLE
    }

    private fun requestExactAlarm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Some builds hide this screen when USE_EXACT_ALARM is already the grant path.
        }
    }

    private fun showAbout() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_about)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.findViewById<View>(R.id.close_about).setOnClickListener { dialog.dismiss() }
        dialog.show()
        val width = min((resources.displayMetrics.widthPixels * 0.92f).toInt(), dp(520))
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun applyInsets() {
        val page = findViewById<View>(R.id.page)
        window.setDecorFitsSystemWindows(false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        page.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val wide = resources.configuration.screenWidthDp >= 600
            val baseX = dp(if (wide) 28 else 16)
            val baseTop = dp(if (wide) 16 else 12)
            val baseBottom = dp(if (wide) 24 else 16)
            view.setPadding(
                bars.left + baseX,
                bars.top + baseTop,
                bars.right + baseX,
                bars.bottom + baseBottom,
            )
            WindowInsets.CONSUMED
        }
        page.requestApplyInsets()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun accent(): Int = getColor(R.color.accent)

    private fun muted(): Int = getColor(R.color.muted)

    private fun holidayIdle(): Int = getColor(R.color.holiday_idle)
}
