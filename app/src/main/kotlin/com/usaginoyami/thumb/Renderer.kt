package com.usaginoyami.thumb

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Desenha o projeto num [Canvas] com coordenadas de 1280x720.
 * A prévia da tela e a exportação usam exatamente este código, então o arquivo sai igual ao que você vê.
 */
object Renderer {

    private fun typeface(f: Font): Typeface {
        val style = when {
            f.bold && f.italic -> Typeface.BOLD_ITALIC
            f.bold -> Typeface.BOLD
            f.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(f.family, style)
    }

    private fun basePaint(t: TextLayer) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = typeface(t.font)
        textSize = t.sizePx
        textAlign = Paint.Align.CENTER
        strokeJoin = Paint.Join.ROUND
        strokeMiter = 2f
    }

    private fun lines(t: TextLayer): List<String> = t.text.split('\n').ifEmpty { listOf("") }

    /** Tamanho (sem escala) da caixa de seleção da camada. */
    fun sizeOf(l: Layer): Pair<Float, Float> = when (l) {
        is ImageLayer -> l.srcW.toFloat() to l.srcH.toFloat()
        is TextLayer -> {
            val p = basePaint(l)
            val fm = p.fontMetrics
            val lh = (fm.descent - fm.ascent) * LINE_SPACING
            val w = lines(l).maxOf { p.measureText(it) } + l.outlineWidth * 2
            val h = lh * lines(l).size + l.outlineWidth * 2
            max(w, 20f) to max(h, 20f)
        }
    }

    private const val LINE_SPACING = 0.95f

    fun render(canvas: Canvas, project: Project, image: (String) -> Bitmap?) {
        drawBackground(canvas, project.background)
        for (l in project.layers) drawLayer(canvas, l, image)
    }

    private fun drawBackground(c: Canvas, bg: Background) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = CANVAS_W.toFloat()
        val h = CANVAS_H.toFloat()
        val colors = bg.colors.ifEmpty { listOf(0xFF000000.toInt()) }
        when {
            bg.kind == BgKind.SOLID || colors.size == 1 -> p.color = colors[0]
            bg.kind == BgKind.GRADIENT -> {
                val a = Math.toRadians(bg.angle.toDouble())
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                val half = abs(w / 2 * dx) + abs(h / 2 * dy)
                p.shader = LinearGradient(
                    w / 2 - dx * half, h / 2 - dy * half, w / 2 + dx * half, h / 2 + dy * half,
                    colors.toIntArray(), null, Shader.TileMode.CLAMP,
                )
            }
            else -> p.shader = RadialGradient(w / 2, h / 2, w * 0.7f, colors.toIntArray(), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, p)
    }

    private fun drawLayer(c: Canvas, l: Layer, image: (String) -> Bitmap?) {
        c.save()
        c.translate(l.cx, l.cy)
        c.rotate(l.rotation)
        c.scale(l.scale, l.scale)
        when (l) {
            is ImageLayer -> {
                val bmp = image(l.src)
                if (bmp != null) {
                    val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                    p.alpha = (l.opacity.coerceIn(0f, 1f) * 255).toInt()
                    if (l.blend == Blend.SCREEN) p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
                    if (l.flipH) c.scale(-1f, 1f)
                    val dst = RectF(-l.srcW / 2f, -l.srcH / 2f, l.srcW / 2f, l.srcH / 2f)
                    c.drawBitmap(bmp, null, dst, p)
                }
            }
            is TextLayer -> drawText(c, l)
        }
        c.restore()
    }

    private fun drawText(c: Canvas, t: TextLayer) {
        val ls = lines(t)
        val p = basePaint(t)
        val fm = p.fontMetrics
        val lh = (fm.descent - fm.ascent) * LINE_SPACING
        val top = -lh * ls.size / 2f
        fun baseline(i: Int) = top + lh * i - fm.ascent - (fm.descent - fm.ascent) * (1 - LINE_SPACING) / 2f

        // 1) contorno + brilho (o brilho nasce da silhueta já com contorno)
        val under = Paint(p)
        if (t.outlineWidth > 0f) {
            under.style = Paint.Style.FILL_AND_STROKE
            under.strokeWidth = t.outlineWidth * 2
            under.color = t.outline
        } else {
            under.style = Paint.Style.FILL
            under.color = t.fill
        }
        if (t.glowRadius > 0f) {
            under.setShadowLayer(t.glowRadius, 0f, 0f, t.glow)
            for (i in ls.indices) c.drawText(ls[i], 0f, baseline(i), under)
            // segunda passada deixa o brilho mais forte, no estilo neon
            for (i in ls.indices) c.drawText(ls[i], 0f, baseline(i), under)
            under.clearShadowLayer()
        } else {
            for (i in ls.indices) c.drawText(ls[i], 0f, baseline(i), under)
        }
        // 2) preenchimento
        val fill = Paint(p)
        fill.style = Paint.Style.FILL
        fill.color = t.fill
        for (i in ls.indices) c.drawText(ls[i], 0f, baseline(i), fill)
    }

    /** Moldura da camada selecionada + guias de centro. [px] = tamanho de 1 pixel de tela em unidades do canvas. */
    fun drawOverlay(c: Canvas, l: Layer?, accent: Int, px: Float, guideX: Boolean, guideY: Boolean) {
        val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            strokeWidth = 1.5f * px
            pathEffect = DashPathEffect(floatArrayOf(8f * px, 6f * px), 0f)
        }
        if (guideX) c.drawLine(CANVAS_W / 2f, 0f, CANVAS_W / 2f, CANVAS_H.toFloat(), guide)
        if (guideY) c.drawLine(0f, CANVAS_H / 2f, CANVAS_W.toFloat(), CANVAS_H / 2f, guide)
        if (l == null) return
        val (w, h) = sizeOf(l)
        c.save()
        c.translate(l.cx, l.cy)
        c.rotate(l.rotation)
        c.scale(l.scale, l.scale)
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = accent
            strokeWidth = 2f * px / l.scale
        }
        c.drawRect(-w / 2, -h / 2, w / 2, h / 2, frame)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val r = 5f * px / l.scale
        for (sx in listOf(-1f, 1f)) for (sy in listOf(-1f, 1f)) c.drawCircle(sx * w / 2, sy * h / 2, r, dot)
        c.restore()
    }

    /** Imagem final 1280x720. */
    fun exportBitmap(project: Project, image: (String) -> Bitmap?): Bitmap {
        val bmp = Bitmap.createBitmap(CANVAS_W, CANVAS_H, Bitmap.Config.ARGB_8888)
        render(Canvas(bmp), project, image)
        return bmp
    }
}
