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

/** Per-widget appearance: RGB colors (no alpha) plus background opacity in percent. */
data class WidgetStyle(
    val backgroundRgb: Int = 0x000000,
    val backgroundAlphaPercent: Int = 70,
    val textRgb: Int = 0xFFFFFF,
) {
    val backgroundArgb: Int get() = (backgroundAlphaPercent * 255 / 100 shl 24) or backgroundRgb
    /** Button fill: a faint tint of the text color so buttons stand out from the background. */
    val buttonArgb: Int get() = (0x33 shl 24) or textRgb
    val textArgb: Int get() = (0xFF shl 24) or textRgb
}

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

    fun saveStyle(context: Context, widgetId: Int, style: WidgetStyle) {
        prefs(context).edit {
            putInt("bg_$widgetId", style.backgroundRgb)
            putInt("alpha_$widgetId", style.backgroundAlphaPercent)
            putInt("text_$widgetId", style.textRgb)
        }
    }

    fun getStyle(context: Context, widgetId: Int): WidgetStyle {
        val p = prefs(context)
        val d = WidgetStyle()
        return WidgetStyle(
            backgroundRgb = p.getInt("bg_$widgetId", d.backgroundRgb),
            backgroundAlphaPercent = p.getInt("alpha_$widgetId", d.backgroundAlphaPercent).coerceIn(0, 100),
            textRgb = p.getInt("text_$widgetId", d.textRgb),
        )
    }

    fun clear(context: Context, widgetId: Int) {
        prefs(context).edit {
            remove(key(widgetId))
            remove("bg_$widgetId")
            remove("alpha_$widgetId")
            remove("text_$widgetId")
        }
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
