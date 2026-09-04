package com.androssh.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure-logic [TerminalEmulator] parser/screen-buffer, independent of Compose. */
class TerminalEmulatorTest {

    private fun TerminalSnapshot.rowText(row: Int): String =
        rows[row].joinToString("") { it.char.toString() }

    @Test
    fun `printable characters wrap to the next line at the last column`() {
        val emulator = TerminalEmulator(rows = 3, cols = 5)
        emulator.feed("Hello World\n")
        val snapshot = emulator.snapshot()

        assertEquals(" Worl", snapshot.rowText(0))
        assertEquals("d    ", snapshot.rowText(1))
        assertEquals(2, snapshot.cursorRow)
        assertEquals(1, snapshot.cursorCol)
    }

    @Test
    fun `carriage return moves cursor to column zero and backspace moves it back one`() {
        val emulator = TerminalEmulator(rows = 2, cols = 10)
        emulator.feed("abc\bdef\r123")
        val snapshot = emulator.snapshot()

        assertTrue(snapshot.rowText(0).startsWith("123ef"))
    }

    @Test
    fun `CSI cursor position moves the cursor to the requested 1-based row and column`() {
        val emulator = TerminalEmulator(rows = 5, cols = 5)
        emulator.feed("\u001B[3;2Hx")
        val snapshot = emulator.snapshot()

        assertEquals('x', snapshot.rows[2][1].char)
    }

    @Test
    fun `CSI relative cursor movement moves up down left and right`() {
        val emulator = TerminalEmulator(rows = 5, cols = 5)
        emulator.feed("\u001B[3;3H") // start at row index 2, col index 2
        emulator.feed("\u001B[1A") // up one
        emulator.feed("\u001B[2C") // right two
        emulator.feed("x")
        val snapshot = emulator.snapshot()

        assertEquals('x', snapshot.rows[1][4].char)
    }

    @Test
    fun `erase in line clears from the cursor to the end of the line by default`() {
        val emulator = TerminalEmulator(rows = 1, cols = 10)
        emulator.feed("abcdefghij")
        emulator.feed("\u001B[5D") // move cursor back 5 -> column 5
        emulator.feed("\u001B[K")
        val snapshot = emulator.snapshot()

        assertEquals("abcde     ", snapshot.rowText(0))
    }

    @Test
    fun `erase in display clears from the cursor to the end of the screen`() {
        val emulator = TerminalEmulator(rows = 3, cols = 4)
        emulator.feed("aaaa\r\nbbbb\r\ncccc")
        emulator.feed("\u001B[2;1H")
        emulator.feed("\u001B[J")
        val snapshot = emulator.snapshot()

        assertEquals("aaaa", snapshot.rowText(0))
        assertEquals("    ", snapshot.rowText(1))
        assertEquals("    ", snapshot.rowText(2))
    }

    @Test
    fun `SGR sets foreground color and bold and resets on code 0`() {
        val emulator = TerminalEmulator(rows = 1, cols = 5)
        emulator.feed("\u001B[31;1mRed\u001B[0mX")
        val snapshot = emulator.snapshot()

        val redCell = snapshot.rows[0][0]
        val resetCell = snapshot.rows[0][3]

        assertEquals(1, redCell.style.foreground)
        assertTrue(redCell.style.bold)
        assertEquals(null, resetCell.style.foreground)
        assertFalse(resetCell.style.bold)
    }

    @Test
    fun `unsupported escape sequences are ignored without corrupting the buffer`() {
        val emulator = TerminalEmulator(rows = 1, cols = 5)
        emulator.feed("\u001B(Bhi")
        val snapshot = emulator.snapshot()

        assertEquals("hi   ", snapshot.rowText(0))
    }

    @Test
    fun `tab advances to the next multiple of 8 columns, clamped to the last column`() {
        val emulator = TerminalEmulator(rows = 1, cols = 20)
        emulator.feed("ab\tx")
        val snapshot = emulator.snapshot()

        assertEquals('x', snapshot.rows[0][8].char)

        val narrowEmulator = TerminalEmulator(rows = 1, cols = 5)
        narrowEmulator.feed("ab\tx")
        val narrowSnapshot = narrowEmulator.snapshot()

        assertEquals('x', narrowSnapshot.rows[0][4].char)
    }

    @Test
    fun `resize preserves existing content that still fits`() {
        val emulator = TerminalEmulator(rows = 2, cols = 4)
        emulator.feed("ab")
        emulator.resize(newRows = 3, newCols = 6)
        val snapshot = emulator.snapshot()

        assertEquals(3, snapshot.rows.size)
        assertEquals(6, snapshot.rows[0].size)
        assertEquals("ab", snapshot.rowText(0).substring(0, 2))
    }
}
