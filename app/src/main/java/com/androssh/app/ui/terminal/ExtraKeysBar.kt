package com.androssh.app.ui.terminal

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Actions available while [BarMode.EDIT] (text edit / selection) mode is
 * active. These act on whatever text field currently owns focus in the
 * terminal input line.
 */
enum class EditAction {
    SelectAll,
    Copy,
    Cut,
    Paste,
    Undo,
    Redo,
}

/** Which view the [ExtraKeysBar] is currently displaying. */
enum class BarMode {
    ExtraKeys,
    Edit,
}

/**
 * Original, from-scratch Compose component implementing a horizontal bar of
 * extra terminal keys (Esc/Tab/Ctrl/Alt/Home/End/PgUp/PgDn/arrows, plus a
 * toggle-able function-keys row) and a swipe gesture that flips the bar into
 * a text-edit/selection mode. Every key press is translated into the raw
 * bytes a shell expects (see [TerminalKey] / [ctrlSequenceFor]) and forwarded
 * to the caller via [onSendKey] so it can be written straight to the SSH
 * shell channel.
 *
 * The key set is intentionally data-driven ([ExtraKeysLayout]) so more keys
 * can be added later without touching this composable.
 */
@Composable
fun ExtraKeysBar(
    onSendKey: (String) -> Unit,
    onEditAction: (EditAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by remember { mutableStateOf(BarMode.ExtraKeys) }
    var showFunctionRow by remember { mutableStateOf(false) }
    var showCtrlPicker by remember { mutableStateOf(false) }
    var showAltPicker by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    fun toggleMode() {
        mode = if (mode == BarMode.ExtraKeys) BarMode.Edit else BarMode.ExtraKeys
        showCtrlPicker = false
        showAltPicker = false
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(dragAccumulator) > SWIPE_TOGGLE_THRESHOLD_PX) {
                            toggleMode()
                        }
                        dragAccumulator = 0f
                    },
                ) { change, dragAmount ->
                    change.consume()
                    dragAccumulator += dragAmount
                }
            },
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            when (mode) {
                BarMode.ExtraKeys -> {
                    if (showCtrlPicker) {
                        ModifierLetterPicker(
                            title = "Ctrl +",
                            onLetterSelected = { letter ->
                                onSendKey(ctrlSequenceFor(letter))
                                showCtrlPicker = false
                            },
                            onDismiss = { showCtrlPicker = false },
                        )
                    } else if (showAltPicker) {
                        ModifierLetterPicker(
                            title = "Alt +",
                            onLetterSelected = { letter ->
                                onSendKey("\u001B$letter")
                                showAltPicker = false
                            },
                            onDismiss = { showAltPicker = false },
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            KeyChip(label = "Ctrl", onClick = { showCtrlPicker = true })
                            KeyChip(label = "Alt", onClick = { showAltPicker = true })
                            ExtraKeysLayout.primaryRow.forEach { key ->
                                KeyChip(label = key.label, onClick = { onSendKey(key.sequence) })
                            }
                            KeyChip(
                                label = if (showFunctionRow) "F\u25B2" else "F\u25BC",
                                onClick = { showFunctionRow = !showFunctionRow },
                            )
                            // The ExtraKeys/Edit toggle rides along in this same scrollable row so
                            // the bar never costs the terminal a second row of height.
                            KeyChip(label = MODE_TOGGLE_LABEL, onClick = { toggleMode() })
                        }
                        if (showFunctionRow) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                ExtraKeysLayout.functionRow.forEach { key ->
                                    KeyChip(label = key.label, onClick = { onSendKey(key.sequence) })
                                }
                            }
                        }
                    }
                }

                BarMode.Edit -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        EditAction.entries.forEach { action ->
                            KeyChip(label = action.name, onClick = { onEditAction(action) })
                        }
                        KeyChip(label = MODE_TOGGLE_LABEL, onClick = { toggleMode() })
                    }
                }
            }
        }
    }
}

@Composable
private fun ModifierLetterPicker(
    title: String,
    onLetterSelected: (Char) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ('A'..'Z').forEach { letter ->
                KeyChip(label = letter.toString(), onClick = { onLetterSelected(letter) })
            }
        }
    }
}

@Composable
private fun KeyChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(2.dp),
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 4.dp,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private const val SWIPE_TOGGLE_THRESHOLD_PX = 120f

/** Glyph of the chip that flips the bar between [BarMode.ExtraKeys] and [BarMode.Edit]. */
private const val MODE_TOGGLE_LABEL = "\u21C4"
