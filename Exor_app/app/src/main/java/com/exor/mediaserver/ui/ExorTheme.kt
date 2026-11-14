package com.exor.mediaserver.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ExorColors {
    val Bg = Color(0xFF050818)
    val Bg2 = Color(0xFF0B1030)
    val Cyan = Color(0xFF38E1FF)
    val Purple = Color(0xFF8A5CFF)
    val Magenta = Color(0xFFD94CFF)
    val TextDim = Color(0xFFA9B0C8)
    val Good = Color(0xFF3DDC84)
    val Warn = Color(0xFFFFB84D)
}

@Composable
fun ExorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ExorColors.Cyan,
            secondary = ExorColors.Purple,
            background = ExorColors.Bg,
            surface = ExorColors.Bg2,
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}
