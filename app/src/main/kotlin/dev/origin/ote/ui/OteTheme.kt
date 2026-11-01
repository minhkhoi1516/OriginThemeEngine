package dev.origin.ote.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.origin.ote.model.AppPreferences
import dev.origin.ote.model.ColorMode
import dev.origin.ote.model.UiStyle

@Composable
fun OteTheme(preferences: AppPreferences, content: @Composable () -> Unit) {
    val dark = when (preferences.colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.DARK -> true
        ColorMode.LIGHT -> false
    }
    val context = LocalContext.current
    val colors = when {
        preferences.style == UiStyle.MATERIAL && preferences.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFC5B5FA), secondary = Color(0xFFB9CCD9),
            background = Color(0xFF101116), surface = Color(0xFF191B23), surfaceContainer = Color(0xFF232630))
        else -> lightColorScheme(primary = Color(0xFF66508F), secondary = Color(0xFF536679),
            background = Color(0xFFF7F7FB), surface = Color.White, surfaceContainer = Color(0xFFEFEEF5))
    }
    val radius = if (preferences.style == UiStyle.MATERIAL) 20.dp else 28.dp
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        small = RoundedCornerShape(14.dp), medium = RoundedCornerShape(radius), large = RoundedCornerShape(radius),
        extraLarge = RoundedCornerShape(32.dp)), content = content)
}

@Composable
fun OteSurface(style: UiStyle, reducedMotion: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val base = MaterialTheme.colorScheme.surfaceContainer
    // Glass uses cheap translucent compositing; no claim of sampling a blurred backdrop.
    val colors = if (style == UiStyle.GLASS && !reducedMotion)
        listOf(MaterialTheme.colorScheme.primary.copy(alpha = .12f), base.copy(alpha = .92f), base)
    else listOf(base, base)
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = MaterialTheme.shapes.large) {
        Column(Modifier.background(Brush.linearGradient(colors)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
