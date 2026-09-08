package com.androssh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.androssh.app.ssh.ForegroundServiceKeepAlive
import com.androssh.app.ui.AndroSshApp
import com.androssh.app.viewmodel.AndroSshViewModel
import com.androssh.app.viewmodel.AndroSshViewModelFactory
import com.androssh.app.viewmodel.BackupViewModel
import com.androssh.app.viewmodel.BackupViewModelFactory
import com.androssh.app.viewmodel.SftpViewModel
import com.androssh.app.viewmodel.SftpViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars and let the platform keep them transparent, so no opaque
        // strip is ever painted where the status bar sits.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as AndroSshApplication).container
        val widgetProfileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L).takeIf { it >= 0 }
        setContent {
            val viewModel: AndroSshViewModel = viewModel(
                factory = AndroSshViewModelFactory(
                    repository = container.connectionRepository,
                    sshConnectionManager = container.sshConnectionManager,
                    keepAlive = ForegroundServiceKeepAlive(applicationContext),
                ),
            )
            val sftpViewModel: SftpViewModel = viewModel(
                factory = SftpViewModelFactory(container.sftpRepository),
            )
            val backupViewModel: BackupViewModel = viewModel(
                factory = BackupViewModelFactory(container.backupManager),
            )
            LaunchedEffect(widgetProfileId) {
                widgetProfileId?.let { profileId -> viewModel.connectByProfileId(profileId) }
            }
            AndroSshApp(
                viewModel = viewModel,
                sftpViewModel = sftpViewModel,
                backupViewModel = backupViewModel,
            )
        }
    }

    companion object {
        /** Extra used by the home-screen widget to launch straight into a connection. */
        const val EXTRA_PROFILE_ID = "com.androssh.app.EXTRA_PROFILE_ID"
    }
}
