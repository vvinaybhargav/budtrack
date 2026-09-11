package com.vinay.fintrack.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PfColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val hairline: Color,
    val accent: Color,
    val accent100: Color,
    val accent200: Color,
    val accent400: Color,
    val accent600: Color,
    val accent700: Color,
    val accent800: Color,
    val accent2: Color,
    val accent2_100: Color,
    val accent2_800: Color,
    val neutral100: Color,
    val neutral700: Color,
    val neutral800: Color,
    val isDark: Boolean
)

val DarkPfColors = PfColors(
    bg = Color(0xFF0C0C0E),
    surface = Color(0xFF161618),
    surface2 = Color(0xFF222226),
    text = Color(0xFFF4F4F6),
    muted = Color(0xFF94949E),
    hairline = Color(0xFF2E2E34),
    accent = Color(0xFFF4F4F6),
    accent100 = Color(0xFF222226),
    accent200 = Color(0xFF333338),
    accent400 = Color(0xFF94949E),
    accent600 = Color(0xFFD4D4D8),
    accent700 = Color(0xFFE4E4E7),
    accent800 = Color(0xFFFFFFFF),
    accent2 = Color(0xFFD4D4D8),
    accent2_100 = Color(0xFF222226),
    accent2_800 = Color(0xFFF4F4F6),
    neutral100 = Color(0xFF161618),
    neutral700 = Color(0xFF71717A),
    neutral800 = Color(0xFFD4D4D8),
    isDark = true
)

val LightPfColors = PfColors(
    bg = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF1F3F5),
    text = Color(0xFF111827),
    muted = Color(0xFF6B7280),
    hairline = Color(0xFFE5E7EB),
    accent = Color(0xFF111827),
    accent100 = Color(0xFFF3F4F6),
    accent200 = Color(0xFFE5E7EB),
    accent400 = Color(0xFF6B7280),
    accent600 = Color(0xFF374151),
    accent700 = Color(0xFF1F2937),
    accent800 = Color(0xFF111827),
    accent2 = Color(0xFF111827),
    accent2_100 = Color(0xFFF3F4F6),
    accent2_800 = Color(0xFF111827),
    neutral100 = Color(0xFFF3F4F6),
    neutral700 = Color(0xFF4B5563),
    neutral800 = Color(0xFF1F2937),
    isDark = false
)

val LocalPfColors = staticCompositionLocalOf { DarkPfColors }

object Pf {
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.bg
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.surface
    val Surface2: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.surface2
    val Text: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.muted
    val Hairline: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.hairline

    val Accent: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.accent
    val Accent100: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.accent100
    val Accent200: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.accent200
    val Accent400: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.accent400
    val Accent600: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.accent600
    val Accent700: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.accent700
    val Accent800: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.accent800

    val Accent2: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.accent2
    val Accent2_100: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.accent2_100
    val Accent2_800: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.accent2_800

    val Neutral100: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.neutral100
    val Neutral700: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.neutral700
    val Neutral800: Color @Composable @ReadOnlyComposable get() = LocalPfColors.current.neutral800

    val isDark: Boolean @Composable @ReadOnlyComposable get() = LocalPfColors.current.isDark
    val OnAccent: Color @Composable @ReadOnlyComposable get() = if (LocalPfColors.current.isDark) Color(0xFF111827) else Color.White
    val Rose: Color = Color(0xFFF43F5E)
    val Danger: Color = Color(0xFFEF4444)
}

object Radius {
    val Xl = RoundedCornerShape(28.dp)
    val Lg = RoundedCornerShape(20.dp)
    val Md = RoundedCornerShape(16.dp)
    val Sm = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(999.dp)
}

object Space {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
}

private val FinTrackTypography = Typography(
    displayLarge = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp),
    headlineSmall = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
)

@Composable
fun FinTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkPfColors else LightPfColors
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = Color(0xFF111827),
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.muted,
            outline = colors.hairline
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = Color.White,
            background = colors.bg,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.muted,
            outline = colors.hairline
        )
    }

    CompositionLocalProvider(LocalPfColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FinTrackTypography,
            content = content
        )
    }
}

