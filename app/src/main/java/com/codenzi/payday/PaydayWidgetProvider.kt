package com.codenzi.payday

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PaydayWidgetProvider : AppWidgetProvider() {

    // Widget'ın kendi CoroutineScope'u
    private val appWidgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        appWidgetScope.launch {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.payday_widget_layout)
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            val repository = PaydayRepository(context)

            // Flow'lardan verileri asenkron olarak 'first()' ile al
            val payPeriod = repository.getPayPeriod().first()
            val paydayValue = repository.getPaydayValue().first()
            val biWeeklyRefDate = repository.getBiWeeklyRefDateString().first()
            val salary = repository.getSalaryAmount().first()
            val weekendAdjustment = repository.isWeekendAdjustmentEnabled().first()

            val result = PaydayCalculator.calculate(
                payPeriod = payPeriod,
                paydayValue = paydayValue,
                biWeeklyRefDateString = biWeeklyRefDate,
                weekendAdjustmentEnabled = weekendAdjustment
            )


            if (result == null) {
                views.setTextViewText(R.id.widget_days_left_text_view, "-")
                views.setTextViewText(R.id.widget_suffix_text_view, context.getString(R.string.widget_configure))
            } else {
                if (result.isPayday) {
                    views.setTextViewText(R.id.widget_days_left_text_view, "🎉")
                    views.setTextViewText(R.id.widget_suffix_text_view, context.getString(R.string.widget_payday_today))
                } else {
                    views.setTextViewText(R.id.widget_days_left_text_view, result.daysLeft.toString())
                    views.setTextViewText(R.id.widget_suffix_text_view, context.getString(R.string.widget_days_left))
                }
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}