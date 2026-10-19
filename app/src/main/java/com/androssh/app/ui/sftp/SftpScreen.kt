package com.androssh.app.ui.sftp

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import com.androssh.app.ui.theme.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.androssh.app.data.sftp.SftpEntry
import com.androssh.app.viewmodel.SftpViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Material 3 remote SFTP browser: breadcrumb path, compact file rows with a
 * per-file overflow menu, long-press multi-selection, pull-to-refresh and a
 * FAB for upload / new folder. Downloads are written to a folder picked via
 * the Storage Access Framework.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SftpScreen(
    viewModel: SftpViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val remote = uiState.remote

    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fabExpanded by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SftpEntry?>(null) }
    var deleteTargets by remember { mutableStateOf<List<SftpEntry>>(emptyList()) }
    var pendingDownloads by remember { mutableStateOf<List<SftpEntry>>(emptyList()) }

    LaunchedEffect(remote.currentPath, remote.entries) { selected = emptySet() }

    val selectionMode = selected.isNotEmpty()
    BackHandler(enabled = selectionMode) { selected = emptySet() }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val entries = pendingDownloads
        pendingDownloads = emptyList()
        if (uri != null && entries.isNotEmpty()) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val folder = DocumentFile.fromTreeUri(context, uri)
            entries.filter { !it.isDirectory }.forEach { entry ->
                val target = folder?.createFile("application/octet-stream", entry.name)
                if (target != null) {
                    viewModel.download(entry) {
                        context.contentResolver.openOutputStream(target.uri)
                            ?: error("Unable to open destination file")
                    }
                }
            }
            selected = emptySet()
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectionMode) {
                val chosen = remote.entries.filter { it.path in selected }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selected = emptySet() }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear selection")
                    }
                    Text(
                        "${selected.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            pendingDownloads = chosen
                            pickFolderLauncher.launch(null)
                        },
                        enabled = chosen.any { !it.isDirectory },
                    ) { Icon(Icons.Default.Download, contentDescription = "Download") }
                    IconButton(onClick = { deleteTargets = chosen }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                    Text(
                        remote.profile?.name.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Breadcrumb(path = remote.currentPath, onNavigate = { viewModel.refreshRemote(it) })

            uiState.transfer?.let { progress ->
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(uiState.transferLabel.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { if (progress.totalBytes > 0) progress.percent / 100f else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (progress.totalBytes > 0) {
                                "${progress.percent}% - ${formatSize(progress.bytesTransferred)} / ${formatSize(progress.totalBytes)}" +
                                    if (progress.bytesPerSecond > 0) " - ${formatSize(progress.bytesPerSecond)}/s" else ""
                            } else {
                                formatSize(progress.bytesTransferred)
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::cancelTransfer) { Text("Cancel") }
                    }
                }
            }
            uiState.message?.let {
                Row(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.contains("failed", ignoreCase = true)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::dismissMessage) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            }
            remote.error?.let {
                Text(
                    "Error: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            HorizontalDivider()

            PullToRefreshBox(
                isRefreshing = remote.isLoading,
                onRefresh = { viewModel.refreshRemote() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(state = rememberLazyListState(), modifier = Modifier.fillMaxSize()) {
                    if (remote.currentPath != "/" && remote.currentPath.isNotBlank()) {
                        item(key = "..up") {
                            ListItem(
                                headlineContent = { Text("..") },
                                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                                modifier = Modifier.combinedClickable(onClick = viewModel::navigateUp),
                            )
                        }
                    }
                    items(remote.entries, key = { it.path }) { entry ->
                        val isSelected = entry.path in selected
                        FileRow(
                            entry = entry,
                            selected = isSelected,
                            selectionMode = selectionMode,
                            onClick = {
                                when {
                                    selectionMode -> selected = if (isSelected) selected - entry.path else selected + entry.path
                                    entry.isDirectory -> viewModel.openRemoteDirectory(entry)
                                }
                            },
                            onLongClick = { selected = selected + entry.path },
                            onDownload = {
                                pendingDownloads = listOf(entry)
                                pickFolderLauncher.launch(null)
                            },
                            onRename = { renameTarget = entry },
                            onDelete = { deleteTargets = listOf(entry) },
                        )
                    }
                    if (remote.entries.isEmpty() && !remote.isLoading) {
                        item(key = "..empty") {
                            Text(
                                "This folder is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }

        if (!selectionMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                if (fabExpanded) {
                    SmallFloatingActionButton(onClick = {
                        fabExpanded = false
                        showNewFolder = true
                    }) { Icon(Icons.Default.CreateNewFolder, contentDescription = "New folder") }
                    SmallFloatingActionButton(onClick = {
                        fabExpanded = false
                        pickFileLauncher.launch(arrayOf("*/*"))
                    }) { Icon(Icons.Default.UploadFile, contentDescription = "Upload file") }
                }
                FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                    Icon(
                        if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Upload or create",
                    )
                }
            }
        }
    }

    if (showNewFolder) {
        NameDialog(
            title = "New folder",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showNewFolder = false },
            onConfirm = {
                showNewFolder = false
                viewModel.makeRemoteDirectory(it)
            },
        )
    }
    renameTarget?.let { entry ->
        NameDialog(
            title = "Rename",
            initial = entry.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = {
                renameTarget = null
                viewModel.renameRemote(entry, it)
            },
        )
    }
    if (deleteTargets.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteTargets = emptyList() },
            title = { Text("Delete") },
            text = {
                Text(
                    if (deleteTargets.size == 1) "Delete ${deleteTargets.first().name}?"
                    else "Delete ${deleteTargets.size} items?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTargets.forEach(viewModel::deleteRemote)
                    deleteTargets = emptyList()
                    selected = emptySet()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTargets = emptyList() }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Breadcrumb(path: String, onNavigate: (String) -> Unit) {
    val segments = path.split('/').filter { it.isNotEmpty() }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            TextButton(onClick = { onNavigate("/") }) { Text("/") }
        }
        segments.forEachIndexed { index, segment ->
            item(key = "$index:$segment") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.width(16.dp),
                    )
                    TextButton(onClick = {
                        onNavigate("/" + segments.take(index + 1).joinToString("/"))
                    }) { Text(segment) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    entry: SftpEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val details = buildList {
        if (!entry.isDirectory) add(formatSize(entry.size))
        if (entry.modifiedEpochSeconds > 0) {
            add(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.modifiedEpochSeconds * 1000)))
        }
    }.joinToString(" · ")

    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (details.isNotEmpty()) {
            { Text(details, maxLines = 1) }
        } else {
            null
        },
        leadingContent = {
            Icon(
                if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
            )
        },
        trailingContent = if (selectionMode) {
            null
        } else {
            {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (!entry.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Download") },
                                onClick = { menuOpen = false; onDownload() },
                            )
                        }
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank() && '/' !in name,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
}
