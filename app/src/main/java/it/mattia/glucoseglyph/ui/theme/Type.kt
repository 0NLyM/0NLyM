package it.mattia.glucoseglyph.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System monospace stands in for Nothing's dot-matrix "Ndot" family, which isn't
// freely redistributable; the letterforms still read as glanceable/technical.
private val mono = FontFamily.Monospace

val NothingTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = mono,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = mono,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = mono,
        fontSize = 11.sp,
        letterSpacing = 1.sp
    )
)
