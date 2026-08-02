package com.mew.animemew.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// AnimeMew Color Palette
val NeonPurple = Color(0xFFB026FF)
val DeepPurple = Color(0xFF6200EA)
val NeonMagenta = Color(0xFFFF0055)

val NeonGradient = Brush.linearGradient(
    colors = listOf(NeonPurple, NeonMagenta)
)

// Dark Theme Colors
val DarkBackground = Color(0xFF09090B)
val DarkSurface = Color(0xFF141416)
val DarkSurfaceVariant = Color(0xFF1F1F22)
val TextPrimaryDark = Color(0xFFEDEDED)
val TextSecondaryDark = Color(0xFFA0A0A5)

// Light Theme Colors
val LightBackground = Color(0xFFF9F9FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0F0F4)
val TextPrimaryLight = Color(0xFF121212)
val TextSecondaryLight = Color(0xFF6B6B70)

// =====================================================
//  NUEVOS PINCELES DE FONDO (modernos / llamativos)
//  No reemplazan ninguno anterior; son aditivos.
// =====================================================

// Fondo principal de la app: gradiente vertical sutil
// con un toque de púrpura en la parte superior que da
// profundidad y hace resaltar el neón del branding.
val AppBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1A0B2E),   // Púrpura profundo en la parte alta (glow del logo)
        Color(0xFF0F0817),   // Transición oscura con tinte púrpura
        DarkBackground,      // Negro puro
        DarkBackground       // Negro puro abajo (estable)
    )
)

// Glow horizontal sutil detrás del top bar.
// Da sensación de "luz neón" sin saturar.
val TopBarGlowBrush = Brush.horizontalGradient(
    colors = listOf(
        NeonPurple.copy(alpha = 0.18f),
        Color.Transparent,
        NeonMagenta.copy(alpha = 0.12f)
    )
)

// Glow circular radial detrás del logo para que "resalte".
val LogoGlowBrush = Brush.radialGradient(
    colors = listOf(
        NeonPurple.copy(alpha = 0.45f),
        NeonPurple.copy(alpha = 0.10f),
        Color.Transparent
    )
)

// Línea de acento vertical para los títulos de sección.
val SectionAccentBrush = Brush.verticalGradient(
    colors = listOf(
        NeonPurple,
        NeonMagenta
    )
)
