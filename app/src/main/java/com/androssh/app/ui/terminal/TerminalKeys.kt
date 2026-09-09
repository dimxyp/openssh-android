package com.androssh.app.ui.terminal

/**
 * Original mapping of terminal control keys to the raw byte sequences a shell
 * expects to receive. These are standard, publicly documented ANSI / VT100
 * escape sequences and ASCII control codes (see e.g. the ECMA-48 / XTerm
 * control sequence references) - none of this is derived from any
 * third-party application's source code.
 */
enum class TerminalKey(val label: String, val sequence: String) {
    Escape("Esc", "\u001B"),
    Tab("Tab", "\t"),
    ArrowUp("\u2191", "\u001B[A"),
    ArrowDown("\u2193", "\u001B[B"),
    ArrowRight("\u2192", "\u001B[C"),
    ArrowLeft("\u2190", "\u001B[D"),
    Home("Home", "\u001B[H"),
    End("End", "\u001B[F"),
    PageUp("PgUp", "\u001B[5~"),
    PageDown("PgDn", "\u001B[6~"),
    Insert("Ins", "\u001B[2~"),
    Slash("/", "/"),
    Pipe("|", "|"),
    Minus("-", "-"),
    Delete("Del", "\u001B[3~"),
    F1("F1", "\u001BOP"),
    F2("F2", "\u001BOQ"),
    F3("F3", "\u001BOR"),
    F4("F4", "\u001BOS"),
    F5("F5", "\u001B[15~"),
    F6("F6", "\u001B[17~"),
    F7("F7", "\u001B[18~"),
    F8("F8", "\u001B[19~"),
    F9("F9", "\u001B[20~"),
    F10("F10", "\u001B[21~"),
    F11("F11", "\u001B[23~"),
    F12("F12", "\u001B[24~"),
}

/**
 * Sends a control character equivalent to holding Ctrl and pressing [letter],
 * e.g. Ctrl+C -> 0x03 (ETX), Ctrl+D -> 0x04 (EOT). Follows the standard
 * ASCII control-code convention where Ctrl+<letter> maps to
 * `letter.uppercase() - 'A' + 1`.
 */
fun ctrlSequenceFor(letter: Char): String {
    val upper = letter.uppercaseChar()
    require(upper in 'A'..'_') { "No control code for '$letter'" }
    val code = upper.code - 'A'.code + 1
    return code.toChar().toString()
}

/** Rows of extra keys shown in the [com.androssh.app.ui.terminal.ExtraKeysBar]. */
object ExtraKeysLayout {
    /** First (upper) row of the compact two-row bar. */
    val topRow: List<TerminalKey> = listOf(
        TerminalKey.Escape,
        TerminalKey.Slash,
        TerminalKey.Pipe,
        TerminalKey.Minus,
        TerminalKey.Home,
        TerminalKey.End,
        TerminalKey.PageUp,
    )

    /** Second-row keys shown before the Ctrl/Alt modifier keys. */
    val bottomRowLeading: List<TerminalKey> = listOf(
        TerminalKey.Tab,
    )

    /** Second-row keys shown after the Ctrl/Alt modifier keys. */
    val bottomRowTrailing: List<TerminalKey> = listOf(
        TerminalKey.ArrowLeft,
        TerminalKey.ArrowUp,
        TerminalKey.ArrowDown,
        TerminalKey.ArrowRight,
        TerminalKey.PageDown,
    )

    val functionRow: List<TerminalKey> = listOf(
        TerminalKey.F1,
        TerminalKey.F2,
        TerminalKey.F3,
        TerminalKey.F4,
        TerminalKey.F5,
        TerminalKey.F6,
        TerminalKey.F7,
        TerminalKey.F8,
        TerminalKey.F9,
        TerminalKey.F10,
        TerminalKey.F11,
        TerminalKey.F12,
    )
}
