package com.androssh.app.ui.terminal

import androidx.compose.ui.graphics.Color

/**
 * Single place holding the terminal's palette, so the look can be tweaked without touching the
 * composables that use it.
 */
object TerminalColors {
    /** Dark, desaturated teal used behind the character grid (not pure black). */
    val Background = Color(0xFF0E3A44)

    /** Warm off-white used for text that carries no explicit ANSI foreground color. */
    val Foreground = Color(0xFFE8E4D8)

    /** Solid block drawn at the cursor cell. */
    val Cursor = Color(0xFFD8D8D0)

    /** Slightly lighter teal for the extra-keys bar so it reads as a separate strip. */
    val KeyBarBackground = Color(0xFF15505C)

    /** Label color of the extra-keys bar. */
    val KeyBarForeground = Color(0xFFE8E4D8)
}
