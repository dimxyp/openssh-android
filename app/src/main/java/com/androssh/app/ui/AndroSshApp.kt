package com.androssh.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.androssh.app.data.AuthMethod
import com.androssh.app.data.HostProfile
import com.androssh.app.viewmodel.AndroSshViewModel
import com.androssh.app.viewmodel.ConnectionFormState
import com.androssh.app.viewmodel.Screen
import com.androssh.app.viewmodel.TerminalState

@Composable
fun AndroSshApp(viewModel: AndroSshViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val profiles by viewModel.profiles.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .fillMaxSize(),
                ) {
                    Text(text = "AndroSSH", style = MaterialTheme.typography.headlineMedium)
                    uiState.message?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    when (uiState.screen) {
                        Screen.ConnectionList -> ConnectionListScreen(
                            profiles = profiles,
                            onAdd = viewModel::openNewProfileForm,
                            onEdit = viewModel::openEditProfileForm,
                            onDelete = viewModel::deleteProfile,
                            onConnect = viewModel::connect,
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
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionListScreen(
    profiles: List<HostProfile>,
    onAdd: () -> Unit,
    onEdit: (HostProfile) -> Unit,
    onDelete: (HostProfile) -> Unit,
    onConnect: (HostProfile) -> Unit,
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
                            Text(profile.name, style = MaterialTheme.typography.titleMedium)
                            Text("${profile.username}@${profile.host}:${profile.port}")
                            Text("Auth: ${profile.authMethod.name}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onConnect(profile) }) { Text("Connect") }
                                OutlinedButton(onClick = { onEdit(profile) }) { Text("Edit") }
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
                visualTransformation = PasswordVisualTransformation(),
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

@Composable
private fun TerminalScreen(
    profile: HostProfile?,
    terminal: TerminalState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(profile?.let { "${it.username}@${it.host}" } ?: "Terminal")
        TextField(
            value = terminal.lines.joinToString(separator = ""),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("Session output") },
        )
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Input") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onSend(input + "\n")
                input = ""
            }) { Text("Send") }
            OutlinedButton(onClick = onBack) { Text("Disconnect") }
        }
    }
}
