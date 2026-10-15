package com.androssh.app.ui.widget

import android.content.Context
import androidx.core.content.edit

/** A saved connection shown as a button on a specific home-screen widget instance. */
data class PinnedConnection(
    val profileId: Long,
    val name: String,
    val username: String,
    val host: String,
)

/**
 * Persists which saved connections are shown on which widget instance.
 * `AppWidgetProvider`/its configuration `Activity` run without direct access
 * to the app's Room database in a simple, synchronous way, so a small
 * dedicated `SharedPreferences` file is used instead - it only stores
 * non-sensitive display metadata and a reference id, never credentials.
 */
object WidgetPreferences {
    private const val PREFS_NAME = "androssh_widget_prefs"

    const val MAX_CONNECTIONS = 6

    fun savePinnedConnections(context: Context, widgetId: Int, connections: List<PinnedConnection>) {
        prefs(context).edit {
            putString(key(widgetId), connections.take(MAX_CONNECTIONS).joinToString(RECORD_SEPARATOR) { encode(it) })
        }
    }

    fun getPinnedConnections(context: Context, widgetId: Int): List<PinnedConnection> {
        val raw = prefs(context).getString(key(widgetId), null) ?: return emptyList()
        return raw.split(RECORD_SEPARATOR).filter { it.isNotEmpty() }.mapNotNull { decode(it) }
    }

    fun clear(context: Context, widgetId: Int) {
        prefs(context).edit { remove(key(widgetId)) }
    }

    private fun encode(connection: PinnedConnection): String =
        listOf(connection.profileId.toString(), connection.name, connection.username, connection.host)
            .joinToString(FIELD_SEPARATOR) { it.replace(FIELD_SEPARATOR, " ").replace(RECORD_SEPARATOR, " ") }

    private fun decode(raw: String): PinnedConnection? {
        val parts = raw.split(FIELD_SEPARATOR, limit = 4)
        if (parts.size != 4) return null
        val profileId = parts[0].toLongOrNull() ?: return null
        return PinnedConnection(profileId, parts[1], parts[2], parts[3])
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(widgetId: Int) = "widget_$widgetId"

    private const val FIELD_SEPARATOR = "\u0001"
    private const val RECORD_SEPARATOR = "\u0002"
}
