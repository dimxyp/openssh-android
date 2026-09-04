package com.androssh.app.ui.sftp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.androssh.app.viewmodel.SftpViewModel
import kotlinx.coroutines.launch

/**
 * Dual-pane SFTP browser: the left pane lists the local device tree picked
 * via the Storage Access Framework, the right pane lists the connected
 * remote server's filesystem. Uploads/downloads move files between the two;
 * transfers between two saved *remote* servers are driven the same way but
 * stream through this device rather than going through the local pane, since
 * SFTP itself has no server-to-server copy operation (see
 * [com.androssh.app.data.sftp.SftpRepository.transferBetweenServers]).
 */
@Composable
fun SftpScreen(
    viewModel: SftpViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var localFolder by remember { mutableStateOf<DocumentFile?>(null) }
    var localEntries by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    val pickTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val folder = DocumentFile.fromTreeUri(context, uri)
            localFolder = folder
            localEntries = folder?.listFiles()?.toList().orEmpty()
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val doc = DocumentFile.fromSingleUri(context, uri)
            val name = doc?.name ?: "upload.bin"
            val size = doc?.length() ?: 0L
            viewModel.upload(name, size) {
                context.contentResolver.openInputStream(uri)
                    ?: error("Unable to open selected file")
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SFTP: ${uiState.remote.profile?.name.orEmpty()}", style = MaterialTheme.typography.titleMedium)
        }
        uiState.transfer?.let { progress ->
            Column {
                Text(uiState.transferLabel.orEmpty())
                LinearProgressIndicator(
                    progress = { if (progress.totalBytes > 0) progress.percent / 100f else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${progress.percent}%")
                    OutlinedButton(onClick = viewModel::cancelTransfer) { Text("Cancel") }
                }
            }
        }
        uiState.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Row(
            modifier = Modifier.fillMaxSize().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = { pickTreeLauncher.launch(null) }) { Text("Choose folder") }
                    OutlinedButton(onClick = { pickFileLauncher.launch(arrayOf("*/*")) }) { Text("Upload file") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(localEntries, key = { it.uri.toString() }) { doc ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(doc.name.orEmpty() + if (doc.isDirectory) "/" else "")
                                if (!doc.isDirectory) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            viewModel.upload(doc.name ?: "upload.bin", doc.length()) {
                                                context.contentResolver.openInputStream(doc.uri)
                                                    ?: error("Unable to open file")
                                            }
                                        }
                                    }) { Text("Upload") }
                                }
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Server", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = viewModel::navigateUp) { Text("Up") }
                    OutlinedButton(onClick = { viewModel.refreshRemote() }) { Text("Refresh") }
                }
                Text(uiState.remote.currentPath, style = MaterialTheme.typography.bodySmall)
                if (uiState.remote.isLoading) {
                    Text("Loading...")
                }
                uiState.remote.error?.let { Text("Error: $it") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(uiState.remote.entries, key = { it.path }) { entry ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(entry.name + if (entry.isDirectory) "/" else "")
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (entry.isDirectory) {
                                        OutlinedButton(onClick = { viewModel.openRemoteDirectory(entry) }) { Text("Open") }
                                    } else {
                                        OutlinedButton(onClick = {
                                            val target = localFolder?.createFile(
                                                "application/octet-stream",
                                                entry.name,
                                            )
                                            if (target != null) {
                                                viewModel.download(entry) {
                                                    context.contentResolver.openOutputStream(target.uri)
                                                        ?: error("Unable to open destination file")
                                                }
                                            }
                                        }) { Text("Download") }
                                    }
                                    OutlinedButton(onClick = { viewModel.deleteRemote(entry) }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Close") }
        }
    }
}
