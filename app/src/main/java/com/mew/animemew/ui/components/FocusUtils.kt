package com.mew.animemew.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mew.animemew.ui.theme.NeonPurple

// Color neón unificado para foco
private val FocusColor = NeonPurple
private val FocusGlow = NeonPurple.copy(alpha = 0.4f)

/**
 * tvFocusable — Para Boxes personalizados.
 *
 * ANTI-TEMBLOR:
 * - Usa graphicsLayer (no afecta layout)
 * - Animación spring con dampingRatio alto (sin rebote excesivo)
 * - El border NO cambia el tamaño (siempre 0.dp cuando no enfocado, pero
 *   usamos padding interno para reservar el espacio, evitando re-layout)
 * - scale por defecto 1.0f (sin escala) para evitar temblor en grids/LazyRow
 *   Cuando se quiere escala, usar valores pequeños (1.02f max)
 *
 * IMPORTANTE: Debe ir PRIMERO en la cadena de modificadores,
 * ANTES de .clip() y .background(), para que el borde no sea recortado.
 *
 * Uso:
 * Box(modifier = Modifier
 *     .tvFocusable(shape = CircleShape, onClick = { ... })
 *     .size(42.dp)
 *     .clip(CircleShape)
 *     .background(...)
 * )
 */
fun Modifier.tvFocusable(
    shape: Shape = RectangleShape,
    scale: Float = 1.0f,
    borderWidth: Dp = 2.dp,
    onClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null  // NUEVO v9.1
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Animación suave con spring (sin rebote excesivo que cause temblor visual)
    val scaleValue by animateFloatAsState(
        targetValue = if (isFocused) scale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tv_scale"
    )

    this
        // NUEVO: aplicar focusRequester si se proporciona
        .then(
            if (focusRequester != null) Modifier.focusRequester(focusRequester)
            else Modifier
        )
        // graphicsLayer NO afecta layout measurement, solo render visual
        .graphicsLayer {
            scaleX = scaleValue
            scaleY = scaleValue
            // Sombra/elevación cuando está enfocado (sin afectar layout)
            shadowElevation = if (isFocused) 16f else 0f
            ambientShadowColor = FocusGlow
            spotShadowColor = FocusColor
        }
        // Border siempre presente (transparente cuando no enfocado)
        // para evitar cambios de tamaño que causan temblor
        .border(
            width = borderWidth,
            color = if (isFocused) FocusColor else Color.Transparent,
            shape = shape
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
        .focusable(interactionSource = interactionSource)
}

/**
 * tvFocusVisual — Para componentes que YA son focuseables (ej: FilterChip).
 * Solo añade el efecto visual (border + sombra) sin añadir focusable/clickable.
 *
 * IMPORTANTE: Esta versión NO usa escala por defecto para evitar temblor
 * en chips/grids. Si necesitas escala, pásala explícitamente.
 *
 * Uso:
 * FilterChip(
 *     modifier = Modifier.tvFocusVisual(shape = RoundedCornerShape(16.dp)),
 *     onClick = { ... },
 *     ...
 * )
 */
fun Modifier.tvFocusVisual(
    shape: Shape = RectangleShape,
    scale: Float = 1.0f,
    borderWidth: Dp = 2.dp
): Modifier = composed {
    var isFocused by remember { mutableStateOf(false) }

    val scaleValue by animateFloatAsState(
        targetValue = if (isFocused) scale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tv_visual_scale"
    )

    this
        .onFocusChanged { isFocused = it.isFocused }
        .graphicsLayer {
            scaleX = scaleValue
            scaleY = scaleValue
            shadowElevation = if (isFocused) 12f else 0f
            ambientShadowColor = FocusGlow
            spotShadowColor = FocusColor
        }
        .border(
            width = borderWidth,
            color = if (isFocused) FocusColor else Color.Transparent,
            shape = shape
        )
}

/**
 * tvFocusableSlim — Variante con scale 1.0 forzado (sin escala visual)
 * Recomendado para grids densos y LazyRows donde CUALQUIER escala causa temblor.
 * Solo usa border + sombra para indicar foco.
 */
fun Modifier.tvFocusableSlim(
    shape: Shape = RectangleShape,
    borderWidth: Dp = 3.dp,
    onClick: (() -> Unit)? = null
): Modifier = tvFocusable(
    shape = shape,
    scale = 1.0f,
    borderWidth = borderWidth,
    onClick = onClick
)
