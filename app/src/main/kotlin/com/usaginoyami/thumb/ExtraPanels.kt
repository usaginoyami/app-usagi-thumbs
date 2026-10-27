package com.usaginoyami.thumb

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usaginoyami.theme.LocalUsagiPalette
import com.usaginoyami.theme.UsagiColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- aba "Adicionar": formas e adesivos ----------

@Composable
internal fun AddPanel(vm: ThumbViewModel) {
    val accent = LocalUsagiPalette.current.accent
    SectionTitle("FORMAS (para apontar e destacar)")
    ChipRow {
        for (k in ShapeKind.entries) SelChip(k.label, false, { vm.addShape(k) })
    }
    SectionTitle("ADESIVOS DE REAÇÃO")
    for (row in StickerEmojis.chunked(6)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in row) {
                val shape = RoundedCornerShape(12.dp)
                Text(
                    e,
                    fontSize = 28.sp,
                    modifier = Modifier
                        .clip(shape)
                        .border(1.dp, accent.copy(alpha = 0.5f), shape)
                        .clickable { vm.addEmoji(e) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
    Hint("A forma ou o adesivo entra no meio da miniatura: arraste, gire e dê zoom como qualquer camada.")
}

// ---------- propriedades de uma forma ----------

@Composable
internal fun ShapeProps(vm: ThumbViewModel, s: ShapeLayer) {
    ChipRow {
        SelChip("Preenchida", s.filled, { vm.editShape("sfill") { it.copy(filled = !it.filled) } })
    }
    ColorRow("Cor", s.color) { c -> vm.editShape("scol") { it.copy(color = c) } }
    SliderRow("Largura", s.w, 40f..900f, { v -> vm.editShape("sw") { it.copy(w = v) } })
    SliderRow("Altura", s.h, 40f..900f, { v -> vm.editShape("sh") { it.copy(h = v) } })
    if (!s.filled) SliderRow("Espessura", s.strokeWidth, 2f..60f, { v -> vm.editShape("sst") { it.copy(strokeWidth = v) } })
    SliderRow("Brilho", s.glowRadius, 0f..60f, { v -> vm.editShape("sgl") { it.copy(glowRadius = v) } })
}

// ---------- aba "Projetos" ----------

@Composable
internal fun ProjectsPanel(vm: ThumbViewModel) {
    LaunchedEffect(Unit) { vm.refreshProjects() }
    val busy = vm.projectBusy
    ChipRow {
        SelChip("💾 Salvar", false, { vm.saveProject() }, enabled = !busy)
        if (vm.currentId != null) SelChip("Salvar como novo", false, { vm.saveProject(asNew = true) }, enabled = !busy)
        SelChip("＋ Projeto novo", false, { vm.newProject() }, enabled = !busy)
    }
    val status = when {
        vm.currentId == null -> "Projeto novo, ainda não salvo."
        vm.hasUnsaved -> "Aberto: ${vm.projectName} (mudanças sem salvar)"
        else -> "Aberto: ${vm.projectName} (salvo)"
    }
    Text(status, color = UsagiColors.Silver, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Hint("Ao abrir ou criar outro projeto, o atual é salvo antes. Ao sair do app, um projeto já salvo é atualizado sozinho. Fica tudo só no seu celular.")
    if (vm.projects.isEmpty()) {
        Hint("Nenhum projeto salvo ainda. Toque em \"Salvar\" para guardar esta miniatura e continuar depois.")
        return
    }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")) }
    for (m in vm.projects) {
        ProjectRow(vm, m, fmt.format(Date(m.updated)), busy)
    }
}

@Composable
private fun ProjectRow(vm: ThumbViewModel, m: ProjectStore.Meta, date: String, busy: Boolean) {
    val accent = LocalUsagiPalette.current.accent
    val shape = RoundedCornerShape(10.dp)
    var confirmDelete by remember(m.id) { mutableStateOf(false) }
    val thumb = remember(m.id, m.updated) {
        runCatching { BitmapFactory.decodeFile(vm.thumbFile(m.id).path)?.asImageBitmap() }.getOrNull()
    }
    val isOpen = m.id == vm.currentId
    Row(
        Modifier.fillMaxWidth().clip(shape).border(if (isOpen) 2.dp else 1.dp, accent.copy(alpha = if (isOpen) 1f else 0.5f), shape).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF111114))) {
            if (thumb != null) Image(bitmap = thumb, contentDescription = m.name, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.name, color = UsagiColors.Silver, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(date + if (isOpen) " · aberto" else "", color = UsagiColors.Silver.copy(alpha = 0.6f), fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SelChip("Abrir", false, { vm.openProject(m.id) }, enabled = !busy && !isOpen)
                if (confirmDelete) {
                    SelChip("Apagar mesmo?", true, { confirmDelete = false; vm.deleteProject(m.id) })
                } else {
                    SelChip("Apagar", false, { confirmDelete = true })
                }
            }
        }
    }
}
