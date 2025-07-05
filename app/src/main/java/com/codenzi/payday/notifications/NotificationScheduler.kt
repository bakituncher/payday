package com.codenzi.payday.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.codenzi.payday.MainActivity
import com.codenzi.payday.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationScheduler {

    private const val CHANNEL_ID = "payday_reminders_channel"
    private const val CHANNEL_NAME = "Payday Hatırlatıcıları"
    private const val PAYDAY_ALARM_REQUEST_CODE = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Payday uygulamanız için motive edici ve önemli bildirimler."
                enableLights(true)
                lightColor = context.getColor(R.color.primary)
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleRepeatingExpenseReminders(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val reminderWorkRequest = PeriodicWorkRequestBuilder<ExpenseReminderWorker>(
            8, TimeUnit.HOURS
        )
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            "daily_expense_reminder_work",
            ExistingPeriodicWorkPolicy.KEEP,
            reminderWorkRequest
        )
    }

    fun schedulePaydayReminder(context: Context, daysUntilPayday: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PaydayAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PAYDAY_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)

        if (daysUntilPayday > 1) {
            val triggerTime = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, (daysUntilPayday - 1).toInt())
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        }
    }
}

class ExpenseReminderWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titles = context.resources.getStringArray(R.array.expense_reminder_titles)
        val messages = context.resources.getStringArray(R.array.expense_reminder_messages)
        val randomTitle = titles.random()
        val randomMessage = messages.random()

        val notification = NotificationCompat.Builder(context, "payday_reminders_channel")
            .setSmallIcon(R.drawable.ic_stat_ic_notification)
            .setContentTitle(randomTitle)
            .setContentText(randomMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(randomMessage))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1, notification)
        return Result.success()
    }
}