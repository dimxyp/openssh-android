package com.androssh.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androssh.app.data.AuthMethod
import com.androssh.app.data.ConnectionRepository
import com.androssh.app.data.HostProfile
import com.androssh.app.ssh.ActiveSshSession
import com.androssh.app.ssh.NetworkReachabilityChecker
import com.androssh.app.ssh.SshConnectionManager
import com.androssh.app.ssh.SshSessionKeepAlive
import com.androssh.app.terminal.TerminalEmulator
import com.androssh.app.terminal.TerminalSnapshot
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroSshViewModel(
    private val repository: ConnectionRepository,
    private val sshConnectionManager: SshConnectionManager,
    private val reachabilityChecker: NetworkReachabilityChecker = NetworkReachabilityChecker(),
    private val keepAlive: SshSessionKeepAlive? = null,
) : ViewModel() {
    val profiles: StateFlow<List<HostProfile>> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(AndroSshUiState())
    val uiState: StateFlow<AndroSshUiState> = _uiState.asStateFlow()

    private var activeSession: ActiveSshSession? = null
    private var outputJob: Job? = null
    private var renderJob: Job? = null
    private var reachabilityJob: Job? = null
    private var reachabilityTargets = emptyList<ReachabilityTarget>()

    /**
     * Parses incoming shell bytes into a screen buffer. Every byte the shell writes is fed into
     * this emulator; the resulting [TerminalSnapshot] is what the Compose UI renders. Snapshots
     * are only published to [uiState] on a throttled interval (see [startRenderLoop]) so bursts
     * of output (e.g. `top` redrawing) don't trigger a recomposition per byte.
     */
    private val terminalEmulator = TerminalEmulator(rows = TERMINAL_ROWS, cols = TERMINAL_COLS)
    private val terminalDirty = AtomicBoolean(false)

    fun openNewProfileForm() {
        _uiState.value = AndroSshUiState(screen = Screen.EditConnection, form = ConnectionFormState())
    }

    /**
     * Opens the edit form for [profile], pre-filling the previously saved password (if any) so the
     * user can see/correct it instead of the field appearing blank. The password itself never
     * touches the Room database; it's read straight from the [ConnectionRepository]-backed
     * encrypted store for this one profile.
     */
    fun openEditProfileForm(profile: HostProfile) {
        val savedPassword = if (profile.authMethod == AuthMethod.Password) {
            repository.getPassword(profile.id).orEmpty()
        } else {
            ""
        }
        _uiState.value = AndroSshUiState(
            screen = Screen.EditConnection,
            form = ConnectionFormState.fromProfile(profile).copy(password = savedPassword),
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

    fun monitorReachability(profiles: List<HostProfile>, enabled: Boolean) {
        val targets = if (enabled) {
            profiles.map { ReachabilityTarget(it.id, it.host, it.port) }
        } else {
            emptyList()
        }
        if (targets == reachabilityTargets) return

        reachabilityTargets = targets
        reachabilityJob?.cancel()
        reachabilityJob = null
        val targetIds = targets.mapTo(mutableSetOf()) { it.profileId }
        _uiState.update { state ->
            state.copy(reachability = state.reachability.filterKeys { it in targetIds })
        }
        if (targets.isEmpty()) return

        reachabilityJob = viewModelScope.launch {
            while (isActive) {
                _uiState.update { state ->
                    state.copy(
                        reachability = state.reachability + targets.associate {
                            it.profileId to ReachabilityStatus.Checking
                        },
                    )
                }
                coroutineScope {
                    targets.forEach { target ->
                        launch {
                            val reachable = reachabilityChecker.isReachable(target.host, target.port)
                            if (target in reachabilityTargets) {
                                _uiState.update { state ->
                                    state.copy(
                                        reachability = state.reachability + (
                                            target.profileId to if (reachable) {
                                                ReachabilityStatus.Reachable
                                            } else {
                                                ReachabilityStatus.Unreachable
                                            }
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                delay(REACHABILITY_INTERVAL_MS)
            }
        }
    }

    fun connect(profile: HostProfile) {
        closeSession()
        terminalEmulator.reset()
        _uiState.value = AndroSshUiState(
            screen = Screen.Terminal,
            selectedProfile = profile,
        )
        // Anchor the process lifetime for as long as the shell is open, so backgrounding the app
        // does not tear down the socket or the output-reading coroutine.
        keepAlive?.start("${profile.username}@${profile.host}")
        feedTerminal("Connecting to ${profile.username}@${profile.host}:${profile.port}...\r\n")
        startRenderLoop()
        viewModelScope.launch {
            runCatching {
                sshConnectionManager.openShell(profile, repository.getPassword(profile.id))
            }.onSuccess { session ->
                activeSession = session
                feedTerminal("Connected.\r\n")
                outputJob = session.readOutput(viewModelScope) { output -> feedTerminal(output) }
            }.onFailure { error ->
                keepAlive?.stop()
                feedTerminal("Connection failed: ${error.message ?: error::class.simpleName}\r\n")
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

    /** Sends raw input straight to the shell channel, e.g. live keystrokes or [com.androssh.app.ui.terminal.ExtraKeysBar] sequences. */
    fun sendTerminalInput(input: String) {
        if (input.isEmpty()) return
        val session = activeSession
        viewModelScope.launch {
            runCatching { session?.sendInput(input) }
                .onFailure { feedTerminal("Send failed: ${it.message ?: it::class.simpleName}\r\n") }
        }
    }

    private fun feedTerminal(text: String) {
        terminalEmulator.feed(text)
        terminalDirty.set(true)
    }

    /**
     * Publishes [terminalEmulator]'s snapshot to [uiState] at most every [TERMINAL_RENDER_INTERVAL_MS]
     * while there is unpublished output, instead of once per incoming chunk. This keeps Compose
     * recomposition work bounded even when the remote shell streams output very quickly (e.g. `top`
     * or `cat` on a large file).
     */
    private fun startRenderLoop() {
        renderJob?.cancel()
        publishTerminalSnapshot()
        renderJob = viewModelScope.launch {
            while (isActive) {
                delay(TERMINAL_RENDER_INTERVAL_MS)
                if (terminalDirty.compareAndSet(true, false)) {
                    publishTerminalSnapshot()
                }
            }
        }
    }

    private fun publishTerminalSnapshot() {
        _uiState.update { it.copy(terminal = TerminalState(snapshot = terminalEmulator.snapshot())) }
    }

    private fun closeSession() {
        renderJob?.cancel()
        renderJob = null
        outputJob?.cancel()
        outputJob = null
        activeSession?.close()
        activeSession = null
        keepAlive?.stop()
    }

    override fun onCleared() {
        reachabilityJob?.cancel()
        closeSession()
        super.onCleared()
    }

    private companion object {
        const val TERMINAL_ROWS = 30
        const val TERMINAL_COLS = 100
        const val TERMINAL_RENDER_INTERVAL_MS = 50L
        const val REACHABILITY_INTERVAL_MS = 30_000L
    }
}

private data class ReachabilityTarget(
    val profileId: Long,
    val host: String,
    val port: Int,
)

class AndroSshViewModelFactory(
    private val repository: ConnectionRepository,
    private val sshConnectionManager: SshConnectionManager,
    private val keepAlive: SshSessionKeepAlive? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AndroSshViewModel::class.java)) {
            return AndroSshViewModel(
                repository = repository,
                sshConnectionManager = sshConnectionManager,
                keepAlive = keepAlive,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

data class AndroSshUiState(
    val screen: Screen = Screen.ConnectionList,
    val selectedProfile: HostProfile? = null,
    val form: ConnectionFormState = ConnectionFormState(),
    val terminal: TerminalState = TerminalState(),
    val reachability: Map<Long, ReachabilityStatus> = emptyMap(),
    val message: String? = null,
)

enum class ReachabilityStatus {
    Unknown,
    Checking,
    Reachable,
    Unreachable,
}

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
    val snapshot: TerminalSnapshot = TerminalSnapshot(rows = emptyList(), cursorRow = 0, cursorCol = 0),
)
