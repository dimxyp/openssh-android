package com.androssh.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androssh.app.data.HostProfile
import com.androssh.app.data.sftp.SftpEntry
import com.androssh.app.data.sftp.SftpRepository
import com.androssh.app.data.sftp.TransferCancelledException
import com.androssh.app.data.sftp.TransferProgress
import com.androssh.app.ssh.SftpSession
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One side of the dual-pane SFTP browser: either "local" or a connected remote server. */
data class SftpPaneState(
    val profile: HostProfile? = null,
    val currentPath: String = "/",
    val entries: List<SftpEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class SftpUiState(
    val remote: SftpPaneState = SftpPaneState(),
    val transfer: TransferProgress? = null,
    val transferLabel: String? = null,
    val message: String? = null,
)

/**
 * Drives the SFTP browser screen(s). Wraps [SftpRepository] and exposes UI
 * state for a remote pane; local-side browsing (Storage Access Framework /
 * MediaStore) is driven directly from the Compose layer via
 * `ActivityResultContracts`, and local files are handed to this view model
 * as plain [InputStream]/[OutputStream] pairs obtained from a
 * `ContentResolver`, keeping this class platform-storage agnostic.
 */
class SftpViewModel(
    private val repository: SftpRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SftpUiState())
    val uiState: StateFlow<SftpUiState> = _uiState.asStateFlow()

    private var remoteSession: SftpSession? = null
    private var secondarySession: SftpSession? = null
    private var transferJob: Job? = null
    @Volatile private var transferCancelled: Boolean = false

    fun connect(profile: HostProfile, password: String?) {
        _uiState.update { it.copy(remote = SftpPaneState(profile = profile, isLoading = true)) }
        viewModelScope.launch {
            runCatching {
                closeRemoteSession()
                remoteSession = repository.connect(profile, password)
                refreshRemote("/")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(remote = it.remote.copy(isLoading = false, error = error.message))
                }
            }
        }
    }

    fun refreshRemote(path: String = uiState.value.remote.currentPath) {
        val session = remoteSession ?: return
        _uiState.update { it.copy(remote = it.remote.copy(isLoading = true, error = null)) }
        viewModelScope.launch {
            runCatching { repository.list(session, path) }
                .onSuccess { entries ->
                    _uiState.update {
                        it.copy(remote = it.remote.copy(currentPath = path, entries = entries, isLoading = false))
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(remote = it.remote.copy(isLoading = false, error = error.message))
                    }
                }
        }
    }

    fun openRemoteDirectory(entry: SftpEntry) {
        if (entry.isDirectory) refreshRemote(entry.path)
    }

    fun navigateUp() {
        val current = uiState.value.remote.currentPath
        if (current == "/" || current.isBlank()) return
        val parent = current.trimEnd('/').substringBeforeLast('/', "/")
        refreshRemote(parent.ifBlank { "/" })
    }

    fun makeRemoteDirectory(name: String) {
        val session = remoteSession ?: return
        val path = joinPath(uiState.value.remote.currentPath, name)
        viewModelScope.launch {
            runCatching { repository.mkdir(session, path) }
                .onSuccess { refreshRemote() }
                .onFailure { error -> _uiState.update { it.copy(message = "Create folder failed: ${error.message}") } }
        }
    }

    fun deleteRemote(entry: SftpEntry) {
        val session = remoteSession ?: return
        viewModelScope.launch {
            runCatching { repository.delete(session, entry) }
                .onSuccess { refreshRemote() }
                .onFailure { error -> _uiState.update { it.copy(message = "Delete failed: ${error.message}") } }
        }
    }

    fun renameRemote(entry: SftpEntry, newName: String) {
        val session = remoteSession ?: return
        val newPath = joinPath(uiState.value.remote.currentPath, newName)
        viewModelScope.launch {
            runCatching { repository.rename(session, entry.path, newPath) }
                .onSuccess { refreshRemote() }
                .onFailure { error -> _uiState.update { it.copy(message = "Rename failed: ${error.message}") } }
        }
    }

    /** Uploads a locally-picked file (device -> server). */
    fun upload(fileName: String, size: Long, input: () -> InputStream) {
        val session = remoteSession ?: return
        val remotePath = joinPath(uiState.value.remote.currentPath, fileName)
        startTransfer("Uploading $fileName") {
            input().use { stream ->
                repository.upload(session, stream, remotePath, size, ::isTransferCancelled) { progress ->
                    publishProgress(progress)
                }
            }
            refreshRemote()
        }
    }

    /** Downloads the selected remote file (server -> device) into a caller-provided sink. */
    fun download(entry: SftpEntry, output: () -> OutputStream) {
        val session = remoteSession ?: return
        startTransfer("Downloading ${entry.name}") {
            output().use { stream ->
                repository.download(session, entry, stream, ::isTransferCancelled) { progress ->
                    publishProgress(progress)
                }
            }
        }
    }

    /**
     * Streams [entry] from the currently connected server to [destinationProfile]
     * via this device ("transfer via device"), since SFTP has no direct
     * server-to-server copy.
     */
    fun transferToOtherServer(entry: SftpEntry, destinationProfile: HostProfile, destinationPassword: String?, destinationPath: String) {
        val source = remoteSession ?: return
        startTransfer("Transferring ${entry.name} via device") {
            val destination = repository.connect(destinationProfile, destinationPassword)
            secondarySession = destination
            try {
                repository.transferBetweenServers(source, entry, destination, destinationPath, ::isTransferCancelled) { progress ->
                    publishProgress(progress)
                }
            } finally {
                destination.close()
                secondarySession = null
            }
        }
    }

    fun cancelTransfer() {
        transferCancelled = true
    }

    private fun isTransferCancelled(): Boolean = transferCancelled

    private fun startTransfer(label: String, block: suspend () -> Unit) {
        transferJob?.cancel()
        transferCancelled = false
        _uiState.update { it.copy(transferLabel = label, transfer = TransferProgress(0, 0)) }
        transferJob = viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _uiState.update { it.copy(transfer = null, transferLabel = null) } }
                .onFailure { error ->
                    val message = if (error is TransferCancelledException) "Transfer cancelled" else "Transfer failed: ${error.message}"
                    _uiState.update { it.copy(transfer = null, transferLabel = null, message = message) }
                }
        }
    }

    private fun publishProgress(progress: TransferProgress) {
        _uiState.update { it.copy(transfer = progress) }
    }

    private fun joinPath(directory: String, name: String): String {
        val base = if (directory.endsWith("/")) directory else "$directory/"
        return "$base$name"
    }

    private fun closeRemoteSession() {
        remoteSession?.close()
        remoteSession = null
    }

    override fun onCleared() {
        transferJob?.cancel()
        closeRemoteSession()
        secondarySession?.close()
        super.onCleared()
    }
}

class SftpViewModelFactory(
    private val repository: SftpRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SftpViewModel::class.java)) {
            return SftpViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
