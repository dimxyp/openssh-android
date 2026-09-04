package com.androssh.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.androssh.app.MainActivity
import com.androssh.app.R

/**
 * Original, from-scratch home-screen widget implementation using the
 * classic `AppWidgetProvider` + `RemoteViews` APIs (no Glance dependency
 * required). Each widget instance shows the connection pinned to it via
 * [WidgetConfigureActivity] and, when tapped, launches [MainActivity]
 * directly into that connection's terminal.
 */
class AndroSshWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> updateWidget(context, appWidgetManager, widgetId) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> WidgetPreferences.clear(context, widgetId) }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val pinned = WidgetPreferences.getPinnedConnection(context, widgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_androssh)

            if (pinned != null) {
                views.setTextViewText(R.id.widget_title, pinned.name)
                views.setTextViewText(R.id.widget_subtitle, "${pinned.username}@${pinned.host}")
            } else {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_default_title))
                views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_tap_to_configure))
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (pinned != null) putExtra(MainActivity.EXTRA_PROFILE_ID, pinned.profileId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
