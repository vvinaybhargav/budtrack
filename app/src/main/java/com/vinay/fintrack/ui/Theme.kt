package com.vinay.fintrack.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Pf {
    val Bg = Color(0xFF0D0D0B)
    val Surface = Color(0xFF1B1A17)
    val Surface2 = Color(0xFF282622)
    val Text = Color(0xFFF7F4EB)
    val Muted = Color(0xFF9E988A)
    val Hairline = Color(0xFFEBE5D8).copy(alpha = 0.12f)

    val Accent = Color(0xFFEE5E4C) // Claude Coral
    val Accent100 = Color(0xFF3B1E1A)
    val Accent400 = Color(0xFFF79489)
    val Accent600 = Color(0xFFD54C3A)
    val Accent700 = Color(0xFFB33A29)
    val Accent800 = Color(0xFFFBE4E1)

    val Accent2 = Color(0xFF4CAE7C) // Warm Green
    val Accent2_100 = Color(0xFF1B3D2B)
    val Accent2_800 = Color(0xFFCBEAD7)

    val Neutral100 = Color(0xFF262422)
    val Neutral700 = Color(0xFF70685E)
    val Neutral800 = Color(0xFFDED8CD)
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
fun FinTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pf.Accent,
            onPrimary = Color.White,
            background = Pf.Bg,
            onBackground = Pf.Text,
            surface = Pf.Surface,
            onSurface = Pf.Text,
            surfaceVariant = Pf.Surface2,
            onSurfaceVariant = Pf.Muted,
            outline = Pf.Hairline
        ),
        typography = FinTrackTypography,
        content = content
    )
}
