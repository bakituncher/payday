package com.example.payday

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class PaydayWidgetProvider : AppWidgetProvider() {

    // Widget güncellendiğinde bu fonksiyon çağrılır.
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Ekrandaki tüm Payday widget'ları için döngü başlat
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        // Bu fonksiyon, widget'ı güncelleme mantığını içerir.
        // Artık çok daha temiz!
        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Widget'a tıklandığında MainActivity'yi açacak bir Intent oluşturuyoruz.
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Widget'ın arayüzünü yönetmek için RemoteViews kullanıyoruz.
            val views = RemoteViews(context.packageName, R.layout.payday_widget_layout)
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            // Tek ve merkezi hesaplayıcımızı çağırıyoruz.
            val result = PaydayCalculator.calculate(context)

            if (result == null) {
                // Eğer sonuç null ise (gün ayarlanmamış veya geçersizse)
                views.setTextViewText(R.id.widget_days_left_text_view, "-")
                views.setTextViewText(R.id.widget_suffix_text_view, "Ayarla")
            } else {
                // Sonuç geçerliyse, arayüzü güncelle
                if (result.isPayday) {
                    views.setTextViewText(R.id.widget_days_left_text_view, "🎉")
                    views.setTextViewText(R.id.widget_suffix_text_view, "Maaş Günü!")
                } else {
                    views.setTextViewText(R.id.widget_days_left_text_view, result.daysLeft.toString())
                    views.setTextViewText(R.id.widget_suffix_text_view, "Gün Kaldı")
                }
            }

            // Widget'ı son haliyle güncelle
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
