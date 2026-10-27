package com.usaginoyami.thumb

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usaginoyami.theme.LocalUsagiPalette
import com.usaginoyami.theme.UsagiColors

/** Botãozinho que pode ficar "ligado" (fundo na cor de destaque). */
@Composable
fun SelChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val palette = LocalUsagiPalette.current
    val shape = RoundedCornerShape(50)
    Text(
        text = label,
        color = UsagiColors.Silver,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .background(if (selected) palette.accent else Color.Transparent)
            .border(1.dp, if (selected) palette.glow else palette.accent.copy(alpha = 0.7f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** Linha de botões que rola para o lado quando não cabe. */
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, shown: String = value.toInt().toString()) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = UsagiColors.Silver, fontSize = 12.sp, modifier = Modifier.width(84.dp))
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
        Text(shown, color = UsagiColors.Silver.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.width(38.dp))
    }
}

val Swatches: List<Int> = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFE50914, 0xFFFF2A2A, 0xFFFF6D00, 0xFFFFD400,
    0xFF00E676, 0xFF00E5FF, 0xFF2979FF, 0xFF8E24FF, 0xFFFF4081, 0xFFD8D8DC,
).map { it.toInt() }

@Composable
fun ColorRow(label: String, current: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = UsagiColors.Silver, fontSize = 12.sp, modifier = Modifier.width(84.dp))
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in Swatches) {
                val on = c == current
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (on) 3.dp else 1.dp, if (on) UsagiColors.Silver else Color(0x55FFFFFF), CircleShape)
                        .clickable { onPick(c) },
                )
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = UsagiColors.Silver.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun TopBtn(label: String, onClick: () -> Unit, enabled: Boolean = true, filled: Boolean = false) {
    SelChip(label, filled, onClick, enabled = enabled)
}

@Composable
fun Hint(text: String) {
    Text(text, color = UsagiColors.Silver.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
}

/** Faz a área engolir os toques (para que nada por baixo de uma tela por cima reaja). */
fun Modifier.blockTouches(): Modifier = composed {
    clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
}
