package com.usaginoyami.thumb

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import android.graphics.Canvas as NativeCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usaginoyami.theme.LocalUsagiPalette
import com.usaginoyami.theme.UsagiColors
import kotlinx.coroutines.delay
import kotlin.math.abs

/** O que a tela precisa pedir ao Android (abrir seletor, compartilhar). */
interface ThumbActions {
    fun pickImage()
    fun share(uri: android.net.Uri, png: Boolean)
}

private enum class Tab(val label: String) { LAYER("Camada"), ADD("Adicionar"), TEMPLATES("Templates"), BACKGROUND("Fundo"), PROJECTS("Projetos") }

@Composable
fun ThumbApp(vm: ThumbViewModel, actions: ThumbActions, onCyclePalette: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(vm.selectedId) { if (vm.selectedId != null) tab = Tab.LAYER.ordinal }

    Box(Modifier.fillMaxSize().background(UsagiColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            TopBar(vm, onCyclePalette)
            CanvasView(vm)
            ChipRow(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                SelChip("＋ Texto", false, { vm.addText() })
                SelChip("＋ Imagem", false, actions::pickImage)
                SelChip("🐰 Mascote", false, { vm.addMascot() })
                SelChip("▶ Prévia", false, { vm.openPreview() })
            }
            ChipRow(Modifier.padding(horizontal = 12.dp)) {
                for (t in Tab.entries) SelChip(t.label, tab == t.ordinal, { tab = t.ordinal })
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (Tab.entries[tab]) {
                    Tab.LAYER -> LayerPanel(vm)
                    Tab.ADD -> AddPanel(vm)
                    Tab.TEMPLATES -> TemplatesPanel(vm)
                    Tab.BACKGROUND -> BackgroundPanel(vm)
                    Tab.PROJECTS -> ProjectsPanel(vm)
                }
            }
        }

        if (vm.cutout != null) CutoutScreen(vm)
        if (vm.exportOpen) ExportSheet(vm, actions)
        if (vm.previewOpen) PreviewScreen(vm)
        BackHandler(enabled = vm.previewOpen) { vm.closePreview() }
        BackHandler(enabled = vm.exportOpen) { vm.closeExport() }

        val msg = vm.message
        LaunchedEffect(msg) {
            if (msg != null) {
                delay(2600)
                if (vm.message == msg) vm.message = null
            }
        }
        if (msg != null) {
            Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
                Text(
                    msg,
                    color = UsagiColors.Silver,
                    fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(UsagiColors.Surface).padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun TopBar(vm: ThumbViewModel, onCyclePalette: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Usagi Thumb", color = UsagiColors.Silver, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TopBtn("↶", { vm.undo() }, enabled = vm.canUndo)
        TopBtn("↷", { vm.redo() }, enabled = vm.canRedo)
        TopBtn("🎨", onCyclePalette)
        TopBtn("Exportar", { vm.openExport() }, filled = true)
    }
}

// ---------- canvas ----------

@Composable
private fun CanvasView(vm: ThumbViewModel) {
    val palette = LocalUsagiPalette.current
    val accent = palette.glow.toArgb()
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CANVAS_W / CANVAS_H.toFloat())
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val sc = size.width / CANVAS_W.toFloat()
                    val px = down.position.x / sc
                    val py = down.position.y / sc
                    val sizeOfLayer: (Layer) -> Pair<Float, Float> = { Renderer.sizeOf(it) }
                    // Se o toque cai na camada já selecionada, continua com ela; senão pega a de cima.
                    val current = vm.selected
                    val keepCurrent = current != null && hitTest(listOf(current), px, py, 16f / sc, sizeOfLayer) != null
                    val hit = if (keepCurrent) current else hitTest(vm.project.layers, px, py, 16f / sc, sizeOfLayer)
                    if (hit != null) vm.selectedId = hit.id
                    val startedOnLayer = hit != null
                    val targetId = hit?.id ?: vm.selectedId
                    var rawCx = (vm.project.get(targetId))?.cx ?: 0f
                    var rawCy = (vm.project.get(targetId))?.cy ?: 0f
                    var travelled = 0f
                    do {
                        val ev = awaitPointerEvent()
                        val multi = ev.changes.count { it.pressed } >= 2
                        val pan = ev.calculatePan()
                        val zoom = if (multi) ev.calculateZoom() else 1f
                        val rot = if (multi) ev.calculateRotation() else 0f
                        travelled += pan.getDistance()
                        val layer = vm.project.get(targetId)
                        if (layer != null && (startedOnLayer || multi) && (pan != Offset.Zero || zoom != 1f || rot != 0f)) {
                            rawCx += pan.x / sc
                            rawCy += pan.y / sc
                            val thr = 8f / sc
                            val sx = snap(rawCx, listOf(CANVAS_W / 2f), thr)
                            val sy = snap(rawCy, listOf(CANVAS_H / 2f), thr)
                            vm.guideX = sx == CANVAS_W / 2f
                            vm.guideY = sy == CANVAS_H / 2f
                            val g = applyGesture(layer, 0f, 0f, zoom, rot)
                            vm.transform(layer.id, sx, sy, g.rotation, g.scale)
                            ev.changes.forEach { if (it.pressed) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                    vm.guideX = false
                    vm.guideY = false
                    if (!startedOnLayer && travelled < 12f) vm.selectedId = null
                }
            },
    ) {
        @Suppress("UNUSED_VARIABLE") val tick = vm.imageVersion // refaz o desenho quando uma imagem muda
        val sc = size.width / CANVAS_W
        drawIntoCanvas { c ->
            val n: NativeCanvas = c.nativeCanvas
            n.save()
            n.scale(sc, sc)
            n.clipRect(0f, 0f, CANVAS_W.toFloat(), CANVAS_H.toFloat())
            Renderer.render(n, vm.project) { vm.image(it) }
            Renderer.drawOverlay(n, vm.selected, accent, 1f / sc, vm.guideX, vm.guideY)
            n.restore()
        }
    }
}

// ---------- painéis ----------

@Composable
private fun LayerPanel(vm: ThumbViewModel) {
    val sel = vm.selected
    // lista de camadas (a de cima primeiro): deixa escolher uma que está escondida atrás de outra
    if (vm.project.layers.isNotEmpty()) {
        ChipRow {
            for (l in vm.project.layers.asReversed()) {
                SelChip(layerName(l), l.id == vm.selectedId, { vm.selectedId = l.id })
            }
        }
    }
    if (sel == null) {
        Hint("Toque numa camada para editar. Arraste para mover; com 2 dedos dá zoom e gira. Toque no vazio para soltar.")
        return
    }
    ChipRow {
        SelChip("Frente", false, { vm.reorder(+1) })
        SelChip("Trás", false, { vm.reorder(-1) })
        SelChip("Centralizar", false, { vm.centerLayer() })
        SelChip("Duplicar", false, { vm.duplicate() })
        SelChip("Apagar", false, { vm.delete() })
    }
    when (sel) {
        is TextLayer -> TextProps(vm, sel)
        is ImageLayer -> ImageProps(vm, sel)
        is ShapeLayer -> ShapeProps(vm, sel)
    }
}

private fun layerName(l: Layer): String = when (l) {
    is TextLayer -> "T  " + l.text.replace('\n', ' ').take(12)
    is ImageLayer -> if (l.src == MASCOT_SRC) "🐰 Mascote" else "🖼 Imagem"
    is ShapeLayer -> "◆ " + l.kind.label
}

@Composable
private fun TextProps(vm: ThumbViewModel, t: TextLayer) {
    OutlinedTextField(
        value = t.text,
        onValueChange = { v -> vm.editText("text") { it.copy(text = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Texto (Enter = nova linha)") },
        maxLines = 4,
    )
    ChipRow {
        for (f in Font.entries) SelChip(f.label, t.font == f, { vm.editText("font") { it.copy(font = f) } })
    }
    SliderRow("Tamanho", t.sizePx, 30f..400f, { v -> vm.editText("size") { it.copy(sizePx = v) } })
    ColorRow("Cor", t.fill) { c -> vm.editText("fill") { it.copy(fill = c) } }
    SectionTitle("CONTORNO")
    SliderRow("Espessura", t.outlineWidth, 0f..40f, { v -> vm.editText("ow") { it.copy(outlineWidth = v) } })
    ColorRow("Cor", t.outline) { c -> vm.editText("oc") { it.copy(outline = c) } }
    SectionTitle("BRILHO")
    SliderRow("Intensidade", t.glowRadius, 0f..100f, { v -> vm.editText("gr") { it.copy(glowRadius = v) } })
    ColorRow("Cor", t.glow) { c -> vm.editText("gc") { it.copy(glow = c) } }
}

@Composable
private fun ImageProps(vm: ThumbViewModel, im: ImageLayer) {
    ChipRow {
        SelChip("✂ Recortar fundo", false, { vm.startCutout() })
        SelChip("Preencher tela", false, { vm.fillCanvas() })
        SelChip("Espelhar", im.flipH, { vm.editImage("flip") { it.copy(flipH = !it.flipH) } })
        SelChip("Preto some", im.blend == Blend.SCREEN, { vm.editImage("blend") { it.copy(blend = if (it.blend == Blend.SCREEN) Blend.NORMAL else Blend.SCREEN) } })
        SelChip("Sticker", im.stickerWidth > 0f, { vm.editImage("stk") { it.copy(stickerWidth = if (it.stickerWidth > 0f) 0f else 14f) } })
    }
    if (im.stickerWidth > 0f) {
        SectionTitle("CONTORNO STICKER")
        SliderRow("Espessura", im.stickerWidth, 2f..50f, { v -> vm.editImage("stkw") { it.copy(stickerWidth = v) } })
        ColorRow("Cor", im.stickerColor) { c -> vm.editImage("stkc") { it.copy(stickerColor = c) } }
    }
    SliderRow("Opacidade", im.opacity * 100f, 5f..100f, { v -> vm.editImage("op") { it.copy(opacity = v / 100f) } }, shown = "${(im.opacity * 100).toInt()}%")
    SliderRow("Girar", im.rotation, -180f..180f, { v -> vm.mutate("rot") { p -> p.get(im.id)?.let { p.replace(it.placed(it.cx, it.cy, v, it.scale)) } ?: p } }, shown = "${im.rotation.toInt()}°")
    Hint("\"Preto some\" faz o preto da imagem ficar transparente (bom para mascote e logos em fundo preto). \"Sticker\" põe uma borda em volta do recorte: recorte o fundo primeiro (✂) para a borda acompanhar a silhueta.")
}

@Composable
private fun TemplatesPanel(vm: ThumbViewModel) {
    val previews = remember { Templates.all.map { it to templatePreview(it, vm) } }
    Hint("Escolher um template troca o projeto atual (dá para desfazer em ↶).")
    for (row in previews.chunked(2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((t, bmp) in row) {
                Column(Modifier.weight(1f).clickable { vm.applyTemplate(t) }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = t.name,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                            .border(1.dp, LocalUsagiPalette.current.accent.copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
                    )
                    Text(t.name, color = UsagiColors.Silver, fontSize = 12.sp)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

private fun templatePreview(t: Template, vm: ThumbViewModel): Bitmap {
    val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
    val c = NativeCanvas(bmp)
    c.scale(320f / CANVAS_W, 180f / CANVAS_H)
    Renderer.render(c, t.build()) { vm.image(it) }
    return bmp
}

@Composable
private fun BackgroundPanel(vm: ThumbViewModel) {
    val bg = vm.project.background
    val c1 = bg.colors.getOrElse(0) { 0xFF000000.toInt() }
    val c2 = bg.colors.getOrElse(1) { 0xFF000000.toInt() }
    fun set(kind: BgKind = bg.kind, a: Int = c1, b: Int = c2, angle: Float = bg.angle, key: String = "bg") =
        vm.setBackground(Background(kind, if (kind == BgKind.SOLID) listOf(a) else listOf(a, b), angle), key)
    ChipRow {
        SelChip("Sólido", bg.kind == BgKind.SOLID, { set(BgKind.SOLID, key = "bgk") })
        SelChip("Degradê", bg.kind == BgKind.GRADIENT, { set(BgKind.GRADIENT, key = "bgk") })
        SelChip("Radial", bg.kind == BgKind.RADIAL, { set(BgKind.RADIAL, key = "bgk") })
    }
    ColorRow(if (bg.kind == BgKind.RADIAL) "Centro" else "Cor 1", c1) { set(a = it, key = "bg1") }
    if (bg.kind != BgKind.SOLID) ColorRow(if (bg.kind == BgKind.RADIAL) "Borda" else "Cor 2", c2) { set(b = it, key = "bg2") }
    if (bg.kind == BgKind.GRADIENT) SliderRow("Ângulo", bg.angle, 0f..360f, { set(angle = it, key = "bga") }, shown = "${bg.angle.toInt()}°")
    Hint("Para usar uma foto de fundo: \"＋ Imagem\" e depois \"Preencher tela\" (e \"Trás\" se precisar).")
}

// ---------- exportar ----------

@Composable
private fun ExportSheet(vm: ThumbViewModel, actions: ThumbActions) {
    val enc = vm.exportEnc
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).blockTouches().statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(20.dp).clip(RoundedCornerShape(18.dp)).background(UsagiColors.Surface)
                .border(1.dp, LocalUsagiPalette.current.accent.copy(alpha = 0.7f), RoundedCornerShape(18.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Exportar miniatura", color = UsagiColors.Silver, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelChip("JPG (recomendado)", !vm.exportPng, { vm.chooseExportPng(false) })
                SelChip("PNG", vm.exportPng, { vm.chooseExportPng(true) })
            }
            val info = when {
                vm.exportBusy || enc == null -> "Preparando…"
                else -> "1280 × 720 · ${Export.humanSize(enc.bytes.size)}" + if (!enc.png) " · qualidade ${enc.quality}" else ""
            }
            Text(info, color = UsagiColors.Silver, fontSize = 14.sp)
            if (enc != null && enc.overLimit) {
                Text(
                    if (enc.png) "Passou de 2 MB: o YouTube recusa. Use JPG." else "Mesmo na qualidade mínima passou de 2 MB. Simplifique a imagem.",
                    color = LocalUsagiPalette.current.glow, fontSize = 13.sp,
                )
            } else if (enc != null) {
                Text("Dentro do limite de 2 MB do YouTube ✔", color = UsagiColors.Silver.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            ChipRow {
                SelChip("Salvar na galeria", false, { vm.save { } }, enabled = enc != null)
                SelChip("Compartilhar", false, { vm.save { actions.share(it, enc?.png == true) } }, enabled = enc != null)
                SelChip("Fechar", false, { vm.closeExport() })
            }
        }
    }
}
