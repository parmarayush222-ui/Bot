package com.mulerun.bot.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF11130F)
val Cream = Color(0xFFF4F1E8)
val Lime = Color(0xFFC8F169)
val Rust = Color(0xFFFF7657)
val Muted = Color(0xFF9DA292)
val Panel = Color(0xFF1B1E18)

private val BotColors = darkColorScheme(
    primary = Lime,
    onPrimary = Ink,
    secondary = Rust,
    background = Ink,
    onBackground = Cream,
    surface = Panel,
    onSurface = Cream,
    outline = Color(0xFF3B4035),
)

@Composable
fun BotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BotColors,
        typography = MaterialTheme.typography.copy(
            displayLarge = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 52.sp,
                lineHeight = 54.sp,
            ),
            headlineSmall = TextStyle(
                fontFamily = FontFamily.Serif,
                fontSize = 27.sp,
                lineHeight = 31.sp,
            ),
            titleMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 1.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 16.sp,
                lineHeight = 23.sp,
            ),
        ),
        content = content,
    )
}
