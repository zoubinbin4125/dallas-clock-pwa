package com.binbin.dallasclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

open class DallasClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetUpdater.refresh(context)
    }

    override fun onDisabled(context: Context) {
        WidgetUpdater.stopIfIdle(context)
    }
}

/** Cover-screen sized widget. Label in the picker is 达拉斯时间. */
class DallasClockCompactProvider : DallasClockWidgetProvider()

/** Inner-screen sized widget. Label in the picker is 达拉斯时间. */
class DallasClockWideProvider : DallasClockWidgetProvider()
