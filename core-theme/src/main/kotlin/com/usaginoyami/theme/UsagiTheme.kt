package com.usaginoyami.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cores base da marca UsagiNoYami (tiradas do mascote). */
object UsagiColors {
    val Black = Color(0xFF000000)
    val Background = Color(0xFF0B0B0D)
    val Surface = Color(0xFF1A1A1E)
    val Silver = Color(0xFFD8D8DC)
}

/** Cor de destaque da suíte. O padrão é o vermelho neon do mascote. */
data class UsagiPalette(val name: String, val accent: Color, val glow: Color)

object UsagiPalettes {
    val Classic = UsagiPalette("Vermelho", Color(0xFFE50914), Color(0xFFFF2A2A))
    val Violet = UsagiPalette("Violeta", Color(0xFF8E24FF), Color(0xFFB66BFF))
    val Cyan = UsagiPalette("Ciano", Color(0xFF00B8D4), Color(0xFF00E5FF))
    val all = listOf(Classic, Violet, Cyan)
}

val LocalUsagiPalette = staticCompositionLocalOf { UsagiPalettes.Classic }

@Composable
fun UsagiTheme(
    palette: UsagiPalette = UsagiPalettes.Classic,
    content: @Composable () -> Unit,
) {
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = UsagiColors.Silver,
        secondary = palette.glow,
        background = UsagiColors.Background,
        onBackground = UsagiColors.Silver,
        surface = UsagiColors.Surface,
        onSurface = UsagiColors.Silver,
    )
    CompositionLocalProvider(LocalUsagiPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Tecla padrão da suíte: cinza escuro com contorno na cor de destaque. */
@Composable
fun UsagiKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val palette = LocalUsagiPalette.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (filled) palette.accent else UsagiColors.Surface)
            .border(1.dp, if (filled) palette.glow else palette.accent.copy(alpha = 0.55f), shape)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = UsagiColors.Silver,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Botãozinho de texto para barras superiores e ações secundárias. */
@Composable
fun UsagiChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalUsagiPalette.current
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        color = UsagiColors.Silver,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(shape)
            .border(1.dp, palette.accent.copy(alpha = 0.7f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
