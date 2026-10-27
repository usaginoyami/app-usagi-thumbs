package com.usaginoyami.thumb

/**
 * Salvar/abrir projeto como texto JSON (Kotlin puro, sem Android, testável na JVM).
 * As imagens em si ficam em arquivos PNG ao lado do JSON; aqui só vão as referências ("img:N").
 */
object ProjectJson {

    const val VERSION = 1

    class Loaded(val name: String, val project: Project)

    // ---------- projeto <-> JSON ----------

    fun encode(project: Project, name: String): String {
        val layers = project.layers.map { layerToMap(it) }
        val bg = project.background
        val root = linkedMapOf<String, Any?>(
            "v" to VERSION,
            "name" to name,
            "nextId" to project.nextId,
            "bg" to linkedMapOf<String, Any?>("kind" to bg.kind.name, "colors" to bg.colors, "angle" to bg.angle),
            "layers" to layers,
        )
        return Mini.write(root)
    }

    /** Devolve null se o texto não for um projeto válido (arquivo corrompido). Camadas desconhecidas são ignoradas. */
    fun decode(text: String): Loaded? = try {
        val root = Mini.parse(text) as? Map<*, *> ?: return null
        val bgMap = root["bg"] as? Map<*, *>
        val bg = if (bgMap == null) Background.Black else Background(
            kind = enumOr(bgMap["kind"], BgKind.SOLID),
            colors = (bgMap["colors"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.ifEmpty { null } ?: Background.Black.colors,
            angle = (bgMap["angle"] as? Number)?.toFloat() ?: 90f,
        )
        val layers = (root["layers"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.let(::mapToLayer) }
        val maxId = layers.maxOfOrNull { it.id } ?: 0
        val nextId = maxOf((root["nextId"] as? Number)?.toInt() ?: 1, maxId + 1)
        Loaded(root["name"] as? String ?: "Thumb", Project(bg, layers, nextId))
    } catch (e: Exception) {
        null
    }

    /** Chaves "img:N" usadas pelas camadas (o mascote não entra: ele vem do app). */
    fun imageKeys(project: Project): Set<String> =
        project.layers.filterIsInstance<ImageLayer>().map { it.src }.filter { it != MASCOT_SRC }.toSet()

    /** Maior N entre as chaves "img:N", para o contador de imagens continuar depois dele. */
    fun maxImageNumber(keys: Collection<String>): Int =
        keys.mapNotNull { it.removePrefix("img:").toIntOrNull() }.maxOrNull() ?: 0

    private inline fun <reified E : Enum<E>> enumOr(v: Any?, default: E): E =
        enumValues<E>().firstOrNull { it.name == v } ?: default

    private fun layerToMap(l: Layer): Map<String, Any?> {
        val m = linkedMapOf<String, Any?>("id" to l.id)
        when (l) {
            is TextLayer -> {
                m["type"] = "text"
                m["text"] = l.text
                m["font"] = l.font.name
                m["sizePx"] = l.sizePx
                m["fill"] = l.fill
                m["outline"] = l.outline
                m["outlineWidth"] = l.outlineWidth
                m["glow"] = l.glow
                m["glowRadius"] = l.glowRadius
            }
            is ImageLayer -> {
                m["type"] = "image"
                m["src"] = l.src
                m["srcW"] = l.srcW
                m["srcH"] = l.srcH
                m["opacity"] = l.opacity
                m["flipH"] = l.flipH
                m["blend"] = l.blend.name
                m["stickerWidth"] = l.stickerWidth
                m["stickerColor"] = l.stickerColor
            }
            is ShapeLayer -> {
                m["type"] = "shape"
                m["kind"] = l.kind.name
                m["w"] = l.w
                m["h"] = l.h
                m["color"] = l.color
                m["filled"] = l.filled
                m["strokeWidth"] = l.strokeWidth
                m["glowRadius"] = l.glowRadius
            }
        }
        m["cx"] = l.cx
        m["cy"] = l.cy
        m["rotation"] = l.rotation
        m["scale"] = l.scale
        return m
    }

    private fun mapToLayer(m: Map<*, *>): Layer? {
        fun f(k: String, d: Float) = (m[k] as? Number)?.toFloat() ?: d
        fun i(k: String, d: Int) = (m[k] as? Number)?.toInt() ?: d
        val id = (m["id"] as? Number)?.toInt() ?: return null
        val cx = f("cx", CANVAS_W / 2f)
        val cy = f("cy", CANVAS_H / 2f)
        val rot = f("rotation", 0f)
        val sc = f("scale", 1f).coerceIn(0.03f, 30f)
        return when (m["type"]) {
            "text" -> TextLayer(
                id = id,
                text = m["text"] as? String ?: "",
                font = enumOr(m["font"], Font.IMPACT),
                sizePx = f("sizePx", 150f),
                fill = i("fill", 0xFFFFFFFF.toInt()),
                outline = i("outline", 0xFF000000.toInt()),
                outlineWidth = f("outlineWidth", 12f),
                glow = i("glow", 0xFFFF2A2A.toInt()),
                glowRadius = f("glowRadius", 30f),
                cx = cx, cy = cy, rotation = rot, scale = sc,
            )
            "image" -> {
                val src = m["src"] as? String ?: return null
                val w = i("srcW", 0)
                val h = i("srcH", 0)
                if (w <= 0 || h <= 0) return null
                ImageLayer(
                    id = id, src = src, srcW = w, srcH = h,
                    opacity = f("opacity", 1f),
                    flipH = m["flipH"] as? Boolean ?: false,
                    blend = enumOr(m["blend"], Blend.NORMAL),
                    stickerWidth = f("stickerWidth", 0f),
                    stickerColor = i("stickerColor", 0xFFFFFFFF.toInt()),
                    cx = cx, cy = cy, rotation = rot, scale = sc,
                )
            }
            "shape" -> ShapeLayer(
                id = id,
                kind = enumOr(m["kind"], ShapeKind.ARROW),
                w = f("w", 300f), h = f("h", 300f),
                color = i("color", 0xFFFF2A2A.toInt()),
                filled = m["filled"] as? Boolean ?: false,
                strokeWidth = f("strokeWidth", 18f),
                glowRadius = f("glowRadius", 20f),
                cx = cx, cy = cy, rotation = rot, scale = sc,
            )
            else -> null
        }
    }

    // ---------- JSON mínimo (sem biblioteca, para rodar igual no Android e nos testes da JVM) ----------

    object Mini {
        fun write(v: Any?): String = StringBuilder().also { put(it, v) }.toString()

        private fun put(sb: StringBuilder, v: Any?) {
            when (v) {
                null -> sb.append("null")
                is Boolean -> sb.append(v.toString())
                is Int, is Long -> sb.append(v.toString())
                is Float -> sb.append(if (v.isFinite()) v.toString() else "0")
                is Double -> sb.append(if (v.isFinite()) v.toString() else "0")
                is String -> quote(sb, v)
                is Map<*, *> -> {
                    sb.append('{')
                    var first = true
                    for ((k, x) in v) {
                        if (!first) sb.append(',')
                        first = false
                        quote(sb, k.toString())
                        sb.append(':')
                        put(sb, x)
                    }
                    sb.append('}')
                }
                is Iterable<*> -> {
                    sb.append('[')
                    var first = true
                    for (x in v) {
                        if (!first) sb.append(',')
                        first = false
                        put(sb, x)
                    }
                    sb.append(']')
                }
                else -> quote(sb, v.toString())
            }
        }

        private fun quote(sb: StringBuilder, s: String) {
            sb.append('"')
            for (ch in s) {
                when {
                    ch == '"' -> sb.append("\\\"")
                    ch == '\\' -> sb.append("\\\\")
                    ch == '\n' -> sb.append("\\n")
                    ch == '\r' -> sb.append("\\r")
                    ch == '\t' -> sb.append("\\t")
                    ch < ' ' -> sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    else -> sb.append(ch)
                }
            }
            sb.append('"')
        }

        /** Lê JSON para Map / List / String / Double / Boolean / null. Lança exceção se estiver malformado. */
        fun parse(text: String): Any? {
            val p = Parser(text)
            val v = p.value()
            p.skipWs()
            if (p.i != text.length) throw IllegalArgumentException("sobrou texto no fim")
            return v
        }

        private class Parser(val s: String) {
            var i = 0

            fun skipWs() {
                while (i < s.length && s[i].isWhitespace()) i++
            }

            fun value(): Any? {
                skipWs()
                if (i >= s.length) throw IllegalArgumentException("fim inesperado")
                return when (val c = s[i]) {
                    '{' -> obj()
                    '[' -> arr()
                    '"' -> str()
                    't' -> lit("true", true)
                    'f' -> lit("false", false)
                    'n' -> lit("null", null)
                    else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("caractere inesperado '$c'")
                }
            }

            private fun lit(word: String, v: Any?): Any? {
                if (!s.startsWith(word, i)) throw IllegalArgumentException("valor inválido")
                i += word.length
                return v
            }

            private fun num(): Double {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
                return s.substring(st, i).toDouble()
            }

            private fun str(): String {
                i++ // abre aspas
                val sb = StringBuilder()
                while (true) {
                    if (i >= s.length) throw IllegalArgumentException("texto sem fim")
                    val c = s[i++]
                    when (c) {
                        '"' -> return sb.toString()
                        '\\' -> {
                            if (i >= s.length) throw IllegalArgumentException("escape sem fim")
                            when (val e = s[i++]) {
                                'n' -> sb.append('\n')
                                'r' -> sb.append('\r')
                                't' -> sb.append('\t')
                                'b' -> sb.append('\b')
                                'f' -> sb.append('\u000C')
                                'u' -> {
                                    if (i + 4 > s.length) throw IllegalArgumentException("unicode incompleto")
                                    sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                    i += 4
                                }
                                else -> sb.append(e) // \" \\ \/
                            }
                        }
                        else -> sb.append(c)
                    }
                }
            }

            private fun arr(): List<Any?> {
                i++
                val out = ArrayList<Any?>()
                skipWs()
                if (i < s.length && s[i] == ']') { i++; return out }
                while (true) {
                    out.add(value())
                    skipWs()
                    if (i >= s.length) throw IllegalArgumentException("lista sem fim")
                    when (s[i++]) {
                        ',' -> {}
                        ']' -> return out
                        else -> throw IllegalArgumentException("esperava , ou ]")
                    }
                }
            }

            private fun obj(): Map<String, Any?> {
                i++
                val out = LinkedHashMap<String, Any?>()
                skipWs()
                if (i < s.length && s[i] == '}') { i++; return out }
                while (true) {
                    skipWs()
                    if (i >= s.length || s[i] != '"') throw IllegalArgumentException("esperava nome")
                    val k = str()
                    skipWs()
                    if (i >= s.length || s[i++] != ':') throw IllegalArgumentException("esperava :")
                    out[k] = value()
                    skipWs()
                    if (i >= s.length) throw IllegalArgumentException("objeto sem fim")
                    when (s[i++]) {
                        ',' -> {}
                        '}' -> return out
                        else -> throw IllegalArgumentException("esperava , ou }")
                    }
                }
            }
        }
    }
}
