package com.androssh.app.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.androssh.app.AndroSshApplication
import com.androssh.app.data.HostProfile

/**
 * Lets the user pick which saved connection a newly-placed home screen
 * widget should show/pin, per the standard Android app-widget
 * configuration-activity contract.
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

        val repository = (application as AndroSshApplication).container.connectionRepository
        setContent {
            val profiles by repository.observeProfiles().collectAsState(initial = emptyList())
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WidgetConfigureScreen(profiles = profiles, onSelect = ::finishWithSelection)
                }
            }
        }
    }

    private fun finishWithSelection(profile: HostProfile) {
        WidgetPreferences.savePinnedConnection(
            this,
            appWidgetId,
            PinnedConnection(
                profileId = profile.id,
                name = profile.name,
                username = profile.username,
                host = profile.host,
            ),
        )
        AndroSshWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)

        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@Composable
private fun WidgetConfigureScreen(profiles: List<HostProfile>, onSelect: (HostProfile) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Choose a connection to pin", style = MaterialTheme.typography.titleMedium)
        if (profiles.isEmpty()) {
            Text("No saved connections yet. Add one in AndroSSH first.")
        } else {
            LazyColumn {
                items(profiles, key = { it.id }) { profile ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelect(profile) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(profile.name, style = MaterialTheme.typography.titleSmall)
                            Text("${profile.username}@${profile.host}:${profile.port}")
                        }
                    }
                }
            }
        }
    }
}
