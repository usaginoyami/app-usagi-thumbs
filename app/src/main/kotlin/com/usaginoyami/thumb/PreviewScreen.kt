package com.usaginoyami.thumb

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usaginoyami.theme.LocalUsagiPalette
import com.usaginoyami.theme.UsagiColors

/**
 * Mostra a miniatura como ela aparece no YouTube: grande (feed do celular), média (vídeos relacionados)
 * e bem pequena (o pior caso). O horário no canto cobre um pedaço da imagem, como no site de verdade.
 */
@Composable
fun PreviewScreen(vm: ThumbViewModel) {
    var light by remember { mutableStateOf(false) }
    val bmp = vm.previewBitmap?.asImageBitmap()
    val cardBg = if (light) Color.White else Color(0xFF0F0F0F)
    val textMain = if (light) Color(0xFF0F0F0F) else Color.White
    val textSub = if (light) Color(0xFF606060) else Color(0xFFAAAAAA)

    Box(Modifier.fillMaxSize().background(UsagiColors.Background).blockTouches().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Prévia no YouTube", color = UsagiColors.Silver, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TopBtn("Fechar", { vm.closePreview() }, filled = true)
            }
            OutlinedTextField(
                value = vm.previewTitle,
                onValueChange = { vm.previewTitle = it.take(100) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título do vídeo (só para testar)") },
                maxLines = 2,
            )
            ChipRow {
                SelChip("Tema escuro", !light, { light = false })
                SelChip("Tema claro", light, { light = true })
            }

            SectionTitle("FEED DO CELULAR")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cardBg).padding(bottom = 10.dp)) {
                Thumb(bmp, Modifier.fillMaxWidth(), RoundedCornerShape(0.dp), 12.sp)
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(LocalUsagiPalette.current.accent))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(vm.previewTitle, color = textMain, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("UsagiNoYami · 12 mil visualizações · há 2 dias", color = textSub, fontSize = 12.sp)
                    }
                }
            }

            SectionTitle("VÍDEOS RELACIONADOS")
            SideCard(bmp, 168.dp, vm.previewTitle, cardBg, textMain, textSub, 13.sp)

            SectionTitle("BEM PEQUENA (pior caso: lista no celular ou TV)")
            SideCard(bmp, 112.dp, vm.previewTitle, cardBg, textMain, textSub, 11.sp)

            Hint(
                "Se não dá para ler o texto na versão pequena, aumente a fonte, use contorno grosso ou mais contraste. " +
                    "Nada importante deve ficar no canto de baixo à direita: o horário cobre esse pedaço.",
            )
        }
    }
}

@Composable
private fun SideCard(bmp: ImageBitmap?, width: Dp, title: String, bg: Color, main: Color, sub: Color, titleSize: androidx.compose.ui.unit.TextUnit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Thumb(bmp, Modifier.width(width), RoundedCornerShape(8.dp), if (width < 140.dp) 9.sp else 11.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = main, fontSize = titleSize, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("UsagiNoYami", color = sub, fontSize = (titleSize.value - 2f).sp)
            Text("12 mil visualizações", color = sub, fontSize = (titleSize.value - 2f).sp)
        }
    }
}

/** A imagem 16:9 com o selo de duração no canto de baixo à direita. */
@Composable
private fun Thumb(bmp: ImageBitmap?, modifier: Modifier, shape: RoundedCornerShape, badgeSize: androidx.compose.ui.unit.TextUnit) {
    Box(modifier.aspectRatio(16f / 9f).clip(shape).background(Color(0xFF1A1A1A))) {
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = "Miniatura", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        } else {
            Text("Preparando…", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
        }
        Text(
            "12:34",
            color = Color.White,
            fontSize = badgeSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
