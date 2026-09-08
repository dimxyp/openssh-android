package com.androssh.app.ui.terminal

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.androssh.app.terminal.TerminalSnapshot

/**
 * Renders a [TerminalSnapshot] as a monospace character grid: one row per buffer line, each built
 * as a single [androidx.compose.ui.text.AnnotatedString] with per-character color/bold spans
 * derived from the terminal emulator's SGR state. The cursor cell is drawn as a solid block using
 * [TerminalColors.Cursor]. Colors that the emulator does not set explicitly fall back to
 * [TerminalColors]. This composable only reads the snapshot it is given - all
 * parsing/state lives in [com.androssh.app.terminal.TerminalEmulator].
 *
 * Original implementation written from scratch for AndroSSH; not derived from JuiceSSH, Termux,
 * or any other terminal emulator's source code.
 */
@Composable
fun TerminalGrid(snapshot: TerminalSnapshot) {
    Column {
        snapshot.rows.forEachIndexed { rowIndex, row ->
            val annotated = buildAnnotatedString {
                row.forEachIndexed { colIndex, cell ->
                    val isCursor = rowIndex == snapshot.cursorRow && colIndex == snapshot.cursorCol
                    val foreground = ansiColor(cell.style.foreground, bright = cell.style.bold) ?: TerminalColors.Foreground
                    val background = cell.style.background?.let { ansiColor(it, bright = false) }
                        ?: TerminalColors.Background
                    // The cursor cell is painted as a solid light block with the background color
                    // showing through the glyph, matching a classic block cursor.
                    withStyle(
                        SpanStyle(
                            color = if (isCursor) TerminalColors.Background else foreground,
                            background = if (isCursor) TerminalColors.Cursor else background,
                            fontWeight = if (cell.style.bold) FontWeight.Bold else FontWeight.Normal,
                        ),
                    ) {
                        append(cell.char)
                    }
                }
            }
            Text(
                text = annotated,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                softWrap = false,
            )
        }
    }
}

private val AnsiNormalColors = listOf(
    Color(0xFF000000), Color(0xFFCC0000), Color(0xFF4E9A06), Color(0xFFC4A000),
    Color(0xFF3465A4), Color(0xFF75507B), Color(0xFF06989A), Color(0xFFD3D7CF),
)

private val AnsiBrightColors = listOf(
    Color(0xFF555753), Color(0xFFEF2929), Color(0xFF8AE234), Color(0xFFFCE94F),
    Color(0xFF729FCF), Color(0xFFAD7FA8), Color(0xFF34E2E2), Color(0xFFEEEEEC),
)

/** Maps a standard ANSI color index (0-7) to a concrete [Color], or `null` if [index] is `null`. */
private fun ansiColor(index: Int?, bright: Boolean): Color? =
    index?.let { (if (bright) AnsiBrightColors else AnsiNormalColors).getOrNull(it) }
