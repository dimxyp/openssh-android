package com.androssh.app.terminal

/**
 * A focused, original VT100/ANSI-subset terminal emulator.
 *
 * It parses the raw byte/character stream coming from a shell's stdout and maintains an
 * in-memory screen buffer: a rows x cols grid of styled characters plus the current cursor
 * position. This is intentionally *not* a full xterm/VT100 implementation - it covers the
 * escape sequences that interactive shell programs (bash line editing, `top`, simple `vim`/`nano`
 * screens, etc.) rely on most:
 *
 *  - printable characters, `\r` (carriage return), `\n` (line feed), backspace (`\b`), tab
 *  - cursor positioning: `ESC[<row>;<col>H` (and its `f` alias)
 *  - relative cursor movement: `ESC[<n>A/B/C/D` (up/down/forward/back)
 *  - erase in line: `ESC[K`, `ESC[0K`, `ESC[1K`, `ESC[2K`
 *  - erase in display: `ESC[J`, `ESC[0J`, `ESC[1J`, `ESC[2J`
 *  - basic SGR (Select Graphic Rendition) attributes: `ESC[...m` - reset, bold, and the 8
 *    standard foreground/background colors
 *
 * This class has no Android/Compose dependency so its parsing logic can be unit tested in
 * isolation; the Compose UI layer only ever reads immutable [TerminalSnapshot]s produced by
 * [snapshot], which keeps rendering decoupled from parsing.
 *
 * This is a clean-room implementation written from scratch for AndroSSH; it is not derived from
 * JuiceSSH, Termux, or any other terminal emulator's source code.
 */
class TerminalEmulator(rows: Int, cols: Int) {

    var rows: Int = rows
        private set
    var cols: Int = cols
        private set

    private var grid: Array<Array<TerminalCell>> = Array(rows) { Array(cols) { TerminalCell() } }
    private var cursorRow = 0
    private var cursorCol = 0
    private var currentStyle = TerminalStyle()

    private var parserState = ParserState.Normal
    private val paramBuilder = StringBuilder()

    /** Resizes the screen buffer, preserving as much of the existing content as fits. */
    @Synchronized
    fun resize(newRows: Int, newCols: Int) {
        if (newRows <= 0 || newCols <= 0 || (newRows == rows && newCols == cols)) return
        val oldGrid = grid
        grid = Array(newRows) { r -> Array(newCols) { c -> oldGrid.getOrNull(r)?.getOrNull(c) ?: TerminalCell() } }
        rows = newRows
        cols = newCols
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
    }

    /** Feeds newly received shell output through the parser, updating the screen buffer. */
    @Synchronized
    fun feed(text: String) {
        for (ch in text) processChar(ch)
    }

    /** Clears the screen buffer and resets cursor/style state. */
    @Synchronized
    fun reset() {
        grid = Array(rows) { Array(cols) { TerminalCell() } }
        cursorRow = 0
        cursorCol = 0
        currentStyle = TerminalStyle()
        parserState = ParserState.Normal
        paramBuilder.setLength(0)
    }

    /** Returns an immutable snapshot of the current screen buffer and cursor position. */
    @Synchronized
    fun snapshot(): TerminalSnapshot = TerminalSnapshot(
        rows = grid.map { it.toList() },
        cursorRow = cursorRow,
        cursorCol = cursorCol,
    )

    private fun processChar(ch: Char) {
        when (parserState) {
            ParserState.Normal -> processNormalChar(ch)
            ParserState.Escape -> {
                when (ch) {
                    '[' -> {
                        parserState = ParserState.Csi
                        paramBuilder.setLength(0)
                    }
                    // ESC ( / ESC ) designate the G0/G1 character set and are always followed by
                    // exactly one more byte identifying the set (e.g. `ESC ( B` for US-ASCII, as
                    // commonly emitted by vim/tput). That trailing byte must be consumed here too,
                    // otherwise it would leak into the screen buffer as a printable character.
                    '(', ')' -> parserState = ParserState.CharsetDesignator
                    // Other single-byte escape sequences (e.g. `ESC c` reset, `ESC 7`/`ESC 8` save
                    // /restore cursor) are outside the supported subset - ignore them and return
                    // to normal processing; they consume no further bytes.
                    else -> parserState = ParserState.Normal
                }
            }
            ParserState.CharsetDesignator -> parserState = ParserState.Normal
            ParserState.Csi -> processCsiChar(ch)
        }
    }

    private fun processNormalChar(ch: Char) {
        when (ch) {
            '\u001B' -> parserState = ParserState.Escape
            '\r' -> cursorCol = 0
            '\n' -> lineFeed()
            '\b' -> if (cursorCol > 0) cursorCol--
            '\t' -> cursorCol = (((cursorCol / TAB_WIDTH) + 1) * TAB_WIDTH).coerceAtMost(cols - 1)
            else -> if (!ch.isISOControl()) writeChar(ch)
        }
    }

