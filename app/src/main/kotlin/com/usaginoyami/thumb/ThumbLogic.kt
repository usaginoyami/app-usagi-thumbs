package com.usaginoyami.thumb

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lógica pura do Editor Thumb (Kotlin sem Android, testável na JVM):
 * camadas, templates, gestos, recorte de fundo por cor e limite de 2 MB do YouTube.
 */

const val CANVAS_W = 1280
const val CANVAS_H = 720
const val YT_MAX_BYTES = 2 * 1024 * 1024

// ---------- fundo ----------

enum class BgKind { SOLID, GRADIENT, RADIAL }

/** Cores em ARGB (Int). GRADIENT usa [angle] em graus (0 = esquerda→direita, 90 = cima→baixo). */
data class Background(val kind: BgKind, val colors: List<Int>, val angle: Float = 90f) {
    companion object {
        val Black = Background(BgKind.SOLID, listOf(0xFF0B0B0D.toInt()))
    }
}

// ---------- camadas ----------

enum class Blend { NORMAL, SCREEN }

enum class Font(val label: String, val family: String, val bold: Boolean, val italic: Boolean) {
    IMPACT("Impacto", "sans-serif-condensed", true, false),
    BLACK("Pesada", "sans-serif-black", false, false),
    NORMAL("Normal", "sans-serif", true, false),
    ITALIC("Itálico", "sans-serif", true, true),
    SERIF("Serifa", "serif", true, false),
    MONO("Mono", "monospace", true, false),
}

sealed interface Layer {
    val id: Int
    val cx: Float          // centro, em pixels do canvas 1280x720
    val cy: Float
    val rotation: Float    // graus, sentido horário
    val scale: Float
    fun placed(cx: Float, cy: Float, rotation: Float, scale: Float): Layer
}

data class TextLayer(
    override val id: Int,
    val text: String,
    val font: Font = Font.IMPACT,
    val sizePx: Float = 150f,
    val fill: Int = 0xFFFFFFFF.toInt(),
    val outline: Int = 0xFF000000.toInt(),
    val outlineWidth: Float = 12f,
    val glow: Int = 0xFFFF2A2A.toInt(),
    val glowRadius: Float = 30f,       // 0 = sem brilho
    override val cx: Float = CANVAS_W / 2f,
    override val cy: Float = CANVAS_H / 2f,
    override val rotation: Float = 0f,
    override val scale: Float = 1f,
) : Layer {
    override fun placed(cx: Float, cy: Float, rotation: Float, scale: Float) = copy(cx = cx, cy = cy, rotation = rotation, scale = scale)
}

/** [src] é "mascote" ou "img:N" (a imagem em si fica fora do modelo, no gerenciador de bitmaps). */
data class ImageLayer(
    override val id: Int,
    val src: String,
    val srcW: Int,
    val srcH: Int,
    val opacity: Float = 1f,
    val flipH: Boolean = false,
    val blend: Blend = Blend.NORMAL,
    val stickerWidth: Float = 0f,       // contorno estilo sticker em volta do recorte (px da imagem); 0 = sem contorno
    val stickerColor: Int = 0xFFFFFFFF.toInt(),
    override val cx: Float = CANVAS_W / 2f,
    override val cy: Float = CANVAS_H / 2f,
    override val rotation: Float = 0f,
    override val scale: Float = 1f,
) : Layer {
    override fun placed(cx: Float, cy: Float, rotation: Float, scale: Float) = copy(cx = cx, cy = cy, rotation = rotation, scale = scale)
}

enum class ShapeKind(val label: String) {
    ARROW("Seta"),
    ELLIPSE("Círculo"),
    RECT("Retângulo"),
    STAR("Estrela"),
}

