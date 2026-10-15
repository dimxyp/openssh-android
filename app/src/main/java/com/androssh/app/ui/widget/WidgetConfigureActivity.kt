package com.androssh.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Surface
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
                        onSave = ::finishWithSelection,
                    )
                }
            }
        }
    }

    private fun finishWithSelection(selected: List<HostProfile>) {
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
    onSave: (List<HostProfile>) -> Unit,
) {
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
            onClick = { onSave(chosen) },
            enabled = chosen.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}
