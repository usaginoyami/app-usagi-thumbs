package com.usaginoyami.thumb

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usaginoyami.theme.LocalUsagiPalette
import com.usaginoyami.theme.UsagiColors
import kotlin.math.max
import kotlin.math.min

private enum class CutTool(val label: String) { WAND("Varinha"), ERASE("Borracha"), RESTORE("Restaurar") }

/** Onde a imagem fica na área de edição: escala, canto esquerdo e topo, em pixels da tela. */
private class Fit(val scale: Float, val left: Float, val top: Float)

private fun fit(cw: Float, ch: Float, w: Int, h: Int, zoom: Float, off: Offset): Fit {
    val s = min(cw / w, ch / h) * zoom
    return Fit(s, (cw - w * s) / 2f + off.x, (ch - h * s) / 2f + off.y)
}

private fun checkerShader(): BitmapShader {
    val t = 12
    val bmp = Bitmap.createBitmap(t * 2, t * 2, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(0xFF4A4A50.toInt())
    val p = Paint().apply { color = 0xFF65656C.toInt() }
    val c = android.graphics.Canvas(bmp)
    c.drawRect(0f, 0f, t.toFloat(), t.toFloat(), p)
    c.drawRect(t.toFloat(), t.toFloat(), 2f * t, 2f * t, p)
    return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

@Composable
fun CutoutScreen(vm: ThumbViewModel) {
    val s = vm.cutout ?: return
    val accent = LocalUsagiPalette.current.glow
    var tool by remember { mutableStateOf(CutTool.WAND) }
    var tolerance by remember { mutableFloatStateOf(30f) }
    var contiguous by remember { mutableStateOf(true) }
    var brushPx by remember { mutableFloatStateOf(28f) }   // raio do pincel em pixels da TELA
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    val checker = remember { checkerShader() }
    val ver = vm.cutVersion // lido aqui para o botão Desfazer reagir a cada mudança

    BackHandler { vm.cancelCutout() }

    Column(
        Modifier.fillMaxSize().background(UsagiColors.Background).blockTouches()
            .statusBarsPadding().navigationBarsPadding().imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelChip("Cancelar", false, { vm.cancelCutout() })
            Text("Recortar fundo", color = UsagiColors.Silver, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SelChip("Aplicar", true, { vm.applyCutout() })
        }

        Canvas(
            Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val cw = size.width.toFloat()
                    val ch = size.height.toFloat()
                    val toolNow = tool
                    val brush = toolNow != CutTool.WAND
                    var multi = false
                    var painting = false
                    var last = down.position
                    var moved = 0f

                    fun toImage(p: Offset): Offset {
                        val f = fit(cw, ch, s.w, s.h, zoom, offset)
                        return Offset((p.x - f.left) / f.scale, (p.y - f.top) / f.scale)
                    }

                    fun paint(from: Offset, to: Offset) {
                        val f = fit(cw, ch, s.w, s.h, zoom, offset)
                        val r = brushPx / f.scale
                        val dist = (to - from).getDistance()
                        val steps = max(1, (dist / max(1f, brushPx / 3f)).toInt())
                        for (i in 1..steps) {
                            val p = Offset(from.x + (to.x - from.x) * i / steps, from.y + (to.y - from.y) * i / steps)
                            val q = toImage(p)
                            vm.cutBrush(q.x, q.y, r, toolNow == CutTool.RESTORE)
                        }
                        vm.cutCommit()
                    }

                    if (brush) cursor = down.position
                    do {
                        val ev = awaitPointerEvent()
                        val pressed = ev.changes.count { it.pressed }
                        if (pressed >= 2) {
                            multi = true
                            painting = false
                            cursor = null
                            val z = ev.calculateZoom()
                            val pan = ev.calculatePan()
                            val c = ev.calculateCentroid(useCurrent = true)
                            val newZoom = (zoom * z).coerceIn(1f, 12f)
                            val k = newZoom / zoom
                            val center = Offset(cw / 2f, ch / 2f)
                            if (c != Offset.Unspecified) {
                                offset = (c - center) - (c - center - offset) * k + pan
                            }
                            zoom = newZoom
                            ev.changes.forEach { it.consume() }
                        } else if (!multi) {
                            val change = ev.changes.firstOrNull { it.pressed } ?: ev.changes.first()
                            val p = change.position
                            moved += (p - last).getDistance()
                            if (brush) {
                                cursor = p
                                if (!painting && moved > 6f) {
                                    painting = true
                                    vm.cutBrushStart()
                                    paint(down.position, down.position)
                                }
                                if (painting) paint(last, p)
                            }
                            last = p
                            change.consume()
                        }
                    } while (ev.changes.any { it.pressed })
                    cursor = null
                    if (!multi) {
                        if (toolNow == CutTool.WAND && moved < 20f) {
                            val q = toImage(down.position)
                            vm.cutMagic(q.x.toInt(), q.y.toInt(), tolerance.toInt(), contiguous)
                        } else if (brush && !painting) {
                            vm.cutBrushStart()
                            paint(down.position, down.position)
                        }
                    }
                }
            },
        ) {
            @Suppress("UNUSED_VARIABLE") val tick = vm.cutVersion
            val f = fit(size.width, size.height, s.w, s.h, zoom, offset)
            val dst = RectF(f.left, f.top, f.left + s.w * f.scale, f.top + s.h * f.scale)
            drawIntoCanvas { c ->
                val n = c.nativeCanvas
                n.save()
                n.clipRect(0f, 0f, size.width, size.height)
                n.drawRect(dst, Paint().apply { shader = checker })
                n.drawBitmap(s.bitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                n.restore()
            }
            val cur = cursor
            if (cur != null && tool != CutTool.WAND) {
                clipRect { drawCircle(accent, radius = brushPx, center = cur, style = Stroke(width = 2.dp.toPx())) }
            }
        }

        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipRow {
                for (t in CutTool.entries) SelChip(t.label, tool == t, { tool = t })
                SelChip("Fundo automático", false, { vm.cutAuto(tolerance.toInt().coerceAtLeast(12)) })
            }
            if (tool == CutTool.WAND) {
                SliderRow("Tolerância", tolerance, 0f..120f, { tolerance = it })
                ChipRow {
                    SelChip("Só região ligada", contiguous, { contiguous = !contiguous })
                }
            } else {
                SliderRow("Pincel", brushPx, 8f..90f, { brushPx = it })
            }
            ChipRow {
                SelChip("Desfazer", false, { vm.cutUndo() }, enabled = ver >= 0 && s.undo.isNotEmpty())
                SelChip("Suavizar bordas", false, { vm.cutSoften() })
                SelChip("Cortar sobra", false, { vm.cutCrop() })
                SelChip("Zoom 1×", false, { zoom = 1f; offset = Offset.Zero })
            }
            Text(
                when (tool) {
                    CutTool.WAND -> "Toque numa cor para apagá-la (e as parecidas). 2 dedos: zoom e mover."
                    CutTool.ERASE -> "Passe o dedo para apagar. 2 dedos: zoom e mover."
                    CutTool.RESTORE -> "Passe o dedo para trazer de volta o que foi apagado."
                },
                color = UsagiColors.Silver.copy(alpha = 0.55f), fontSize = 12.sp,
            )
        }
    }
}