/** Forma desenhada por código (seta, círculo, retângulo, estrela): serve para apontar e destacar coisas. */
data class ShapeLayer(
    override val id: Int,
    val kind: ShapeKind,
    val w: Float = 300f,
    val h: Float = 300f,
    val color: Int = 0xFFFF2A2A.toInt(),
    val filled: Boolean = false,
    val strokeWidth: Float = 18f,
    val glowRadius: Float = 20f,        // 0 = sem brilho; o brilho usa a própria cor da forma
    override val cx: Float = CANVAS_W / 2f,
    override val cy: Float = CANVAS_H / 2f,
    override val rotation: Float = 0f,
    override val scale: Float = 1f,
) : Layer {
    override fun placed(cx: Float, cy: Float, rotation: Float, scale: Float) = copy(cx = cx, cy = cy, rotation = rotation, scale = scale)

    companion object {
        /** Forma nova com proporção boa para cada tipo (a seta é comprida, o resto é quadrado). */
        fun create(id: Int, kind: ShapeKind): ShapeLayer = when (kind) {
            ShapeKind.ARROW -> ShapeLayer(id, kind, w = 420f, h = 220f, filled = true, glowRadius = 24f)
            ShapeKind.ELLIPSE -> ShapeLayer(id, kind, w = 360f, h = 360f)
            ShapeKind.RECT -> ShapeLayer(id, kind, w = 420f, h = 260f)
            ShapeKind.STAR -> ShapeLayer(id, kind, w = 300f, h = 300f, filled = true, color = 0xFFFFD400.toInt())
        }

        /** Pontos (x,y, origem no centro) do contorno da seta apontando para a direita. */
        fun arrowPoints(w: Float, h: Float): FloatArray {
            val shaft = h * 0.18f      // meia espessura da haste
            val headX = w * 0.1f       // onde começa a ponta
            return floatArrayOf(
                -w / 2, -shaft, headX, -shaft, headX, -h / 2, w / 2, 0f,
                headX, h / 2, headX, shaft, -w / 2, shaft,
            )
        }

        /** Pontos da estrela de 5 pontas dentro de uma caixa [w]x[h] (10 vértices, ponta para cima). */
        fun starPoints(w: Float, h: Float): FloatArray {
            val out = FloatArray(20)
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 1f else 0.45f
                val a = Math.toRadians(-90.0 + i * 36.0)
                out[i * 2] = (cos(a) * r * w / 2).toFloat()
                out[i * 2 + 1] = (sin(a) * r * h / 2).toFloat()
            }
            return out
        }
    }
}

/** Emojis de reação prontos para colar na thumb (viram uma camada de texto sem contorno). */
val StickerEmojis: List<String> = listOf(
    "😱", "😂", "🔥", "💀", "😡", "👀", "🤯", "😭", "👑", "⭐", "❓", "❗", "💥", "👍", "🏆", "✅", "❌", "💯",
)

fun emojiLayer(id: Int, emoji: String): TextLayer = TextLayer(id = id, text = emoji, sizePx = 220f, outlineWidth = 0f, glowRadius = 0f)

const val MASCOT_SRC = "mascote"
const val MASCOT_W = 808   // tamanho de mascote_recorte.png
const val MASCOT_H = 900

data class Project(val background: Background = Background.Black, val layers: List<Layer> = emptyList(), val nextId: Int = 1) {

    fun newId() = nextId

    fun add(make: (Int) -> Layer): Project = copy(layers = layers + make(nextId), nextId = nextId + 1)

    fun get(id: Int?): Layer? = layers.firstOrNull { it.id == id }

    fun replace(layer: Layer): Project = copy(layers = layers.map { if (it.id == layer.id) layer else it })

    fun remove(id: Int): Project = copy(layers = layers.filter { it.id != id })

    fun duplicate(id: Int): Project {
        val l = get(id) ?: return this
        val moved = l.placed(l.cx + 30f, l.cy + 30f, l.rotation, l.scale)
        val dup = when (moved) {
            is TextLayer -> moved.copy(id = nextId)
            is ImageLayer -> moved.copy(id = nextId)
            is ShapeLayer -> moved.copy(id = nextId)
        }
        return copy(layers = layers + dup, nextId = nextId + 1)
    }

