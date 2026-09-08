package com.androssh.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androssh.app.ui.AndroSshApp
import com.androssh.app.viewmodel.AndroSshViewModel
import com.androssh.app.viewmodel.AndroSshViewModelFactory
import com.androssh.app.viewmodel.BackupViewModel
import com.androssh.app.viewmodel.BackupViewModelFactory
import com.androssh.app.viewmodel.SftpViewModel
import com.androssh.app.viewmodel.SftpViewModelFactory

class MainActivity : ComponentActivity() {
    /**
     * The session keeps running whether or not the user grants this; only the ongoing notification
     * (and with it the tap-to-return / Disconnect shortcuts) would be missing, so a denial is
     * simply ignored.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /**
     * Profile the widget asked us to connect to. The activity is `singleTask`, so a widget tap
     * while it is already running arrives through [onNewIntent] rather than [onCreate].
     */
    private val pendingWidgetProfileId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars and let the platform keep them transparent, so no opaque
        // strip is ever painted where the status bar sits.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val container = (application as AndroSshApplication).container
        pendingWidgetProfileId.value = intent.widgetProfileId()
        setContent {
            val viewModel: AndroSshViewModel = viewModel(
                factory = AndroSshViewModelFactory(
                    repository = container.connectionRepository,
                    sessionHolder = container.sshSessionHolder,
                ),
            )
            val sftpViewModel: SftpViewModel = viewModel(
                factory = SftpViewModelFactory(container.sftpRepository),
            )
            val backupViewModel: BackupViewModel = viewModel(
                factory = BackupViewModelFactory(container.backupManager),
            )
            val widgetProfileId by pendingWidgetProfileId
            LaunchedEffect(widgetProfileId) {
                widgetProfileId?.let { profileId ->
                    viewModel.connectByProfileId(profileId)
                    pendingWidgetProfileId.value = null
                }
            }
            AndroSshApp(
                viewModel = viewModel,
                sftpViewModel = sftpViewModel,
                backupViewModel = backupViewModel,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWidgetProfileId.value = intent.widgetProfileId()
    }

    private fun Intent.widgetProfileId(): Long? =
        getLongExtra(EXTRA_PROFILE_ID, -1L).takeIf { it >= 0 }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        /** Extra used by the home-screen widget to launch straight into a connection. */
        const val EXTRA_PROFILE_ID = "com.androssh.app.EXTRA_PROFILE_ID"
    }
}
