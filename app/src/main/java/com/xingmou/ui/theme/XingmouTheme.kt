package com.xingmou.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFF8F5F0)
val PaperSurface = Color(0xFFFFFCF8)
val Ink = Color(0xFF24262B)
val Muted = Color(0xFF66707A)
val Rule = Color(0xFFE1DDD7)
val Coral = Color(0xFFD96855)
val CoralDark = Color(0xFF9E3F32)
val CoralSoft = Color(0xFFF7DED7)
val ExistingBlue = Color(0xFF4F86F7)
val BlueSoft = Color(0xFFDDE8FF)
val Success = Color(0xFF4E8A70)
val Warning = Color(0xFF9A5A22)
val Error = Color(0xFFB4473E)

private val StandardColors = lightColorScheme(
    primary = Coral,
    onPrimary = Ink,
    primaryContainer = CoralSoft,
    onPrimaryContainer = CoralDark,
    secondary = ExistingBlue,
    onSecondary = Color(0xFFF8FAFF),
    secondaryContainer = BlueSoft,
    onSecondaryContainer = Color(0xFF183D7A),
    background = Paper,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF1EDE7),
    onSurfaceVariant = Muted,
    outline = Rule,
    error = Error
)

private val HighContrastColors = lightColorScheme(
    primary = Color(0xFF9E2F20),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9D2),
    onPrimaryContainer = Color(0xFF4A1008),
    secondary = Color(0xFF174E9B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E4FF),
    onSecondaryContainer = Color(0xFF062B62),
    background = Color(0xFFFFFBF8),
    onBackground = Color(0xFF111318),
    surface = Color.White,
    onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xFFF1ECE7),
    onSurfaceVariant = Color(0xFF3D454D),
    outline = Color(0xFF5C6268),
    error = Color(0xFF8B1E16)
)

private val XingmouTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )
)

private val XingmouShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun XingmouTheme(highContrast: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (highContrast) HighContrastColors else StandardColors,
        typography = XingmouTypography,
        shapes = XingmouShapes,
        content = content
    )
}