    /** [delta] = +1 sobe uma posição (para frente), -1 desce; "toFront/toBack" usam as pontas. */
    fun reorder(id: Int, delta: Int): Project {
        val i = layers.indexOfFirst { it.id == id }
        if (i < 0) return this
        val j = (i + delta).coerceIn(0, layers.size - 1)
        if (i == j) return this
        val list = layers.toMutableList()
        val l = list.removeAt(i)
        list.add(j, l)
        return copy(layers = list)
    }

    fun toFront(id: Int) = reorder(id, layers.size)
    fun toBack(id: Int) = reorder(id, -layers.size)
}

/** Nome automático do projeto: primeira linha do primeiro texto que tenha letra ou número (emoji sozinho não conta). */
fun projectTitle(p: Project): String {
    val t = p.layers.filterIsInstance<TextLayer>()
        .map { l -> l.text.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }
        .firstOrNull { s -> s.any { it.isLetterOrDigit() } }
    return (t ?: "Thumb sem título").take(28)
}

// ---------- geometria ----------

/** Ponto do canvas (px,py) → coordenadas locais da camada (origem no centro, sem escala/rotação). */
fun toLocal(layer: Layer, px: Float, py: Float): Pair<Float, Float> {
    val dx = px - layer.cx
    val dy = py - layer.cy
    val r = Math.toRadians(-layer.rotation.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    val lx = (dx * c - dy * s) / layer.scale
    val ly = (dx * s + dy * c) / layer.scale
    return lx to ly
}

/** Inverso de [toLocal]: ponto local (sem escala/rotação, origem no centro) → ponto no canvas. */
fun toCanvas(layer: Layer, lx: Float, ly: Float): Pair<Float, Float> {
    val r = Math.toRadians(layer.rotation.toDouble())
    val c = cos(r).toFloat()
    val s = sin(r).toFloat()
    val x = lx * layer.scale
    val y = ly * layer.scale
    return (layer.cx + x * c - y * s) to (layer.cy + x * s + y * c)
}

/** A camada de cima que contém o ponto. [sizeOf] devolve largura x altura sem escala. */
fun hitTest(layers: List<Layer>, px: Float, py: Float, slop: Float = 0f, sizeOf: (Layer) -> Pair<Float, Float>): Layer? {
    for (l in layers.asReversed()) {
        val (w, h) = sizeOf(l)
        val (lx, ly) = toLocal(l, px, py)
        val pad = slop / l.scale
        if (abs(lx) <= w / 2 + pad && abs(ly) <= h / 2 + pad) return l
    }
    return null
}

/** Move/gira/escala a camada com um gesto de dois dedos (valores já em pixels do canvas). */
fun applyGesture(layer: Layer, panX: Float, panY: Float, zoom: Float, rotateDeg: Float): Layer =
    layer.placed(
        cx = layer.cx + panX,
        cy = layer.cy + panY,
        rotation = normalizeAngle(layer.rotation + rotateDeg),
        scale = (layer.scale * zoom).coerceIn(0.03f, 30f),
    )

fun normalizeAngle(a: Float): Float {
    var r = a % 360f
    if (r > 180f) r -= 360f
    if (r <= -180f) r += 360f
    return r
}

/** Ímã: se [v] está a menos de [threshold] de algum alvo, devolve o alvo. */
fun snap(v: Float, targets: List<Float>, threshold: Float): Float {
    var best = v
    var bestD = threshold
    for (t in targets) {
        val d = abs(v - t)
        if (d <= bestD) {
            best = t
            bestD = d
        }
    }
    return best
}

/** Tamanho inicial (scale) para uma imagem caber em [maxW]x[maxH] sem passar do tamanho original. */
fun fitScale(w: Int, h: Int, maxW: Float, maxH: Float): Float = min(1f, min(maxW / w, maxH / h))

/** Escala para a imagem cobrir o canvas inteiro (pode cortar as bordas). */
fun coverScale(w: Int, h: Int): Float = max(CANVAS_W.toFloat() / w, CANVAS_H.toFloat() / h)

/** Largura x altura da imagem com [scale] já aplicada, para caber no canvas. */
fun initialImageScale(w: Int, h: Int): Float = min(CANVAS_W * 0.6f / w, CANVAS_H * 0.8f / h)

// ---------- templates ----------

class Template(val name: String, val build: () -> Project)

object Templates {
    private const val RED = 0xFFE50914.toInt()
    private const val DARKRED = 0xFF3A0000.toInt()
    private const val BLACK = 0xFF050507.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val NEON = 0xFFFF2A2A.toInt()
    private const val YELLOW = 0xFFFFD400.toInt()

    private fun text(id: Int, s: String, size: Float, cx: Float, cy: Float, fill: Int = WHITE, glow: Int = NEON, rot: Float = 0f) =
        TextLayer(id = id, text = s, sizePx = size, fill = fill, glow = glow, cx = cx, cy = cy, rotation = rot)

    val all: List<Template> = listOf(
        Template("Usagi clássico") {
            Project(Background(BgKind.RADIAL, listOf(DARKRED, BLACK)))
                .add { ImageLayer(it, MASCOT_SRC, MASCOT_W, MASCOT_H, cx = 930f, cy = 360f, scale = 0.7f) }
                .add { text(it, "TÍTULO\nDO VÍDEO", 160f, 380f, 300f) }
                .add { text(it, "SUBTÍTULO", 70f, 340f, 560f, fill = YELLOW, glow = YELLOW) }
        },
        Template("Reação") {
            Project(Background(BgKind.GRADIENT, listOf(0xFF1A0000.toInt(), BLACK), 90f))
                .add { text(it, "NÃO ACREDITO", 150f, 640f, 110f) }
                .add { text(it, "O QUE ACONTECEU?", 120f, 640f, 640f, fill = YELLOW, glow = YELLOW) }
        },
        Template("Versus") {
            Project(Background(BgKind.GRADIENT, listOf(RED, 0xFF0A0A2A.toInt()), 0f))
                .add { text(it, "VS", 330f, 640f, 360f, glow = 0xFFFFFFFF.toInt()) }
                .add { text(it, "LADO A", 100f, 320f, 620f) }
                .add { text(it, "LADO B", 100f, 960f, 620f) }
        },
        Template("Tutorial") {
            Project(Background(BgKind.GRADIENT, listOf(0xFF0B0B0D.toInt(), 0xFF2A0008.toInt()), 45f))
                .add { text(it, "COMO FAZER", 150f, 640f, 230f) }
                .add { text(it, "PASSO A PASSO", 90f, 640f, 440f, fill = YELLOW, glow = YELLOW) }
                .add { text(it, "#1", 220f, 1100f, 600f, fill = NEON, glow = NEON, rot = -8f) }
        },
        Template("Texto gigante") {
            Project(Background(BgKind.SOLID, listOf(RED)))
                .add { text(it, "PALAVRA", 300f, 640f, 360f, glow = 0xFF000000.toInt()) }
        },
        Template("Em branco") { Project() },
    )
}

// ---------- recorte de fundo (pixels ARGB) ----------

object Cutout {

    private fun dist(a: Int, b: Int): Float {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toFloat())
    }

    /**
     * Varinha mágica: apaga (alpha 0) os pixels parecidos com o pixel tocado, ligados a ele.
     * [tolerance] 0..255 é a distância de cor. Se [contiguous] for falso, apaga a cor em toda a imagem.
     * Devolve quantos pixels foram apagados.
     */
    fun magicErase(px: IntArray, w: Int, h: Int, sx: Int, sy: Int, tolerance: Int, contiguous: Boolean = true): Int {
        if (sx !in 0 until w || sy !in 0 until h) return 0
        val seed = px[sy * w + sx]
        if ((seed ushr 24) == 0) return 0
        val tol = tolerance.toFloat()
        var count = 0
        if (!contiguous) {
            for (i in px.indices) {
                if ((px[i] ushr 24) != 0 && dist(px[i], seed) <= tol) {
                    px[i] = 0
                    count++
                }
            }
            return count
        }
        val stack = IntArray(w * h)
        var sp = 0
        val seen = BooleanArray(w * h)
        stack[sp++] = sy * w + sx
        seen[sy * w + sx] = true
        while (sp > 0) {
            val i = stack[--sp]
            if ((px[i] ushr 24) == 0 || dist(px[i], seed) > tol) continue
            px[i] = 0
            count++
            val x = i % w
            val y = i / w
            if (x > 0 && !seen[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
            if (x < w - 1 && !seen[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
            if (y > 0 && !seen[i - w]) { seen[i - w] = true; stack[sp++] = i - w }
            if (y < h - 1 && !seen[i + w]) { seen[i + w] = true; stack[sp++] = i + w }
        }
        return count
    }

    /** Apaga em círculo (borracha). Com [original], restaura em vez de apagar (borracha ao contrário). */
    fun brush(px: IntArray, w: Int, h: Int, cx: Float, cy: Float, radius: Float, original: IntArray? = null) {
        val x0 = max(0, (cx - radius).toInt())
        val x1 = min(w - 1, (cx + radius).toInt() + 1)
        val y0 = max(0, (cy - radius).toInt())
        val y1 = min(h - 1, (cy + radius).toInt() + 1)
        val r2 = radius * radius
        for (y in y0..y1) for (x in x0..x1) {
            val dx = x - cx
            val dy = y - cy
            if (dx * dx + dy * dy <= r2) {
                val i = y * w + x
                px[i] = if (original == null) 0 else original[i]
            }
        }
    }

    /** Suaviza a borda: pixels visíveis encostados em transparente perdem um pouco de alpha (evita serrilhado). */
    fun softenEdges(px: IntArray, w: Int, h: Int) {
        val alphaOut = IntArray(px.size) { px[it] ushr 24 }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val a = px[i] ushr 24
            if (a == 0) continue
            var sum = 0
            var n = 0
            var touchesEmpty = false
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                val na = if (nx in 0 until w && ny in 0 until h) px[ny * w + nx] ushr 24 else a
                sum += na
                n++
                if (na == 0) touchesEmpty = true
            }
            if (touchesEmpty) alphaOut[i] = sum / n
        }
        for (i in px.indices) {
            val a = alphaOut[i]
            if (a != (px[i] ushr 24)) px[i] = (a shl 24) or (px[i] and 0xFFFFFF)
        }
    }

    /** Retângulo (esq, topo, dir, base) do que não é transparente; null se tudo sumiu. Dir/base exclusivos. */
    fun contentBounds(px: IntArray, w: Int, h: Int): IntArray? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > 8) {
                if (x < l) l = x
                if (x > r) r = x
                if (y < t) t = y
                if (y > b) b = y
            }
        }
        return if (r < 0) null else intArrayOf(l, t, r + 1, b + 1)
    }
}

