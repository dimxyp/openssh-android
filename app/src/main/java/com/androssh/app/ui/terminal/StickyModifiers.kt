package com.androssh.app.ui.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.connectbot.terminal.ModifierManager

/**
 * Ctrl/Alt state driven by the on-screen CTRL/ALT keys. The terminal view consults it for every
 * key it handles (soft keyboard or hardware) and clears it after one key press, so tapping CTRL
 * and then typing `c` sends Ctrl+C.
 */
class StickyModifiers : ModifierManager {
    var ctrl by mutableStateOf(false)
    var alt by mutableStateOf(false)

    override fun isCtrlActive(): Boolean = ctrl

    override fun isAltActive(): Boolean = alt

    override fun isShiftActive(): Boolean = false

    override fun clearTransients() {
        ctrl = false
        alt = false
    }
}
