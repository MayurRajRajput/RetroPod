package com.retropod.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal val Night = Color(0xFF000000)
internal val SurfaceNight = Color(0xFF0F0F10)
internal val SurfaceRaised = Color(0xFF1A1A1C)
internal val Muted = Color(0xFF8B8B90)
internal val White = Color(0xFFF4F4F5)

/** Accent palette; the selected index is changed from Settings > Appearance. */
internal object Pal {
    val accents = listOf(
        "Silver" to Color(0xFFE8E8E8),
        "Ice" to Color(0xFFBFD9FF),
        "Rose" to Color(0xFFFFC2D1),
        "Mint" to Color(0xFFB8F2D0),
        "Amber" to Color(0xFFFFD38A)
    )
    var accentIndex by mutableIntStateOf(0)
    val accent: Color get() = accents[accentIndex.coerceIn(0, accents.lastIndex)].second
}

internal val Accent: Color get() = Pal.accent

/** Frosted dark card used across the redesigned screens. */
internal fun Modifier.glass(shape: Shape = RoundedCornerShape(22.dp)): Modifier =
    this.clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF1C1C1E), Color(0xFF0D0D0E))), shape)
        .border(1.dp, Brush.verticalGradient(listOf(Color(0x48FFFFFF), Color(0x10FFFFFF))), shape)
