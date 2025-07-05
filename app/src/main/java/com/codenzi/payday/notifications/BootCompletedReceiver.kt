package com.codenzi.payday.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.codenzi.payday.PaydayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                // Günlük hatırlatıcıları yeniden başlat
                NotificationScheduler.scheduleRepeatingExpenseReminders(context)

                // Maaş günü hatırlatıcısını yeniden kur
                val repository = PaydayRepository(context)
                val paydayValue = repository.getPaydayValue().first()
                if (paydayValue != -1) {
                    val today = LocalDate.now()
                    var nextPayday = today.withDayOfMonth(paydayValue)
                    if (today.dayOfMonth >= paydayValue) {
                        nextPayday = nextPayday.plusMonths(1)
                    }
                    val daysLeft = ChronoUnit.DAYS.between(today, nextPayday)
                    NotificationScheduler.schedulePaydayReminder(context, daysLeft)
                }
            }
        }
    }
}