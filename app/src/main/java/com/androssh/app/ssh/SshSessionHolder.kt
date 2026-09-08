package com.androssh.app.ssh

import com.androssh.app.data.HostProfile
import com.androssh.app.terminal.TerminalEmulator
import com.androssh.app.terminal.TerminalSnapshot
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-scoped owner of the one live SSH shell session.
 *
 * The session, its output-reading coroutine and the [TerminalEmulator] scrollback deliberately live
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
    // Initial size is only a placeholder: the UI measures the real viewport as soon as the
    // terminal screen is composed and calls [resizeTerminal] with the actual rows/cols.
    private val emulator = TerminalEmulator(rows = INITIAL_TERMINAL_ROWS, cols = INITIAL_TERMINAL_COLS)
    private val dirty = AtomicBoolean(false)

    private val _state = MutableStateFlow(SshSessionState())
    val state: StateFlow<SshSessionState> = _state.asStateFlow()

    private var session: ActiveSshSession? = null
    private var connectJob: Job? = null
    private var outputJob: Job? = null
    private var renderJob: Job? = null

    /** True when [profileId] is the profile of the session currently open (or being opened). */
    fun isSessionOpenFor(profileId: Long): Boolean = _state.value.profile?.id == profileId

    /**
     * Opens a shell for [profile], replacing any session that is currently open. Reconnecting is
     * safe: the previous session is closed first, so neither sockets nor notifications leak.
     */
    fun connect(profile: HostProfile, password: String?) {
        closeCurrentSession()
        emulator.reset()
        val startedAt = nowMillis()
        _state.value = SshSessionState(
            profile = profile,
            connected = false,
            startedAtMillis = startedAt,
            snapshot = emulator.snapshot(),
        )
        // Anchor the process lifetime for as long as the shell is open, so backgrounding the app
        // does not let Android tear down the socket or the output-reading coroutine.
        keepAlive?.start("${profile.username}@${profile.host}", startedAt)
        feed("Connecting to ${profile.username}@${profile.host}:${profile.port}...\r\n")
        startRenderLoop()
        connectJob = scope.launch {
            runCatching { connectionManager.openShell(profile, password, cols = emulator.cols, rows = emulator.rows) }
                .onSuccess { opened ->
                    session = opened
                    _state.update { it.copy(connected = true) }
                    feed("Connected.\r\n")
                    outputJob = opened.readOutput(scope) { output -> feed(output) }
                }
                .onFailure { error ->
                    keepAlive?.stop()
                    _state.update { it.copy(connected = false) }
                    feed("Connection failed: ${error.message ?: error::class.simpleName}\r\n")
                }
        }
    }

    /**
     * Applies a newly measured terminal size: resizes the local screen buffer and tells the server
     * about the new window dimensions so the shell's line editing and full-screen programs stay in
     * sync with what is actually visible.
     */
    fun resizeTerminal(rows: Int, cols: Int) {
        if (rows <= 0 || cols <= 0) return
        if (rows == emulator.rows && cols == emulator.cols) return
        emulator.resize(rows, cols)
        dirty.set(true)
        publishSnapshot()
        val current = session ?: return
        scope.launch { runCatching { current.resize(cols = cols, rows = rows) } }
    }

    /** Writes raw bytes to the shell channel, e.g. live keystrokes or extra-key sequences. */
    fun send(input: String) {
        if (input.isEmpty()) return
        val current = session ?: return
        scope.launch {
            runCatching { current.sendInput(input) }
                .onFailure { feed("Send failed: ${it.message ?: it::class.simpleName}\r\n") }
        }
    }

    /** Closes the session (if any), stops the keep-alive service and clears the published state. */
    fun disconnect() {
        closeCurrentSession()
        keepAlive?.stop()
        _state.value = SshSessionState(snapshot = emulator.snapshot())
    }

    private fun closeCurrentSession() {
        renderJob?.cancel()
        renderJob = null
        connectJob?.cancel()
        connectJob = null
        outputJob?.cancel()
        outputJob = null
        session?.close()
        session = null
    }

    private fun feed(text: String) {
        emulator.feed(text)
        dirty.set(true)
    }

    /**
     * Publishes the emulator snapshot at most every [TERMINAL_RENDER_INTERVAL_MS] while there is
     * unpublished output, instead of once per incoming chunk, so Compose recomposition work stays
     * bounded even when the remote shell streams output very quickly (e.g. `top`).
     */
    private fun startRenderLoop() {
        renderJob?.cancel()
        publishSnapshot()
        renderJob = scope.launch {
            while (isActive) {
                delay(TERMINAL_RENDER_INTERVAL_MS)
                if (dirty.compareAndSet(true, false)) {
                    publishSnapshot()
                }
            }
        }
    }

    private fun publishSnapshot() {
        _state.update { it.copy(snapshot = emulator.snapshot()) }
    }

    private companion object {
        const val INITIAL_TERMINAL_ROWS = 24
        const val INITIAL_TERMINAL_COLS = 80
        const val TERMINAL_RENDER_INTERVAL_MS = 50L
    }
}

/** Snapshot of the single live SSH session, observed by the UI and by the notification. */
data class SshSessionState(
    val profile: HostProfile? = null,
    val connected: Boolean = false,
    val startedAtMillis: Long = 0L,
    val snapshot: TerminalSnapshot = TerminalSnapshot(rows = emptyList(), cursorRow = 0, cursorCol = 0),
)