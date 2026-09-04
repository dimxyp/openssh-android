package com.androssh.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androssh.app.data.backup.BackupManager
import com.androssh.app.data.backup.ImportConflict
import com.androssh.app.data.backup.ImportConflictResolution
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val isBusy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val pendingConflicts: List<ImportConflict> = emptyList(),
)

/** Drives the export/import settings screen backed by [BackupManager]. */
class BackupViewModel(
    private val backupManager: BackupManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun export(passphrase: String, output: () -> OutputStream) {
        _uiState.update { it.copy(isBusy = true, error = null, status = null) }
        viewModelScope.launch {
            runCatching { output().use { backupManager.exportEncrypted(passphrase.toCharArray(), it) } }
                .onSuccess { _uiState.update { it.copy(isBusy = false, status = "Export complete.") } }
                .onFailure { error -> _uiState.update { it.copy(isBusy = false, error = error.message) } }
        }
    }

    fun import(passphrase: String, input: () -> InputStream) {
        _uiState.update { it.copy(isBusy = true, error = null, status = null) }
        viewModelScope.launch {
            runCatching {
                val plan = input().use { backupManager.buildImportPlan(passphrase.toCharArray(), it) }
                plan.newProfiles.forEach { backupManager.importNewProfile(it) }
                plan
            }.onSuccess { plan ->
                val conflictNote = if (plan.conflicts.isNotEmpty()) {
                    " ${plan.conflicts.size} conflicting connection(s) need a decision below."
                } else {
                    ""
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        pendingConflicts = plan.conflicts,
                        status = "Imported ${plan.newProfiles.size} new connection(s).$conflictNote",
                    )
                }
            }.onFailure { error -> _uiState.update { it.copy(isBusy = false, error = error.message) } }
        }
    }

    fun resolveConflict(conflict: ImportConflict, resolution: ImportConflictResolution) {
        viewModelScope.launch {
            runCatching { backupManager.resolveConflict(conflict, resolution) }
                .onSuccess { _uiState.update { state -> state.copy(pendingConflicts = state.pendingConflicts - conflict) } }
                .onFailure { error -> _uiState.update { it.copy(error = error.message) } }
        }
    }
}

class BackupViewModelFactory(
    private val backupManager: BackupManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {
            return BackupViewModel(backupManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
