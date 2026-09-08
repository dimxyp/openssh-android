package com.androssh.app.ui.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Actions available while [BarMode.Edit] (text edit / selection) mode is
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
 * Original, from-scratch Compose component implementing a compact two-row bar of extra terminal
 * keys (Esc/Tab/Ctrl/Alt/Home/End/PgUp/PgDn/arrows/punctuation, a toggle-able function-keys row and
 * a soft-keyboard show/hide key), plus a swipe gesture that flips the bar into a text-edit /
 * selection mode. Every key press is translated into the raw bytes a shell expects (see
 * [TerminalKey] / [ctrlSequenceFor]) and forwarded to the caller via [onSendKey] so it can be
 * written straight to the SSH shell channel.
 *
 * Keys are flat text labels spread evenly across the width (no chip borders or elevation) so the
 * bar costs the terminal as little height as possible.
 *
 * The key set is intentionally data-driven ([ExtraKeysLayout]) so more keys can be added later
 * without touching this composable.
 *
 * @param onToggleKeyboard shows/hides the soft keyboard; hoisted so this bar stays presentational.
 */
@Composable
fun ExtraKeysBar(
    onSendKey: (String) -> Unit,
    onEditAction: (EditAction) -> Unit,
    onToggleKeyboard: () -> Unit,
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
        color = TerminalColors.KeyBarBackground,
        contentColor = TerminalColors.KeyBarForeground,
    ) {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
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
                        KeyRow {
                            ExtraKeysLayout.topRow.forEach { key ->
                                KeyLabel(label = key.barLabel, onClick = { onSendKey(key.sequence) })
                            }
                            KeyLabel(label = "FN", onClick = { showFunctionRow = !showFunctionRow })
                        }
                        KeyRow {
                            ExtraKeysLayout.bottomRowLeading.forEach { key ->
                                KeyLabel(label = key.barLabel, onClick = { onSendKey(key.sequence) })
                            }
                            KeyLabel(label = "CTRL", onClick = { showCtrlPicker = true })
                            KeyLabel(label = "ALT", onClick = { showAltPicker = true })
                            ExtraKeysLayout.bottomRowTrailing.forEach { key ->
                                KeyLabel(label = key.barLabel, onClick = { onSendKey(key.sequence) })
                            }
                            // The ExtraKeys/Edit toggle rides along in this same row so the bar
                            // never costs the terminal an extra row of height just for the mode.
                            KeyLabel(label = MODE_TOGGLE_LABEL, onClick = { toggleMode() })
                            KeyLabel(label = KEYBOARD_LABEL, onClick = onToggleKeyboard)
                        }
                        if (showFunctionRow) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                ExtraKeysLayout.functionRow.forEach { key ->
                                    KeyLabel(label = key.barLabel, onClick = { onSendKey(key.sequence) })
                                }
                            }
                        }
                    }
                }

                BarMode.Edit -> {
                    KeyRow {
                        EditAction.entries.take(EDIT_ACTIONS_PER_ROW).forEach { action ->
                            KeyLabel(label = action.barLabel, onClick = { onEditAction(action) })
                        }
                    }
                    KeyRow {
                        EditAction.entries.drop(EDIT_ACTIONS_PER_ROW).forEach { action ->
                            KeyLabel(label = action.barLabel, onClick = { onEditAction(action) })
                        }
                        KeyLabel(label = MODE_TOGGLE_LABEL, onClick = { toggleMode() })
                        KeyLabel(label = KEYBOARD_LABEL, onClick = onToggleKeyboard)
                    }
                }
            }
        }
    }
}

/** One row of flat keys, spread evenly across the full width. */
@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ModifierLetterPicker(
    title: String,
    onLetterSelected: (Char) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                KeyLabel(label = letter.toString(), onClick = { onLetterSelected(letter) })
            }
        }
    }
}

/** A flat, borderless key: just a text label with a generous tap target. */
@Composable
private fun KeyLabel(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Uppercase rendering of a key's label, matching the flat bar's typography. */
private val TerminalKey.barLabel: String
    get() = label.uppercase()

/** Short, uppercase label used for edit actions on the compact bar. */
private val EditAction.barLabel: String
    get() = when (this) {
        EditAction.SelectAll -> "ALL"
        EditAction.Copy -> "COPY"
        EditAction.Cut -> "CUT"
        EditAction.Paste -> "PASTE"
        EditAction.Undo -> "UNDO"
        EditAction.Redo -> "REDO"
    }

private const val SWIPE_TOGGLE_THRESHOLD_PX = 120f

/** How many edit actions go on the first of the two edit-mode rows. */
private const val EDIT_ACTIONS_PER_ROW = 3

/** Glyph of the key that flips the bar between [BarMode.ExtraKeys] and [BarMode.Edit]. */
private const val MODE_TOGGLE_LABEL = "\u21C4"

/** Glyph of the key that shows/hides the soft keyboard. */
private const val KEYBOARD_LABEL = "\u2328"
