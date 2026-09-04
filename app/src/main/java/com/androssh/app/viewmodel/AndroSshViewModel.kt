package com.androssh.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androssh.app.data.AuthMethod
import com.androssh.app.data.ConnectionRepository
import com.androssh.app.data.HostProfile
import com.androssh.app.ssh.ActiveSshSession
import com.androssh.app.ssh.SshConnectionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AndroSshViewModel(
    private val repository: ConnectionRepository,
    private val sshConnectionManager: SshConnectionManager,
) : ViewModel() {
    val profiles: StateFlow<List<HostProfile>> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(AndroSshUiState())
    val uiState: StateFlow<AndroSshUiState> = _uiState.asStateFlow()

    private var activeSession: ActiveSshSession? = null
    private var outputJob: Job? = null

    fun openNewProfileForm() {
        _uiState.value = AndroSshUiState(screen = Screen.EditConnection, form = ConnectionFormState())
    }

    fun openEditProfileForm(profile: HostProfile) {
        _uiState.value = AndroSshUiState(
            screen = Screen.EditConnection,
            form = ConnectionFormState.fromProfile(profile),
        )
    }

    fun openSftp(profile: HostProfile) {
        _uiState.value = AndroSshUiState(screen = Screen.Sftp, selectedProfile = profile)
    }

    fun openBackup() {
        _uiState.value = AndroSshUiState(screen = Screen.Backup)
    }

    fun showConnectionList() {
        closeSession()
        _uiState.value = AndroSshUiState(screen = Screen.ConnectionList)
    }

    fun updateForm(update: ConnectionFormState.() -> ConnectionFormState) {
        _uiState.update { it.copy(form = it.form.update()) }
    }

    fun saveCurrentProfile() {
        val form = uiState.value.form
        val port = form.port.toIntOrNull()
        if (form.host.isBlank() || form.username.isBlank() || port == null) {
            _uiState.update { it.copy(message = "Host, username, and numeric port are required.") }
            return
        }

        viewModelScope.launch {
            val profile = HostProfile(
                id = form.id,
                name = form.name.ifBlank { form.host },
                host = form.host.trim(),
                port = port,
                username = form.username.trim(),
                authMethod = form.authMethod,
                privateKeyAlias = form.privateKeyAlias.ifBlank { null },
                hasSavedPassword = form.password.isNotBlank(),
            )
            repository.saveProfile(profile, form.password.takeIf { it.isNotBlank() })
            _uiState.value = AndroSshUiState(screen = Screen.ConnectionList, message = "Connection saved.")
        }
    }

    fun deleteProfile(profile: HostProfile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            _uiState.update { it.copy(message = "Connection deleted.") }
        }
    }

    fun connect(profile: HostProfile) {
        closeSession()
        _uiState.value = AndroSshUiState(
            screen = Screen.Terminal,
            selectedProfile = profile,
            terminal = TerminalState(lines = listOf("Connecting to ${profile.username}@${profile.host}:${profile.port}...")),
        )
        viewModelScope.launch {
            runCatching {
                sshConnectionManager.openShell(profile, repository.getPassword(profile.id))
            }.onSuccess { session ->
                activeSession = session
                appendTerminalLine("Connected. Basic shell stream is active.")
                outputJob = session.readOutput(viewModelScope) { output -> appendTerminalText(output) }
            }.onFailure { error ->
                appendTerminalLine("Connection failed: ${error.message ?: error::class.simpleName}")
            }
        }
    }

    /** Connects directly to a saved profile by id, e.g. when launched from the home-screen widget. */
    fun connectByProfileId(profileId: Long) {
        viewModelScope.launch {
            repository.getProfile(profileId)?.let { profile -> connect(profile) }
        }
    }

    /** Looks up the saved password for a profile, e.g. to hand off to the SFTP view model. */
    fun getPasswordFor(profile: HostProfile): String? = repository.getPassword(profile.id)

    fun sendTerminalInput(input: String) {
        if (input.isEmpty()) return
        val session = activeSession
        viewModelScope.launch {
            runCatching { session?.sendInput(input) }
                .onFailure { appendTerminalLine("Send failed: ${it.message ?: it::class.simpleName}") }
        }
    }

    private fun appendTerminalLine(line: String) {
        appendTerminalText("\n$line\n")
    }

    private fun appendTerminalText(text: String) {
        _uiState.update { state ->
            state.copy(terminal = state.terminal.copy(lines = state.terminal.lines + text))
        }
    }

    private fun closeSession() {
        outputJob?.cancel()
        outputJob = null
        activeSession?.close()
        activeSession = null
    }

    override fun onCleared() {
        closeSession()
        super.onCleared()
    }
}

class AndroSshViewModelFactory(
    private val repository: ConnectionRepository,
    private val sshConnectionManager: SshConnectionManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AndroSshViewModel::class.java)) {
            return AndroSshViewModel(repository, sshConnectionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

data class AndroSshUiState(
    val screen: Screen = Screen.ConnectionList,
    val selectedProfile: HostProfile? = null,
    val form: ConnectionFormState = ConnectionFormState(),
    val terminal: TerminalState = TerminalState(),
    val message: String? = null,
)

enum class Screen {
    ConnectionList,
    EditConnection,
    Terminal,
    Sftp,
    Backup,
}

data class ConnectionFormState(
    val id: Long = 0,
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authMethod: AuthMethod = AuthMethod.Password,
    val password: String = "",
    val privateKeyAlias: String = "",
) {
    companion object {
        fun fromProfile(profile: HostProfile) = ConnectionFormState(
            id = profile.id,
            name = profile.name,
            host = profile.host,
            port = profile.port.toString(),
            username = profile.username,
            authMethod = profile.authMethod,
            privateKeyAlias = profile.privateKeyAlias.orEmpty(),
        )
    }
}

data class TerminalState(
    val lines: List<String> = emptyList(),
    val pendingInput: String = "",
)