    private fun writeChar(ch: Char) {
        if (cursorCol >= cols) {
            cursorCol = 0
            lineFeed()
        }
        grid[cursorRow][cursorCol] = TerminalCell(ch, currentStyle)
        cursorCol++
    }

    private fun lineFeed() {
        if (cursorRow == rows - 1) {
            scrollUp()
        } else {
            cursorRow++
        }
    }

    private fun scrollUp() {
        for (r in 0 until rows - 1) {
            grid[r] = grid[r + 1]
        }
        grid[rows - 1] = Array(cols) { TerminalCell() }
    }

    private fun processCsiChar(ch: Char) {
        if (ch in '0'..'9' || ch == ';' || ch == '?') {
            paramBuilder.append(ch)
            return
        }
        val params = paramBuilder.toString()
        parserState = ParserState.Normal
        paramBuilder.setLength(0)
        dispatchCsi(params, ch)
    }

    private fun dispatchCsi(params: String, command: Char) {
        val nums = params.trimStart('?').split(';').mapNotNull { it.toIntOrNull() }
        fun paramOrDefault(index: Int, default: Int) = nums.getOrNull(index)?.takeIf { it > 0 } ?: default

        when (command) {
            'H', 'f' -> {
                cursorRow = (paramOrDefault(0, 1) - 1).coerceIn(0, rows - 1)
                cursorCol = (paramOrDefault(1, 1) - 1).coerceIn(0, cols - 1)
            }
            'A' -> cursorRow = (cursorRow - paramOrDefault(0, 1)).coerceIn(0, rows - 1)
            'B' -> cursorRow = (cursorRow + paramOrDefault(0, 1)).coerceIn(0, rows - 1)
            'C' -> cursorCol = (cursorCol + paramOrDefault(0, 1)).coerceIn(0, cols - 1)
            'D' -> cursorCol = (cursorCol - paramOrDefault(0, 1)).coerceIn(0, cols - 1)
            'K' -> eraseInLine(nums.getOrNull(0) ?: 0)
            'J' -> eraseInDisplay(nums.getOrNull(0) ?: 0)
            'm' -> applySgr(nums)
            else -> Unit // Unsupported sequence: ignore rather than corrupt the buffer.
        }
    }

    private fun eraseInLine(mode: Int) {
        val row = grid[cursorRow]
        when (mode) {
            0 -> for (c in cursorCol until cols) row[c] = TerminalCell()
            1 -> for (c in 0..cursorCol) row[c] = TerminalCell()
            2 -> for (c in 0 until cols) row[c] = TerminalCell()
        }
    }

    private fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseInLine(0)
                for (r in cursorRow + 1 until rows) grid[r] = Array(cols) { TerminalCell() }
            }
            1 -> {
                eraseInLine(1)
                for (r in 0 until cursorRow) grid[r] = Array(cols) { TerminalCell() }
            }
            2 -> for (r in 0 until rows) grid[r] = Array(cols) { TerminalCell() }
        }
    }

    private fun applySgr(codes: List<Int>) {
        if (codes.isEmpty()) {
            currentStyle = TerminalStyle()
            return
        }
        var style = currentStyle
        for (code in codes) {
            style = when (code) {
                0 -> TerminalStyle()
                1 -> style.copy(bold = true)
                22 -> style.copy(bold = false)
                in 30..37 -> style.copy(foreground = code - 30)
                39 -> style.copy(foreground = null)
                in 40..47 -> style.copy(background = code - 40)
                49 -> style.copy(background = null)
                else -> style
            }
        }
        currentStyle = style
    }

    private enum class ParserState { Normal, Escape, CharsetDesignator, Csi }

    private companion object {
        const val TAB_WIDTH = 8
    }
}

/** A single styled character cell in the terminal screen buffer. */
data class TerminalCell(
    val char: Char = ' ',
    val style: TerminalStyle = TerminalStyle(),
)

/**
 * SGR-derived style for a cell. [foreground]/[background] are the standard ANSI color indices
 * (0-7, matching codes 30-37 / 40-47) or `null` for the terminal's default color.
 */
data class TerminalStyle(
    val foreground: Int? = null,
    val background: Int? = null,
    val bold: Boolean = false,
)

/** Immutable, UI-consumable snapshot of the terminal screen buffer at a point in time. */
data class TerminalSnapshot(
    val rows: List<List<TerminalCell>>,
    val cursorRow: Int,
    val cursorCol: Int,
)
