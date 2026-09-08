package com.androssh.app.ui.terminal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.androssh.app.terminal.TerminalCell
import com.androssh.app.terminal.TerminalSnapshot

/**
 * Renders a [TerminalSnapshot] as a monospace character grid: the retained scrollback history
 * first, then the live screen, one row per line. Each line is built as a single
 * [androidx.compose.ui.text.AnnotatedString] with per-character color/bold spans derived from the
 * terminal emulator's SGR state. The cursor cell is drawn as a solid block using
 * [TerminalColors.Cursor]. Colors that the emulator does not set explicitly fall back to
 * [TerminalColors].
 *
 * Lines are emitted from a [LazyColumn] so that even a long scrollback only composes the handful of
 * rows that are actually visible. New output auto-scrolls the view to the bottom - but only while
 * the user is already at the bottom, so scrolling up to read history is never yanked back down by
 * incoming output. This composable only reads the snapshot it is given; all parsing/state lives in
 * [com.androssh.app.terminal.TerminalEmulator].
 *
 * Original implementation written from scratch for AndroSSH; not derived from JuiceSSH, Termux,
 * or any other terminal emulator's source code.
 */
@Composable
fun TerminalGrid(
    snapshot: TerminalSnapshot,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val lines = remember(snapshot) { snapshot.scrollback + snapshot.rows }
    val liveScreenStart = snapshot.scrollback.size
    // Whether new output should keep the view pinned to the bottom. It is only re-evaluated when a
    // scroll gesture settles, so appending lines (which by itself makes the last item scroll out of
    // view) never flips it off on its own.
    var followOutput by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling -> if (!scrolling) followOutput = listState.isAtBottom() }
    }

    LaunchedEffect(snapshot) {
        if (followOutput && lines.isNotEmpty()) {
            listState.scrollToItem(lines.lastIndex)
        }
    }

    LazyColumn(state = listState, modifier = modifier) {
        items(count = lines.size) { index ->
            val cursorCol = if (index == liveScreenStart + snapshot.cursorRow) snapshot.cursorCol else null
            Text(
                text = renderLine(lines[index], cursorCol),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** True when the last line is (at least partially) visible, i.e. the view is showing live output. */
private fun LazyListState.isAtBottom(): Boolean {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return true
    return (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 1
}

/**
 * Builds the styled text for one terminal line, painting the cell at [cursorCol] (when non-null) as
 * a solid light block with the background color showing through the glyph - a classic block cursor.
 */
private fun renderLine(cells: List<TerminalCell>, cursorCol: Int?) = buildAnnotatedString {
    cells.forEachIndexed { colIndex, cell ->
        val isCursor = colIndex == cursorCol
        val foreground = ansiColor(cell.style.foreground, bright = cell.style.bold) ?: TerminalColors.Foreground
        val background = cell.style.background?.let { ansiColor(it, bright = false) }
            ?: TerminalColors.Background
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
