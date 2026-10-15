package com.androssh.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.androssh.app.MainActivity
import com.androssh.app.R

/**
 * Transparent home-screen widget (`AppWidgetProvider` + `RemoteViews`) that shows
 * the app title and one button per connection chosen in [WidgetConfigureActivity].
 * Tapping a button launches [MainActivity] directly into that connection.
 */
class AndroSshWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> updateWidget(context, appWidgetManager, widgetId) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> WidgetPreferences.clear(context, widgetId) }
    }

    companion object {
        private val BUTTON_IDS = intArrayOf(
            R.id.widget_btn_1, R.id.widget_btn_2, R.id.widget_btn_3,
            R.id.widget_btn_4, R.id.widget_btn_5, R.id.widget_btn_6,
        )

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val connections = WidgetPreferences.getPinnedConnections(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_androssh)

            views.setViewVisibility(R.id.widget_empty, if (connections.isEmpty()) View.VISIBLE else View.GONE)
            val style = WidgetPreferences.getStyle(context, widgetId)
            views.setInt(R.id.widget_root, "setBackgroundColor", style.backgroundArgb)
            views.setTextColor(R.id.widget_title, style.textArgb)
            views.setTextColor(R.id.widget_empty, style.textArgb)

            BUTTON_IDS.forEachIndexed { index, buttonId ->
                val connection = connections.getOrNull(index)
                if (connection == null) {
                    views.setViewVisibility(buttonId, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(buttonId, View.VISIBLE)
                views.setTextViewText(buttonId, connection.name)
                views.setTextColor(buttonId, style.textArgb)
                views.setInt(buttonId, "setBackgroundColor", style.buttonArgb)
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra(MainActivity.EXTRA_PROFILE_ID, connection.profileId)
                }
                views.setOnClickPendingIntent(
                    buttonId,
                    PendingIntent.getActivity(
                        context,
                        widgetId * WidgetPreferences.MAX_CONNECTIONS + index,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }

            if (connections.isEmpty()) {
                val configureIntent = Intent(context, WidgetConfigureActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        widgetId * WidgetPreferences.MAX_CONNECTIONS + BUTTON_IDS.size,
                        configureIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
