package com.androssh.app.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalKeysTest {
    @Test
    fun `arrow keys form a contiguous directional cluster in the lower row`() {
        assertEquals(
            listOf(
                TerminalKey.ArrowLeft,
                TerminalKey.ArrowUp,
                TerminalKey.ArrowDown,
                TerminalKey.ArrowRight,
            ),
            ExtraKeysLayout.bottomRowTrailing.take(4),
        )
    }
}
