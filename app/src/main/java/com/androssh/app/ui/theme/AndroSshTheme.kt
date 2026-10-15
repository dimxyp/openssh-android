package com.androssh.app.ui.theme

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.TextButton as M3TextButton

private val AndroSshColors = darkColorScheme(
    primary = Color(0xFF31D0AA),
    onPrimary = Color(0xFF00201A),
    secondary = Color(0xFF8FA8A1),
    background = Color(0xFF0A0D10),
    onBackground = Color(0xFFE3E8E6),
    surface = Color(0xFF0A0D10),
    onSurface = Color(0xFFE3E8E6),
    surfaceVariant = Color(0xFF1A2025),
    onSurfaceVariant = Color(0xFFB4BDBA),
    surfaceContainer = Color(0xFF12171B),
    surfaceContainerHigh = Color(0xFF181E23),
    surfaceContainerHighest = Color(0xFF1F262B),
    outline = Color(0xFF4A5459),
    error = Color(0xFFFF6B6B),
)

private val SquareShape = RoundedCornerShape(4.dp)

private val AndroSshShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = SquareShape,
    medium = SquareShape,
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

@Composable
fun AndroSshTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AndroSshColors, shapes = AndroSshShapes, content = content)
}

// Material 3 buttons are pill-shaped and not themeable via Shapes, so these squarer drop-in replacements are used instead.
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable RowScope.() -> Unit,
) = M3Button(onClick, modifier, enabled, shape = SquareShape, colors = colors, content = content)

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = M3OutlinedButton(onClick, modifier, enabled, shape = SquareShape, content = content)

@Composable
fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = M3TextButton(onClick, modifier, enabled, shape = SquareShape, content = content)
