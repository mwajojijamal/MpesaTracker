package com.mpesatracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DeepTealPrimary = Color(0xFF005B4F)
val DeepGreenSecondary = Color(0xFF1B5E20)
val MintAccent = Color(0xFF4DB6AC)
val LightGreyBackground = Color(0xFFF6F8F7)
val CardSurfaceWhite = Color(0xFFFFFFFF)
val ExpenseRed = Color(0xFFC62828)
val IncomeGreen = Color(0xFF2E7D32)

private val LightColorPalette = lightColorScheme(
    primary = DeepTealPrimary,
    onPrimary = Color.White,
    secondary = DeepGreenSecondary,
    onSecondary = Color.White,
    tertiary = MintAccent,
    background = LightGreyBackground,
    surface = CardSurfaceWhite,
    onBackground = Color(0xFF191C1B),
    onSurface = Color(0xFF191C1B),
    error = ExpenseRed
)

@Composable
fun MpesaTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorPalette,
        content = content
    )
}