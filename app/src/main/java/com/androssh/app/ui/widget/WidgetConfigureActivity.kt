package com.androssh.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import com.androssh.app.AndroSshApplication
import com.androssh.app.data.HostProfile

/**
 * Widget settings: choose how many connection buttons the widget shows (1..6)
 * and which saved connections they are.
 */
class WidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val existing = WidgetPreferences.getPinnedConnections(this, appWidgetId)
        val repository = (application as AndroSshApplication).container.connectionRepository
        setContent {
            val profiles by repository.observeProfiles().collectAsState(initial = emptyList())
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WidgetConfigureScreen(
                        profiles = profiles,
                        initialSelection = existing.map { it.profileId },
                        initialStyle = WidgetPreferences.getStyle(this, appWidgetId),
                        onSave = ::finishWithSelection,
                    )
                }
            }
        }
    }

    private fun finishWithSelection(selected: List<HostProfile>, style: WidgetStyle) {
        WidgetPreferences.saveStyle(this, appWidgetId, style)
        WidgetPreferences.savePinnedConnections(
            this,
            appWidgetId,
            selected.map { PinnedConnection(it.id, it.name, it.username, it.host) },
        )
        AndroSshWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)

        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

@Composable
private fun WidgetConfigureScreen(
    profiles: List<HostProfile>,
    initialSelection: List<Long>,
    initialStyle: WidgetStyle,
    onSave: (List<HostProfile>, WidgetStyle) -> Unit,
) {
    var style by remember { mutableStateOf(initialStyle) }
    var maxCount by remember { mutableStateOf(initialSelection.size.coerceIn(1, WidgetPreferences.MAX_CONNECTIONS)) }
    var selectedIds by remember { mutableStateOf(initialSelection) }
    // Selection order is preserved so buttons appear in the order the user picked them.
    val chosen = selectedIds.mapNotNull { id -> profiles.firstOrNull { it.id == id } }.take(maxCount)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text("Widget settings", style = MaterialTheme.typography.titleLarge)
        Text("Number of connections shown", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            (1..WidgetPreferences.MAX_CONNECTIONS).forEach { n ->
                FilterChip(
                    selected = maxCount == n,
                    onClick = {
                        maxCount = n
                        selectedIds = selectedIds.take(n)
                    },
                    label = { Text(n.toString()) },
                )
            }
        }
        Text(
            "Choose connections (${chosen.size}/$maxCount)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (profiles.isEmpty()) {
            Text("No saved connections yet. Add one in AndroSSH first.")
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                StyleSection(style = style, onChange = { style = it })
            }
            items(profiles, key = { it.id }) { profile ->
                val checked = profile.id in selectedIds
                val enabled = checked || selectedIds.size < maxCount
                val toggle = {
                    selectedIds = if (checked) selectedIds - profile.id else selectedIds + profile.id
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { toggle() }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = checked, onCheckedChange = { toggle() }, enabled = enabled)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(profile.name, style = MaterialTheme.typography.titleSmall)
                        Text("${profile.username}@${profile.host}:${profile.port}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(
            onClick = { onSave(chosen, style) },
            enabled = chosen.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}

private val BACKGROUND_COLORS = listOf(0x000000, 0xFFFFFF, 0x1E1E1E, 0x1565C0, 0x2E7D32, 0xC62828, 0x6A1B9A, 0xEF6C00)
private val TEXT_COLORS = listOf(0xFFFFFF, 0x000000, 0xFFEB3B, 0x80D8FF, 0xA5D6A7, 0xFF8A80)

@Composable
private fun StyleSection(style: WidgetStyle, onChange: (WidgetStyle) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text("Background color", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        ColorRow(BACKGROUND_COLORS, style.backgroundRgb) { onChange(style.copy(backgroundRgb = it)) }
        Text("Background opacity: ${style.backgroundAlphaPercent}%", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = style.backgroundAlphaPercent.toFloat(),
            onValueChange = { onChange(style.copy(backgroundAlphaPercent = it.toInt())) },
            valueRange = 0f..100f,
        )
        Text("Text & button color", style = MaterialTheme.typography.titleSmall)
        ColorRow(TEXT_COLORS, style.textRgb) { onChange(style.copy(textRgb = it)) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(Color(style.backgroundArgb))
                .padding(12.dp),
        ) {
            Text(
                "Preview: AndroSSH",
                color = Color(style.textArgb),
                modifier = Modifier.background(Color(style.buttonArgb)).padding(8.dp),
            )
        }
    }
}

@Composable
private fun ColorRow(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        colors.forEach { rgb ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF000000.toInt() or rgb))
                    .border(
                        width = if (rgb == selected) 3.dp else 1.dp,
                        color = if (rgb == selected) MaterialTheme.colorScheme.primary else Color.Gray,
                        shape = CircleShape,
                    )
                    .clickable { onPick(rgb) },
            )
        }
    }
}