// ---------- exportação ----------

object Export {
    /**
     * Maior qualidade JPEG (de [minQ] a [maxQ]) cujo arquivo cabe em [maxBytes].
     * [sizeAt] comprime com a qualidade dada e devolve o tamanho. Se nem a [minQ] couber, devolve [minQ].
     */
    fun bestQuality(maxBytes: Int, minQ: Int = 40, maxQ: Int = 95, sizeAt: (Int) -> Int): Int {
        if (sizeAt(maxQ) <= maxBytes) return maxQ
        var lo = minQ
        var hi = maxQ
        var best = minQ
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (sizeAt(mid) <= maxBytes) { best = mid; lo = mid + 1 } else hi = mid - 1
        }
        return best
    }

    fun fileName(now: Long, png: Boolean): String {
        val sec = now / 1000
        val days = sec / 86400
        val rem = sec % 86400
        // data civil (algoritmo de Howard Hinnant)
        val z = days + 719468
        val era = z / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        fun p(n: Long) = n.toString().padStart(2, '0')
        return "thumb_${y}${p(m)}${p(d)}_${p(rem / 3600)}${p((rem % 3600) / 60)}${p(rem % 60)}" + if (png) ".png" else ".jpg"
    }

    fun humanSize(bytes: Int): String = if (bytes >= 1024 * 1024) "%.2f MB".format(java.util.Locale.US, bytes / 1048576.0) else "${bytes / 1024} KB"
}
