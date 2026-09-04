package com.androssh.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.androssh.app.data.backup.ImportConflictResolution
import com.androssh.app.viewmodel.BackupViewModel

/**
 * Export/import screen: lets the user back up all saved connections to a
 * single AES-GCM encrypted file (protected by a passphrase entered here,
 * never stored) and restore them later, merging with whatever is already
 * saved and asking how to handle host+username+port duplicates.
 */
@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var passphrase by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null && passphrase.isNotBlank()) {
            viewModel.export(passphrase) {
                context.contentResolver.openOutputStream(uri) ?: error("Unable to open export destination")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImportUri = uri }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Backup & restore", style = MaterialTheme.typography.titleMedium)
        Text(
            "Saved connections (including passwords) are encrypted with the passphrase " +
                "below before being written to disk. Nothing is ever stored unencrypted.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Passphrase") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = passphrase.isNotBlank() && !uiState.isBusy,
                onClick = { exportLauncher.launch("androssh-backup.asbk") },
            ) { Text("Export") }
            Button(
                enabled = passphrase.isNotBlank() && !uiState.isBusy,
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            ) { Text("Import") }
            OutlinedButton(onClick = onBack) { Text("Close") }
        }

        pendingImportUri?.let { uri ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("File selected. Confirm to decrypt with the passphrase above.")
            }
            Button(onClick = {
                viewModel.import(passphrase) {
                    context.contentResolver.openInputStream(uri) ?: error("Unable to open selected file")
                }
                pendingImportUri = null
            }) { Text("Decrypt & import") }
        }

        if (uiState.isBusy) Text("Working...")
        uiState.status?.let { Text(it) }
        uiState.error?.let { Text("Error: $it") }

        uiState.pendingConflicts.forEach { conflict ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("${conflict.incoming.username}@${conflict.incoming.host}:${conflict.incoming.port} already exists")
                    Text("Existing name: ${conflict.existing.name} - Incoming name: ${conflict.incoming.name}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            viewModel.resolveConflict(conflict, ImportConflictResolution.Skip)
                        }) { Text("Skip") }
                        OutlinedButton(onClick = {
                            viewModel.resolveConflict(conflict, ImportConflictResolution.Overwrite)
                        }) { Text("Overwrite") }
                        OutlinedButton(onClick = {
                            viewModel.resolveConflict(conflict, ImportConflictResolution.Duplicate)
                        }) { Text("Keep both") }
                    }
                }
            }
        }
    }
}
