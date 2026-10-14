package com.androssh.app.ssh

import com.androssh.app.data.HostProfile
import com.androssh.app.ui.terminal.TerminalColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.connectbot.terminal.TerminalDimensions
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory

/**
 * Process-scoped owner of the one live SSH shell session.
 *
 * The session, its output-reading coroutine and the terminal emulator's scrollback deliberately live
 * here - in a singleton held by [com.androssh.app.AndroSshApplication] and kept alive by
 * [SshForegroundService] - rather than in a `ViewModel`. That way destroying the Activity/ViewModel
 * (backgrounding, rotation, ...) cannot cancel the reader coroutine or close the socket: returning
 * to the app simply re-observes [state] and finds the same session with its buffer intact.
 *
 * The UI layer only ever observes [state] and calls [connect]/[send]/[disconnect].
 */
class SshSessionHolder(
    private val connectionManager: SshConnectionManager,
    private val keepAlive: SshSessionKeepAlive? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var rows = INITIAL_TERMINAL_ROWS

    @Volatile
    private var cols = INITIAL_TERMINAL_COLS

    // Keystrokes are queued and written by a single consumer so they always reach the shell in the
    // order they were typed (one coroutine per key could be reordered on the IO dispatcher).
    private val inputQueue = Channel<ByteArray>(Channel.UNLIMITED)

    /**
     * The terminal emulator and its Compose view share this instance. Its initial size is only a
     * placeholder: the view reports the real size once it is laid out, which arrives through
     * `onResize` and is forwarded to the remote PTY.
     */
    val emulator: TerminalEmulator = TerminalEmulatorFactory.create(
        initialRows = INITIAL_TERMINAL_ROWS,
        initialCols = INITIAL_TERMINAL_COLS,
        defaultForeground = TerminalColors.Foreground,
        defaultBackground = TerminalColors.Background,
        onKeyboardInput = { data -> send(data) },
        onResize = { dimensions -> onTerminalResized(dimensions) },
    )

    private val _state = MutableStateFlow(SshSessionState())
    val state: StateFlow<SshSessionState> = _state.asStateFlow()

    @Volatile
    private var session: ActiveSshSession? = null
    private var connectJob: Job? = null
    private var outputJob: Job? = null

    init {
        scope.launch {
            for (data in inputQueue) {
                val current = session ?: continue
                runCatching { current.sendInput(data) }
                    .onFailure { feed("Send failed: ${it.message ?: it::class.simpleName}\r\n") }
            }
        }
    }

    /** True when [profileId] is the profile of the session currently open (or being opened). */
    fun isSessionOpenFor(profileId: Long): Boolean = _state.value.profile?.id == profileId

    /**
     * Opens a shell for [profile], replacing any session that is currently open. Reconnecting is
     * safe: the previous session is closed first, so neither sockets nor notifications leak.
     */
    fun connect(profile: HostProfile, password: String?) {
        closeCurrentSession()
        feed("\u001Bc")
        val startedAt = nowMillis()
        _state.value = SshSessionState(
            profile = profile,
            connected = false,
            startedAtMillis = startedAt,
        )
        // Anchor the process lifetime for as long as the shell is open, so backgrounding the app
        // does not let Android tear down the socket or the output-reading coroutine.
        keepAlive?.start("${profile.username}@${profile.host}", startedAt)
        feed("Connecting to ${profile.username}@${profile.host}:${profile.port}...\r\n")
        connectJob = scope.launch {
            runCatching { connectionManager.openShell(profile, password, cols = cols, rows = rows) }
                .onSuccess { opened ->
                    session = opened
                    // The viewport may have been re-measured while the connection was being set up
                    // (e.g. the keyboard opened), in which case the PTY was allocated with a size
                    // that is already stale - reconcile it now.
                    runCatching { opened.resize(cols = cols, rows = rows) }
                    _state.update { it.copy(connected = true) }
                    feed("Connected.\r\n")
                    outputJob = opened.readOutput(scope) { output -> emulator.writeInput(output) }
                }
                .onFailure { error ->
                    keepAlive?.stop()
                    _state.update { it.copy(connected = false) }
                    feed("Connection failed: ${error.message ?: error::class.simpleName}\r\n")
                }
        }
    }

    /**
     * Called when the terminal view is laid out at a new size: tells the server about the new
     * window dimensions (SSH `window-change`) so the shell's line editing and full-screen programs
     * stay in sync with what is actually visible.
     */
    private fun onTerminalResized(dimensions: TerminalDimensions) {
        if (dimensions.rows <= 0 || dimensions.columns <= 0) return
        rows = dimensions.rows
        cols = dimensions.columns
        val current = session ?: return
        // Always report the latest size rather than the captured one, so two resizes racing each
        // other cannot leave the server with stale dimensions.
        scope.launch { runCatching { current.resize(cols = cols, rows = rows) } }
    }

    /** Queues raw bytes for the shell channel, e.g. live keystrokes or extra-key sequences. */
    fun send(input: ByteArray) {
        if (input.isEmpty() || session == null) return
        inputQueue.trySend(input)
    }

    fun send(input: String) = send(input.toByteArray())

    /** Closes the session (if any), stops the keep-alive service and clears the published state. */
    fun disconnect() {
        closeCurrentSession()
        keepAlive?.stop()
        _state.value = SshSessionState()
    }

    private fun closeCurrentSession() {
        connectJob?.cancel()
        connectJob = null
        outputJob?.cancel()
        outputJob = null
        session?.close()
        session = null
    }

    private fun feed(text: String) {
        emulator.writeInput(text.toByteArray())
    }

    private companion object {
        const val INITIAL_TERMINAL_ROWS = 24
        const val INITIAL_TERMINAL_COLS = 80
    }
}

/** Snapshot of the single live SSH session, observed by the UI and by the notification. */
data class SshSessionState(
    val profile: HostProfile? = null,
    val connected: Boolean = false,
    val startedAtMillis: Long = 0L,
)