package com.binbin.dallasclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Refreshes widgets after boot, clock changes, unlock, and the once-a-minute tick. */
class ClockUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        WidgetUpdater.refresh(context)
    }
}
