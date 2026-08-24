package it.mattia.glucoseglyph.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NothingBlack = Color(0xFF000000)
val NothingCard = Color(0xFF141414)
val NothingWhite = Color(0xFFF5F5F0)
val NothingGrey = Color(0xFF8A8A8A)
val NothingRed = Color(0xFFE0122A)

private val NothingColorScheme = darkColorScheme(
    primary = NothingRed,
    onPrimary = NothingWhite,
    secondary = NothingGrey,
    background = NothingBlack,
    onBackground = NothingWhite,
    surface = NothingCard,
    onSurface = NothingWhite,
    error = NothingRed,
)

@Composable
fun GlucoseGlyphTheme(content: @Composable () -> Unit) {
    // Deliberately theme-locked: the Glyph hardware itself is a fixed black-and-white
    // dot matrix, so the companion screen mirrors that instead of following system theme.
    MaterialTheme(
        colorScheme = NothingColorScheme,
        typography = NothingTypography,
        content = content
    )
}
