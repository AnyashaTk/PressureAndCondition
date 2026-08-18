package com.local.statetracker.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.local.statetracker.StateTrackerApplication
import com.local.statetracker.data.CheckInSlot
import java.time.LocalDate

/** Debug-build-only hook used to verify that AlarmManager wakes the app with no Activity. */
class DebugReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = runCatching { CheckInSlot.valueOf(intent.getStringExtra(EXTRA_SLOT) ?: CheckInSlot.DAY.name) }.getOrDefault(CheckInSlot.DAY)
        val date = runCatching { LocalDate.parse(intent.getStringExtra(EXTRA_DATE)) }.getOrDefault(LocalDate.now())
        val delayMillis = intent.getLongExtra("delay_millis", 15_000L).coerceAtLeast(1_000L)
        (context.applicationContext as StateTrackerApplication).container.scheduler.scheduleDebug(slot, date, System.currentTimeMillis() + delayMillis)
    }
}
