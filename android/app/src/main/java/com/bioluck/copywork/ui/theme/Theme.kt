package com.bioluck.copywork.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF2C3E50), onPrimary = Color.White,
    secondary = Color(0xFF4E6E5D), onSecondary = Color.White,
    tertiary = Color(0xFF899B72), background = Color(0xFFF8F7F2),
    surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF0EFEA),
    onSurface = Color(0xFF202A32), outline = Color(0xFFD9DBD4)
)

@Composable
fun CopyworkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography(), content = content)
}
