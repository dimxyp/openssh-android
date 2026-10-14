package com.androssh.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Compact two-row bar of extra terminal keys (Esc/Tab/Ctrl/Alt/Home/End/PgUp/PgDn/arrows/
 * punctuation, a toggle-able function-keys row, paste and a soft-keyboard show/hide key). Plain
 * keys are translated into the raw bytes a shell expects (see [TerminalKey]) and forwarded via
 * [onSendKey]. CTRL and ALT are sticky toggles that apply to the next key typed in the terminal
 * (see [StickyModifiers]) and are highlighted while active.
 *
 * Keys are flat text labels spread evenly across the width (no chip borders or elevation) so the
 * bar costs the terminal as little height as possible.
 *
 * @param onToggleKeyboard shows/hides the soft keyboard; hoisted so this bar stays presentational.
 */
@Composable
fun ExtraKeysBar(
    onSendKey: (String) -> Unit,
    ctrlActive: Boolean,
    altActive: Boolean,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onPaste: () -> Unit,
    onToggleKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFunctionRow by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TerminalColors.KeyBarBackground,
        contentColor = TerminalColors.KeyBarForeground,
    ) {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
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
                KeyLabel(label = "CTRL", active = ctrlActive, onClick = onToggleCtrl)
                KeyLabel(label = "ALT", active = altActive, onClick = onToggleAlt)
                ExtraKeysLayout.bottomRowTrailing.forEach { key ->
                    KeyLabel(label = key.barLabel, onClick = { onSendKey(key.sequence) })
                }
                KeyLabel(label = "PASTE", onClick = onPaste)
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

/** A flat, borderless key: just a text label with a generous tap target. */
@Composable
private fun KeyLabel(label: String, onClick: () -> Unit, active: Boolean = false) {
    Box(
        modifier = Modifier
            .background(if (active) TerminalColors.KeyBarActive else Color.Transparent, RoundedCornerShape(4.dp))
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

/** Glyph of the key that shows/hides the soft keyboard. */
private const val KEYBOARD_LABEL = "\u2328"
