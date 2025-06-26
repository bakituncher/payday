package com.example.payday

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class PaydayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
            if (thisAppWidget != null) {
                for (appWidgetId in thisAppWidget) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    companion object {
        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val views = RemoteViews(context.packageName, R.layout.payday_widget_layout)
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            val repository = PaydayRepository(context)
            val result = PaydayCalculator.calculate(
                payPeriod = repository.getPayPeriod(),
                paydayValue = repository.getPaydayValue(),
                biWeeklyRefDateString = repository.getBiWeeklyRefDateString(),
                salaryAmount = repository.getSalaryAmount(),
                weekendAdjustmentEnabled = repository.isWeekendAdjustmentEnabled()
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