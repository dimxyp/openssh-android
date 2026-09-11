package com.androssh.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure-logic helpers backing the hidden-input-capture terminal text field
 * (`applyInput`/`submitLine` in [AndroSshApp.kt]), independent of Compose runtime state.
 */
class AndroSshAppInputTest {

    @Test
    fun `no change produces no bytes`() {
        assertEquals("", computeInputDiff("ls", "ls"))
    }

    @Test
    fun `pure append streams only the appended suffix`() {
        assertEquals(" -la", computeInputDiff("ls", "ls -la"))
    }

    @Test
    fun `pure trailing backspace streams only backspaces`() {
        assertEquals("\b\b\b", computeInputDiff("ls -la", "ls"))
    }

    @Test
    fun `autocorrect-style whole-word replacement only backspaces the changed suffix`() {
        // Autocorrect turning "teh" into "the" shares the leading "t", so only the last two
        // characters need to be replaced - not the whole three-character word (which the old
        // backspace-everything-and-retype fallback would have done).
        val diff = computeInputDiff("teh", "the")
        assertEquals("\b\bhe", diff)
    }

    @Test
    fun `mid-line word swap only touches the differing region`() {
        // "quikc brown" -> "quick brown": the differing region is isolated to "kc" vs "ck",
        // everything else (both the common prefix and the untouched trailing text) must not be
        // re-sent.
        val diff = computeInputDiff("quikc", "quick")
        assertEquals("\b\bck", diff)
    }

    @Test
    fun `first submit is never treated as a duplicate`() {
        // Mirrors the real initial value of `lastSubmitAtMillis` (far in the past), so that even
        // a submit shortly after device boot - when SystemClock.elapsedRealtime() is itself close
        // to 0 - is not mistaken for a duplicate.
        assertFalse(isDuplicateSubmit(lastSubmitAtMillis = Long.MIN_VALUE / 2, nowMillis = 0L))
    }

    @Test
    fun `a second submit signal immediately after the first is a duplicate`() {
        // isDuplicateSubmit is a pure function of two Long millisecond values, so any monotonic
        // clock source works for testing it - production code feeds it SystemClock.elapsedRealtime()
        // values, but the arbitrary offsets used here (not wall-clock timestamps) exercise the same
        // comparison. Simulates two of the three Enter-handling paths (onKeyEvent,
        // KeyboardActions#onSend, trailing "\n" in applyInput) firing for the same physical key
        // press, a few milliseconds apart.
        assertTrue(isDuplicateSubmit(lastSubmitAtMillis = 1_000L, nowMillis = 1_010L))
    }

    @Test
    fun `a later separate enter press is not a duplicate`() {
        assertFalse(isDuplicateSubmit(lastSubmitAtMillis = 1_000L, nowMillis = 5_000L))
    }
}
