package com.androssh.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.androssh.app.data.AuthMethod
import com.androssh.app.data.ConnectionRepository
import com.androssh.app.data.HostProfile
import com.androssh.app.ssh.NetworkReachabilityChecker
import com.androssh.app.ssh.SshSessionHolder
import com.androssh.app.terminal.TerminalSnapshot
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

/**
 * UI state holder for the whole app. It deliberately does *not* own the live SSH session: that
 * lives in the process-scoped [SshSessionHolder] (kept alive by the foreground service), which this
 * view model only observes and drives. Destroying this view model therefore never closes the shell.
 */
class AndroSshViewModel(
    private val repository: ConnectionRepository,
    private val sessionHolder: SshSessionHolder,
    private val reachabilityChecker: NetworkReachabilityChecker = NetworkReachabilityChecker(),
) : ViewModel() {
    val profiles: StateFlow<List<HostProfile>> = repository.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(AndroSshUiState())
    val uiState: StateFlow<AndroSshUiState> = _uiState.asStateFlow()

    private var reachabilityJob: Job? = null
    private var reachabilityTargets = emptyList<ReachabilityTarget>()

    init {
        // Returning to a still-running session (app resumed, Activity recreated, notification
        // tapped) must resume it rather than reconnect, so start straight on the terminal screen.
        sessionHolder.state.value.profile?.let { profile ->
            _uiState.value = AndroSshUiState(screen = Screen.Terminal, selectedProfile = profile)
        }
        viewModelScope.launch {
            sessionHolder.state.collect { session ->
                _uiState.update { state ->
                    if (session.profile == null && state.screen == Screen.Terminal) {
                        // The session was closed elsewhere, e.g. via the notification action.
                        AndroSshUiState(screen = Screen.ConnectionList)
                    } else {
                        state.copy(terminal = TerminalState(snapshot = session.snapshot))
                    }
                }
            }
        }
    }

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
        sessionHolder.disconnect()
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
        _uiState.value = AndroSshUiState(
            screen = Screen.Terminal,
            selectedProfile = profile,
            terminal = TerminalState(snapshot = sessionHolder.state.value.snapshot),
        )
        // Reconnecting to the host we are already on would throw away a perfectly good shell (and
        // its scrollback), so only open a new session when this really is a different one.
        if (sessionHolder.isSessionOpenFor(profile.id)) return
        sessionHolder.connect(profile, repository.getPassword(profile.id))
    }

    /**
     * Connects directly to a saved profile by id, e.g. when launched from the home-screen widget.
     * Resumes the existing session when that profile is already connected.
     */
    fun connectByProfileId(profileId: Long) {
        viewModelScope.launch {
            repository.getProfile(profileId)?.let { profile -> connect(profile) }
        }
    }

    /** Looks up the saved password for a profile, e.g. to hand off to the SFTP view model. */
    fun getPasswordFor(profile: HostProfile): String? = repository.getPassword(profile.id)

    /** Sends raw input straight to the shell channel, e.g. live keystrokes or [com.androssh.app.ui.terminal.ExtraKeysBar] sequences. */
    fun sendTerminalInput(input: String) {
        sessionHolder.send(input)
    }

    /**
     * Reports the terminal size measured by the UI (viewport size / monospace glyph metrics), which
     * resizes the emulator's screen buffer and the remote PTY. Called again whenever the visible
     * area changes: rotation, split-screen, and the soft keyboard opening or closing.
     */
    fun onTerminalSizeChanged(rows: Int, cols: Int) {
        sessionHolder.resizeTerminal(rows = rows, cols = cols)
    }

    /**
     * Only the reachability polling is tied to this view model's lifetime; the SSH session
     * deliberately is not, so backgrounding the app (which may destroy the Activity and this view
     * model) leaves the shell running inside [SshSessionHolder].
     */
    override fun onCleared() {
        reachabilityJob?.cancel()
        super.onCleared()
    }

    private companion object {
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
    private val sessionHolder: SshSessionHolder,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AndroSshViewModel::class.java)) {
            return AndroSshViewModel(
                repository = repository,
                sessionHolder = sessionHolder,
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
