package com.androssh.app.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.androssh.app.data.AuthMethod
import com.androssh.app.data.HostProfile
import com.androssh.app.ui.backup.BackupScreen
import com.androssh.app.ui.sftp.SftpScreen
import com.androssh.app.ui.terminal.ExtraKeysBar
import com.androssh.app.ui.terminal.TerminalColors
import com.androssh.app.ui.terminal.StickyModifiers
import com.androssh.app.viewmodel.AndroSshViewModel
import com.androssh.app.viewmodel.BackupViewModel
import com.androssh.app.viewmodel.ConnectionFormState
import com.androssh.app.viewmodel.ReachabilityStatus
import com.androssh.app.viewmodel.Screen
import com.androssh.app.viewmodel.SftpViewModel
import kotlinx.coroutines.delay
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulator

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
                            emulator = viewModel.terminalEmulator,
                            onBack = viewModel::showConnectionList,
                            onSend = viewModel::sendTerminalInput,
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

@Composable
private fun TerminalScreen(
    profile: HostProfile?,
    emulator: TerminalEmulator,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
) {
    // The status overlay only shows briefly (on connect and on every tap) so it never permanently
    // covers terminal output; the tap counter restarts the hide timer.
    var overlayVisible by remember { mutableStateOf(true) }
    var overlayTaps by remember { mutableIntStateOf(0) }
    var keyboardVisible by remember { mutableStateOf(true) }
    val modifiers = remember { StickyModifiers() }
    val clipboardManager = LocalClipboardManager.current
    val view = LocalView.current

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

    LaunchedEffect(overlayTaps) {
        overlayVisible = true
        delay(OVERLAY_VISIBLE_MILLIS)
        overlayVisible = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(TerminalColors.Background),
        ) {
            // The terminal view owns keyboard/IME input, scrollback, selection and sizing: it
            // measures its own character grid and reports it to the emulator, which resizes the
            // remote PTY. The inset keeps the gesture target out of the status-bar area so a
            // scroll starting at the top edge cannot also pull down the notification shade.
            Terminal(
                terminalEmulator = emulator,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(4.dp),
                backgroundColor = TerminalColors.Background,
                foregroundColor = TerminalColors.Foreground,
                keyboardEnabled = true,
                showSoftKeyboard = keyboardVisible,
                onTerminalTap = {
                    overlayTaps++
                    keyboardVisible = true
                },
                onImeVisibilityChanged = { keyboardVisible = it },
                modifierManager = modifiers,
            )
            // Unobtrusive translucent status overlay: shows who/where we are connected to and
            // offers a compact disconnect control. It fades away a few seconds after the last tap.
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
            onSendKey = onSend,
            ctrlActive = modifiers.ctrl,
            altActive = modifiers.alt,
            onToggleCtrl = { modifiers.ctrl = !modifiers.ctrl },
            onToggleAlt = { modifiers.alt = !modifiers.alt },
            onPaste = {
                clipboardManager.getText()?.text?.takeIf { it.isNotEmpty() }?.let { text ->
                    onSend(text.replace("\r\n", "\r").replace('\n', '\r'))
                }
            },
            onToggleKeyboard = { keyboardVisible = !keyboardVisible },
        )
    }
}

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
