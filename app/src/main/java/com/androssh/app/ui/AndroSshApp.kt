package com.androssh.app.ui

import android.app.Activity
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

@Composable
private fun TerminalScreen(
    profile: HostProfile?,
    terminal: TerminalState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onResize: (rows: Int, cols: Int) -> Unit,
) {
    var input by remember { mutableStateOf(TextFieldValue()) }
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
     * Applies [newValue] as the new hidden-input-buffer state and streams whatever changed
     * straight to the shell channel, so ordinary typing/backspace/paste never needs an explicit
     * "Send" action. Appends/trailing-deletes are streamed char-by-char; anything else (e.g. a
     * paste replacing a mid-line selection) falls back to backspacing the old text and retyping
     * the new text, which stays correct even though it isn't the most minimal byte sequence.
     */
    fun applyInput(newValue: TextFieldValue) {
        val oldText = input.text
        val newText = newValue.text
        when {
            newText == oldText -> Unit
            newText.startsWith(oldText) -> onSend(newText.substring(oldText.length))
            oldText.startsWith(newText) -> onSend("\b".repeat(oldText.length - newText.length))
            else -> {
                onSend("\b".repeat(oldText.length))
                onSend(newText)
            }
        }
        input = newValue
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
        BasicTextField(
            value = input,
            onValueChange = { newValue ->
                if (newValue.text != input.text) pushUndo(input)
                applyInput(newValue)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    onSend("\r")
                    pushUndo(input)
                    applyInput(TextFieldValue())
                },
            ),
            modifier = Modifier
                .focusRequester(focusRequester)
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
