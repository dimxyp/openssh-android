package com.androssh.app.ui

import android.app.Activity
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.androssh.app.data.AuthMethod
import com.androssh.app.data.HostProfile
import com.androssh.app.ui.backup.BackupScreen
import com.androssh.app.ui.sftp.SftpScreen
import com.androssh.app.ui.terminal.EditAction
import com.androssh.app.ui.terminal.ExtraKeysBar
import com.androssh.app.ui.terminal.TerminalColors
import com.androssh.app.ui.terminal.TerminalGrid
import com.androssh.app.viewmodel.AndroSshViewModel
import com.androssh.app.viewmodel.BackupViewModel
import com.androssh.app.viewmodel.ConnectionFormState
import com.androssh.app.viewmodel.ReachabilityStatus
import com.androssh.app.viewmodel.Screen
import com.androssh.app.viewmodel.SftpViewModel
import com.androssh.app.viewmodel.TerminalState
import kotlinx.coroutines.delay

@Composable
fun AndroSshApp(
    viewModel: AndroSshViewModel,
    sftpViewModel: SftpViewModel,
    backupViewModel: BackupViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val profiles by viewModel.profiles.collectAsState()

    LaunchedEffect(uiState.screen, profiles) {
        viewModel.monitorReachability(profiles, uiState.screen == Screen.ConnectionList)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold { padding ->
                // The terminal screen drops the app title and outer padding so the grid and the
                // extra keys bar get essentially the whole screen: it deliberately keeps only the
                // bottom (navigation bar) inset and draws edge-to-edge behind the status bar, with
                // the floating overlay applying the status-bar inset itself. Every other screen
                // gets the full Scaffold insets plus the usual content padding.
                val isTerminal = uiState.screen == Screen.Terminal
                val density = LocalDensity.current
                // Extra bottom room needed while the soft keyboard is up. The navigation-bar inset
                // is already covered by the Scaffold padding below and is part of the IME inset, so
                // only the difference is added - that way the terminal area shrinks to sit exactly
                // on top of the keyboard instead of being pushed off-screen.
                val imeBottomPadding = with(density) {
                    (WindowInsets.ime.getBottom(this) - WindowInsets.navigationBars.getBottom(this))
                        .coerceAtLeast(0)
                        .toDp()
                }
                Column(
                    modifier = Modifier
                        .then(
                            if (isTerminal) {
                                Modifier.padding(
                                    bottom = padding.calculateBottomPadding() + imeBottomPadding,
                                )
                            } else {
                                Modifier.padding(padding).padding(16.dp)
                            },
                        )
                        .fillMaxSize(),
                ) {
                    if (!isTerminal) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = "AndroSSH", style = MaterialTheme.typography.headlineMedium)
                            if (uiState.screen == Screen.ConnectionList) {
                                TextButton(onClick = viewModel::openBackup) { Text("Backup") }
                            }
                        }
                    }
                    if (!isTerminal) {
                        uiState.message?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = it, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    when (uiState.screen) {
                        Screen.ConnectionList -> ConnectionListScreen(
                            profiles = profiles,
                            reachability = uiState.reachability,
                            onAdd = viewModel::openNewProfileForm,
                            onEdit = viewModel::openEditProfileForm,
                            onDelete = viewModel::deleteProfile,
                            onConnect = viewModel::connect,
                            onOpenSftp = viewModel::openSftp,
                        )

                        Screen.EditConnection -> EditConnectionScreen(
                            form = uiState.form,
                            onFormChange = viewModel::updateForm,
                            onSave = viewModel::saveCurrentProfile,
                            onCancel = viewModel::showConnectionList,
                        )

                        Screen.Terminal -> TerminalScreen(
                            profile = uiState.selectedProfile,
                            terminal = uiState.terminal,
                            onBack = viewModel::showConnectionList,
                            onSend = viewModel::sendTerminalInput,
                            onResize = viewModel::onTerminalSizeChanged,
                        )

                        Screen.Sftp -> {
                            val profile = uiState.selectedProfile
                            LaunchedEffect(profile?.id) {
                                profile?.let { sftpViewModel.connect(it, viewModel.getPasswordFor(it)) }
                            }
                            SftpScreen(viewModel = sftpViewModel, onBack = viewModel::showConnectionList)
                        }

                        Screen.Backup -> BackupScreen(viewModel = backupViewModel, onBack = viewModel::showConnectionList)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionListScreen(
    profiles: List<HostProfile>,
    reachability: Map<Long, ReachabilityStatus>,
    onAdd: () -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
    onConnect: (HostProfile) -> Unit,
    onOpenSftp: (HostProfile) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onAdd) { Text("Add connection") }
        if (profiles.isEmpty()) {
            Text("No saved SSH connections yet. Add a host profile to get started.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val status = reachability[profile.id] ?: ReachabilityStatus.Unknown
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(status.color, CircleShape)
                                        .semantics {
                                            contentDescription = "Reachability: ${status.name.lowercase()}"
                                        },
                                )
                                Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            }
                            Text("${profile.username}@${profile.host}:${profile.port}")
                            Text("Auth: ${profile.authMethod.name}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onConnect(profile) }) { Text("Connect") }
                                OutlinedButton(onClick = { onEdit(profile) }) { Text("Edit") }
                                OutlinedButton(onClick = { onOpenSftp(profile) }) { Text("SFTP") }
                                TextButton(onClick = { onDelete(profile) }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditConnectionScreen(
    form: ConnectionFormState,
    onFormChange: (ConnectionFormState.() -> ConnectionFormState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    var authExpanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { value -> onFormChange { copy(name = value) } },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.host,
            onValueChange = { value -> onFormChange { copy(host = value) } },
            label = { Text("Host") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.port,
            onValueChange = { value -> onFormChange { copy(port = value) } },
            label = { Text("Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.username,
            onValueChange = { value -> onFormChange { copy(username = value) } },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
        )
        ExposedDropdownMenuBox(
            expanded = authExpanded,
            onExpandedChange = { authExpanded = it },
        ) {
            OutlinedTextField(
                value = form.authMethod.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Authentication") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = authExpanded,
                onDismissRequest = { authExpanded = false },
            ) {
                AuthMethod.entries.forEach { method ->
                    DropdownMenuItem(
                        text = { Text(method.name) },
                        onClick = {
                            onFormChange { copy(authMethod = method) }
                            authExpanded = false
                        },
                    )
                }
            }
        }
        if (form.authMethod == AuthMethod.Password) {
            OutlinedTextField(
                value = form.password,
                onValueChange = { value -> onFormChange { copy(password = value) } },
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = form.privateKeyAlias,
                onValueChange = { value -> onFormChange { copy(privateKeyAlias = value) } },
                label = { Text("Private key alias (stub)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Private key authentication is scaffolded for future implementation.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

private val ReachabilityStatus.color: Color
    get() = when (this) {
        ReachabilityStatus.Reachable -> Color(0xFF2E7D32)
        ReachabilityStatus.Unreachable -> Color(0xFFC62828)
        ReachabilityStatus.Unknown, ReachabilityStatus.Checking -> Color.Gray
    }

/** Returns the currently selected text of this [TextFieldValue], or an empty [AnnotatedString] if there is no selection. */
private fun TextFieldValue.selectedText(): AnnotatedString {
    val range = selection
    if (range.collapsed) return AnnotatedString("")
    return AnnotatedString(text.substring(range.min, range.max))
}

/**
 * Computes the bytes that must be streamed to the shell so it ends up displaying [newText] given
 * it currently has [oldText] on screen. The trivial cases (no change, a pure append, or a pure
 * trailing backspace) are streamed as the minimal keystrokes a real terminal would receive.
 * Anything else - autocorrect swapping a whole word, a paste replacing a mid-line selection, an
 * IME composition being committed as different text, etc. - keeps the common leading prefix
 * intact and only backspaces/retypes the part that actually changed, rather than backspacing and
 * retyping the entire line. This keeps the remote line buffer in sync with far fewer control
 * bytes, and avoids "\b"-storms that some programs (e.g. those without local echo) may not
 * handle exactly as a naive full-buffer replay assumes.
 */
internal fun computeInputDiff(oldText: String, newText: String): String {
    if (newText == oldText) return ""
    if (newText.startsWith(oldText)) return newText.substring(oldText.length)
    if (oldText.startsWith(newText)) return "\b".repeat(oldText.length - newText.length)
    val commonPrefixLength = oldText.commonPrefixWith(newText).length
    val backspaces = oldText.length - commonPrefixLength
    return "\b".repeat(backspaces) + newText.substring(commonPrefixLength)
}

/**
 * Minimum gap between two [submitLine]-worthy Enter signals required to treat them as separate
 * key presses, rather than the same physical press being reported twice (see the comment on
 * `submitLine` in [TerminalScreen]). This has to balance two failure modes: too large a window
 * risks swallowing a genuinely separate, fast Enter press (e.g. a user quickly tapping Enter
 * twice to leave a blank line); too small a window risks not catching a real duplicate. The
 * redundant signals for one physical press (raw key event, IME send action, trailing "\n") are
 * always delivered within the same input-processing pass, at most a few milliseconds apart, while
 * even a fast deliberate double press is reliably tens of milliseconds apart - so a short window
 * comfortably separates the two cases.
 */
private const val SUBMIT_DEDUPE_WINDOW_MILLIS = 60L

/** Returns whether a submit at [nowMillis] is a duplicate of the one that last ran at [lastSubmitAtMillis]. */
internal fun isDuplicateSubmit(lastSubmitAtMillis: Long, nowMillis: Long): Boolean =
    nowMillis - lastSubmitAtMillis < SUBMIT_DEDUPE_WINDOW_MILLIS

@Composable
private fun TerminalScreen(
    profile: HostProfile?,
    terminal: TerminalState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onResize: (rows: Int, cols: Int) -> Unit,
) {
    var input by remember { mutableStateOf(TextFieldValue()) }
    // Tracks the text that has actually been streamed to the shell so far, which is *not*
    // necessarily the same as `input.text`: while an IME composition is in progress, `input`
    // mirrors the (possibly not-final) composing preview locally, but nothing is sent to the
    // shell until the composition is committed - see the composing guard in [applyInput].
    var streamedText by remember { mutableStateOf("") }
    // Timestamp of the last time [submitLine] actually ran; see the dedupe guard there. Starts
    // far enough in the past (rather than 0L) so the very first submit is never mistaken for a
    // duplicate, even if it happens within [SUBMIT_DEDUPE_WINDOW_MILLIS] of device boot (when
    // elapsedRealtime() is itself close to 0).
    var lastSubmitAtMillis by remember { mutableStateOf(Long.MIN_VALUE / 2) }
    // The status overlay only shows briefly (on connect and on every tap) so it never permanently
    // covers terminal output; the tap counter restarts the hide timer.
    var overlayVisible by remember { mutableStateOf(true) }
    var overlayTaps by remember { mutableIntStateOf(0) }
    var keyboardVisible by remember { mutableStateOf(true) }
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val textMeasurer = rememberTextMeasurer()
    // Must stay in sync with the style TerminalGrid renders its rows with, otherwise the measured
    // character cell would not match the glyphs actually drawn.
    val terminalTextStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

    // The terminal paints its dark teal background behind the transparent status bar, so the system
    // icons have to be switched to their light variant while it is shown (and restored afterwards).
    DisposableEffect(view) {
        val controller = (view.context as? Activity)
            ?.let { activity -> WindowCompat.getInsetsController(activity.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (controller != null && previous != null) {
                controller.isAppearanceLightStatusBars = previous
            }
        }
    }

    // Focus the hidden input capture as soon as the terminal is shown so the on-screen keyboard
    // comes up without requiring the user to first tap something.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    LaunchedEffect(overlayTaps) {
        overlayVisible = true
        delay(OVERLAY_VISIBLE_MILLIS)
        overlayVisible = false
    }

    fun showKeyboard() {
        focusRequester.requestFocus()
        // requestFocus() alone is a no-op when the hidden field never lost focus (e.g. the user
        // dismissed the keyboard with the IME's own back button), so show the IME explicitly too.
        keyboardController?.show()
        keyboardVisible = true
    }

    fun pushUndo(previous: TextFieldValue) {
        undoStack.add(previous)
        redoStack.clear()
    }

    /**
     * Submits the current line: the command text itself has *already* been streamed to the shell
     * character-by-character by [applyInput] as the user typed, so all that is left to do here is
     * send the carriage return and reset the local buffer.
     *
     * Do NOT re-send [input].text from here - doing so duplicates the command on the remote side
     * (typing "ls" then pressing Enter would execute "lsls").
     *
     * Enter/Send is reported through up to three independent paths (see the comment above the
     * hidden [BasicTextField] below), and on some IMEs/hardware keyboards more than one of them
     * fires for a single Enter press. Guard against that by ignoring any call that follows a
     * successful submit within [SUBMIT_DEDUPE_WINDOW_MILLIS]: duplicate calls for the *same* key
     * press always arrive within the same synchronous event dispatch (a few milliseconds at
     * most), while genuinely separate Enter presses are always further apart than that.
     */
    fun submitLine() {
        // elapsedRealtime() (monotonic, unaffected by wall-clock/NTP adjustments) is used instead
        // of System.currentTimeMillis() so a clock change can never mask or falsely trigger the
        // dedupe guard below.
        val now = SystemClock.elapsedRealtime()
        if (isDuplicateSubmit(lastSubmitAtMillis, now)) return
        lastSubmitAtMillis = now
        onSend("\r")
        pushUndo(input)
        input = TextFieldValue()
        streamedText = ""
    }

    /**
     * Applies [newValue] as the new hidden-input-buffer state and streams whatever changed
     * straight to the shell channel, so ordinary typing/backspace/paste never needs an explicit
     * "Send" action. Appends/trailing-deletes are streamed char-by-char; anything else (e.g. a
     * paste replacing a mid-line selection, or autocorrect swapping a whole word) falls back to
     * backspacing only the part of the old text that actually differs and retyping the new
     * suffix (see [computeInputDiff]), instead of nuking and retyping the entire buffer.
     *
     * While the IME is still composing text (e.g. Gboard underlining a candidate word before the
     * user accepts or the word is auto-finalized), [TextFieldValue.composition] is non-null and
     * `newValue` is only a preview - it can change or be replaced several times before the word
     * is committed. Diffing against previews would stream partial/backspaced bytes to the shell
     * that get invalidated moments later, which is a common source of stray characters appearing
     * mid-word. So while composing, only the local buffer is updated for on-screen display; the
     * diff against [streamedText] is computed and sent once the composition is committed
     * (`newValue.composition == null`).
     *
     * A trailing "\n" here means some IME inserted a literal newline instead of going through
     * [Modifier.onKeyEvent]/[KeyboardActions.onSend]. The text before the newline is streamed
     * normally, then the line is submitted, so the buffer is always cleared once Enter has been
     * handled in any form.
     */
    fun applyInput(newValue: TextFieldValue) {
        if (newValue.composition != null) {
            input = newValue
            return
        }
        val submitting = newValue.text.endsWith("\n")
        val newText = if (submitting) newValue.text.removeSuffix("\n") else newValue.text
        val diff = computeInputDiff(streamedText, newText)
        if (diff.isNotEmpty()) onSend(diff)
        streamedText = newText
        if (submitting) {
            input = newValue.copy(text = newText)
            submitLine()
        } else {
            input = newValue
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                // Keep the edge-to-edge status-bar area visually continuous, but do not let the
                // terminal gesture target extend into it. Previously the clickable was outside
                // this inset, so a terminal scroll starting at the top edge could also pull down
                // the system notification shade.
                .background(TerminalColors.Background),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    // This must precede clickable: the padding reserves a transparent safe area
                    // before the terminal receives pointer input.
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(4.dp)
                    .clickable {
                        showKeyboard()
                        overlayTaps++
                    },
            ) {
                // The emulator grid is sized to the area that is actually visible: the measured
                // monospace glyph box divides the available space into whole character cells. The
                // result is pushed down to the session so the local buffer *and* the remote PTY
                // agree with what the user sees - recomputed automatically whenever this area
                // changes size (rotation, split-screen, keyboard opening or closing).
                val glyphs = remember(textMeasurer, terminalTextStyle) {
                    textMeasurer.measure(
                        AnnotatedString("M".repeat(GLYPH_SAMPLE_LENGTH)),
                        style = terminalTextStyle,
                        softWrap = false,
                    )
                }
                val charWidth = (glyphs.size.width / GLYPH_SAMPLE_LENGTH).coerceAtLeast(1)
                val lineHeight = glyphs.size.height.coerceAtLeast(1)
                val cols = (constraints.maxWidth / charWidth).coerceAtLeast(MIN_TERMINAL_COLS)
                val rows = (constraints.maxHeight / lineHeight).coerceAtLeast(MIN_TERMINAL_ROWS)
                LaunchedEffect(rows, cols) {
                    onResize(rows, cols)
                    // Resizing usually means the keyboard just appeared or disappeared; bring the
                    // overlay back so the disconnect control is always within reach.
                    overlayTaps++
                }
                TerminalGrid(snapshot = terminal.snapshot, modifier = Modifier.fillMaxSize())
            }
            // Unobtrusive translucent status overlay: shows who/where we are connected to and
            // offers a compact disconnect control instead of a full-width button. It fades away a
            // few seconds after the last tap so the terminal output is never permanently covered,
            // and comes back whenever the terminal area is tapped.
            TerminalStatusOverlay(
                visible = overlayVisible,
                title = profile?.let { "${it.username}@${it.host}" } ?: "Terminal",
                onDisconnect = onBack,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(1f),
            )
        }
        ExtraKeysBar(
            onSendKey = { sequence -> onSend(sequence) },
            onToggleKeyboard = {
                if (keyboardVisible) {
                    keyboardController?.hide()
                    keyboardVisible = false
                } else {
                    showKeyboard()
                }
            },
            onEditAction = { action ->
                when (action) {
                    EditAction.SelectAll -> {
                        input = input.copy(selection = TextRange(0, input.text.length))
                    }

                    EditAction.Copy -> {
                        val selected = input.selectedText()
                        val toCopy = if (selected.text.isNotEmpty()) selected else AnnotatedString(input.text)
                        clipboardManager.setText(toCopy)
                    }

                    EditAction.Cut -> {
                        val selected = input.selectedText()
                        if (selected.text.isNotEmpty()) {
                            clipboardManager.setText(selected)
                            pushUndo(input)
                            val range = input.selection
                            val newText = input.text.removeRange(range.min, range.max)
                            applyInput(TextFieldValue(newText, selection = TextRange(range.min)))
                        }
                    }

                    EditAction.Paste -> {
                        val clip = clipboardManager.getText()?.text.orEmpty()
                        if (clip.isNotEmpty()) {
                            pushUndo(input)
                            val range = input.selection
                            val newText = input.text.replaceRange(range.min, range.max, clip)
                            val cursor = range.min + clip.length
                            applyInput(TextFieldValue(newText, selection = TextRange(cursor)))
                        }
                    }

                    EditAction.Undo -> {
                        undoStack.removeLastOrNull()?.let { previous ->
                            redoStack.add(input)
                            applyInput(previous)
                        }
                    }

                    EditAction.Redo -> {
                        redoStack.removeLastOrNull()?.let { next ->
                            undoStack.add(input)
                            applyInput(next)
                        }
                    }
                }
            },
        )
        // Hidden/invisible input capture: this is the only text field in the terminal screen. It
        // has no visible presence (1dp, fully transparent) - its sole purpose is to receive the
        // on-screen keyboard's input events so regular typing can be streamed live (see
        // [applyInput]) instead of requiring a separate visible "command line" + Send button.
        //
        // autoCorrectEnabled = false and keyboardType = Ascii disable IME autocorrect/predictive
        // word suggestions: those replace whole words after the fact, which is the main trigger
        // for the non-trivial diff branch in [computeInputDiff] and for confusing composing-state
        // updates (see [applyInput]).
        //
        // Enter/Send is handled in three redundant ways because different IMEs/hardware keyboards
        // disagree on how they signal "the user pressed Enter":
        //  1. Modifier.onKeyEvent intercepts the raw Enter/NumPadEnter key down event before the
        //     text field can insert a newline into the buffer.
        //  2. KeyboardActions.onSend fires when the IME action button (imeAction = Send) is tapped.
        //  3. applyInput() recognizes a literal trailing "\n" that slipped through both.
        // On some IMEs/hardware keyboards more than one of these fires for the *same* Enter
        // press, so all three converge on submitLine(), which is itself guarded to ignore a
        // second call that immediately follows a successful submit (see the dedupe guard in
        // submitLine's doc comment) - only the first one actually sends "\r" and clears the
        // buffer. The command characters themselves were already streamed while typing.
        BasicTextField(
            value = input,
            onValueChange = { newValue ->
                // Skip while composing (see the guard in applyInput): every intermediate
                // candidate update during an in-progress IME composition would otherwise push
                // its own undo entry, spamming the undo stack with states the user never
                // deliberately typed. Compare against `streamedText` (the text last actually
                // sent to the shell), not `input.text`: while composing, `input.text` has
                // already been mutated to mirror the not-yet-committed preview, so comparing
                // against it would never detect a change once the composition finally commits.
                if (newValue.composition == null && newValue.text != streamedText) pushUndo(input)
                applyInput(newValue)
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send,
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onSend = { submitLine() },
            ),
            modifier = Modifier
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.NumPadEnter)
                    ) {
                        submitLine()
                        true
                    } else {
                        false
                    }
                }
                .size(1.dp)
                .alpha(0f)
                .semantics { contentDescription = "Terminal keyboard input" },
        )
    }
}

/** Number of sample glyphs measured at once, so the average character width is not rounding-skewed. */
private const val GLYPH_SAMPLE_LENGTH = 10

/** Lower bounds for the auto-measured grid, so a degenerate measurement can never produce 0 rows/cols. */
private const val MIN_TERMINAL_ROWS = 4
private const val MIN_TERMINAL_COLS = 20

/** How long the `user@host` overlay stays visible after the last tap before fading out. */
private const val OVERLAY_VISIBLE_MILLIS = 3_000L

/**
 * Translucent `user@host` + disconnect overlay shown on top of the terminal output.
 *
 * It lives in its own composable (rather than being inlined at the call site) so that
 * [AnimatedVisibility] resolves to the plain, non-scoped overload: inside the terminal layout both
 * `ColumnScope` and `BoxScope` are in scope as implicit receivers, which makes the call ambiguous.
 * The caller supplies the placement via [modifier] (e.g. `Modifier.align(Alignment.TopEnd)`).
 */
@Composable
private fun TerminalStatusOverlay(
    visible: Boolean,
    title: String,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        // Apply the safe-area offset to the overlay's own bounds. Applying it only to the Row
        // left AnimatedVisibility anchored at y=0 with a transparent, easy-to-miss leading area.
        modifier = modifier.windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            IconButton(
                onClick = onDisconnect,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = "Disconnect",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
