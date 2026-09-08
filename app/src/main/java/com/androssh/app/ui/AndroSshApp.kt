package com.androssh.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.androssh.app.data.AuthMethod
import com.androssh.app.data.HostProfile
import com.androssh.app.ui.backup.BackupScreen
import com.androssh.app.ui.sftp.SftpScreen
import com.androssh.app.ui.terminal.EditAction
import com.androssh.app.ui.terminal.ExtraKeysBar
import com.androssh.app.ui.terminal.TerminalGrid
import com.androssh.app.viewmodel.AndroSshViewModel
import com.androssh.app.viewmodel.BackupViewModel
import com.androssh.app.viewmodel.ConnectionFormState
import com.androssh.app.viewmodel.ReachabilityStatus
import com.androssh.app.viewmodel.Screen
import com.androssh.app.viewmodel.SftpViewModel
import com.androssh.app.viewmodel.TerminalState

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
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .fillMaxSize(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = "AndroSSH", style = MaterialTheme.typography.headlineMedium)
                        if (uiState.screen == Screen.ConnectionList) {
                            TextButton(onClick = viewModel::openBackup) { Text("Backup") }
                        }
                    }
                    uiState.message?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
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
) {
    var input by remember { mutableStateOf(TextFieldValue()) }
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }
    val clipboardManager = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }

    // Focus the hidden input capture as soon as the terminal is shown so the on-screen keyboard
    // comes up without requiring the user to first tap something.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(profile?.let { "${it.username}@${it.host}" } ?: "Terminal")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .clickable { focusRequester.requestFocus() }
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(4.dp),
        ) {
            TerminalGrid(snapshot = terminal.snapshot)
        }
        ExtraKeysBar(
            onSendKey = { sequence -> onSend(sequence) },
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
        OutlinedButton(onClick = onBack) { Text("Disconnect") }
    }
}
