package com.usaginoyami.thumb

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Sessão do recorte: pixels de trabalho, pixels originais (para "restaurar") e histórico de desfazer. */
class CutoutSession(
    val layerId: Int,
    var w: Int,
    var h: Int,
    var orig: IntArray,
    var px: IntArray,
    var bitmap: Bitmap,
) {
    class Snap(val w: Int, val h: Int, val px: IntArray, val orig: IntArray, val offX: Float, val offY: Float)

    val undo = ArrayDeque<Snap>()
    var offX = 0f   // quanto o centro da imagem andou por causa do "cortar sobra" (em px da imagem)
    var offY = 0f
}

class ThumbViewModel(app: Application) : AndroidViewModel(app) {

    var project by mutableStateOf(Templates.all[0].build())
        private set
    var selectedId by mutableStateOf<Int?>(null)
    var imageVersion by mutableIntStateOf(0)
        private set
    var guideX by mutableStateOf(false)
    var guideY by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    private val images = ConcurrentHashMap<String, Bitmap>()
    private var imgCounter = 1

    private val undo = ArrayDeque<Project>()
    private val redo = ArrayDeque<Project>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private var lastKey: String? = null
    private var lastTime = 0L

    init {
        ThumbIo.decodeResource(app, R.drawable.mascote_recorte)?.let { images[MASCOT_SRC] = it }
    }

    private val store = ProjectStore(app)

    fun image(key: String): Bitmap? = images[key]

    val selected: Layer? get() = project.get(selectedId)

    // ---------- edição com desfazer ----------

    /** [key] não nulo junta mudanças seguidas (arrastar, slider) num só passo de desfazer. */
    fun mutate(key: String? = null, f: (Project) -> Project) {
        val next = f(project)
        if (next == project) return
        val now = SystemClock.uptimeMillis()
        if (key == null || key != lastKey || now - lastTime > 800) {
            undo.addLast(project)
            if (undo.size > 60) undo.removeFirst()
            redo.clear()
        }
        lastKey = key
        lastTime = now
        project = next
        syncUndoFlags()
    }

    private fun syncUndoFlags() {
        canUndo = undo.isNotEmpty()
        canRedo = redo.isNotEmpty()
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(project)
        project = prev
        lastKey = null
        fixSelection()
        syncUndoFlags()
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(project)
        project = next
        lastKey = null
        fixSelection()
        syncUndoFlags()
    }

    private fun fixSelection() {
        if (project.get(selectedId) == null) selectedId = null
    }

    // ---------- camadas ----------

    fun transform(id: Int, cx: Float, cy: Float, rotation: Float, scale: Float) {
        mutate("gesture") { p -> p.get(id)?.let { p.replace(it.placed(cx, cy, rotation, scale)) } ?: p }
    }

    fun editText(key: String, f: (TextLayer) -> TextLayer) {
        val id = selectedId ?: return
        mutate(key) { p -> (p.get(id) as? TextLayer)?.let { p.replace(f(it)) } ?: p }
    }

    fun editImage(key: String, f: (ImageLayer) -> ImageLayer) {
        val id = selectedId ?: return
        mutate(key) { p -> (p.get(id) as? ImageLayer)?.let { p.replace(f(it)) } ?: p }
    }

    fun addText() {
        val id = project.nextId
        mutate { it.add { i -> TextLayer(i, "SEU TEXTO") } }
        selectedId = id
    }

    fun addMascot() {
        val bmp = images[MASCOT_SRC] ?: return
        val id = project.nextId
        mutate { it.add { i -> ImageLayer(i, MASCOT_SRC, bmp.width, bmp.height, scale = 0.7f) } }
        selectedId = id
    }

    fun addShape(kind: ShapeKind) {
        val id = project.nextId
        mutate { it.add { i -> ShapeLayer.create(i, kind) } }
        selectedId = id
    }

    fun addEmoji(emoji: String) {
        val id = project.nextId
        mutate { it.add { i -> emojiLayer(i, emoji) } }
        selectedId = id
    }

    fun editShape(key: String, f: (ShapeLayer) -> ShapeLayer) {
        val id = selectedId ?: return
        mutate(key) { p -> (p.get(id) as? ShapeLayer)?.let { p.replace(f(it)) } ?: p }
    }

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { ThumbIo.decode(getApplication(), uri) }
            if (bmp == null) {
                message = "Não consegui abrir essa imagem"
                return@launch
            }
            val key = "img:${imgCounter++}"
            images[key] = bmp
            val id = project.nextId
            mutate { it.add { i -> ImageLayer(i, key, bmp.width, bmp.height, scale = initialImageScale(bmp.width, bmp.height)) } }
            selectedId = id
            imageVersion++
        }
    }

    fun delete() {
        val id = selectedId ?: return
        mutate { it.remove(id) }
        selectedId = null
    }

    fun duplicate() {
        val id = selectedId ?: return
        val newId = project.nextId
        mutate { it.duplicate(id) }
        selectedId = newId
    }

    fun reorder(delta: Int) {
        val id = selectedId ?: return
        mutate { it.reorder(id, delta) }
    }

    fun fillCanvas() = editImage("fill") { it.copy(cx = CANVAS_W / 2f, cy = CANVAS_H / 2f, rotation = 0f, scale = coverScale(it.srcW, it.srcH)) }

    fun centerLayer() {
        val id = selectedId ?: return
        mutate { p -> p.get(id)?.let { p.replace(it.placed(CANVAS_W / 2f, CANVAS_H / 2f, it.rotation, it.scale)) } ?: p }
    }

    fun setBackground(bg: Background, key: String? = "bg") = mutate(key) { it.copy(background = bg) }

    fun applyTemplate(t: Template) {
        mutate { t.build() }
        selectedId = null
    }

    // ---------- recorte ----------

    var cutout by mutableStateOf<CutoutSession?>(null)
        private set
    var cutVersion by mutableIntStateOf(0)
        private set

    fun startCutout() {
        val l = selected as? ImageLayer ?: return
        val src = images[l.src] ?: return
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val work = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        work.setPixels(px, 0, w, 0, 0, w, h)
        cutout = CutoutSession(l.id, w, h, px.clone(), px, work)
        cutVersion++
    }

    fun cancelCutout() {
        cutout = null
    }

    private fun CutoutSession.snapshot() {
        undo.addLast(CutoutSession.Snap(w, h, px.clone(), orig, offX, offY))
        if (undo.size > 6) undo.removeFirst()
    }

    private fun CutoutSession.refresh() {
        if (bitmap.width != w || bitmap.height != h) bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(px, 0, w, 0, 0, w, h)
        cutVersion++
    }

    fun cutMagic(x: Int, y: Int, tolerance: Int, contiguous: Boolean) {
        val s = cutout ?: return
        s.snapshot()
        val n = Cutout.magicErase(s.px, s.w, s.h, x, y, tolerance, contiguous)
        if (n == 0) s.undo.removeLast() else s.refresh()
    }

    fun cutAuto(tolerance: Int) {
        val s = cutout ?: return
        s.snapshot()
        var n = 0
        for ((x, y) in listOf(0 to 0, s.w - 1 to 0, 0 to s.h - 1, s.w - 1 to s.h - 1)) n += Cutout.magicErase(s.px, s.w, s.h, x, y, tolerance, true)
        if (n == 0) {
            s.undo.removeLast()
            message = "Os cantos já estão transparentes"
        } else s.refresh()
    }

    fun cutBrushStart() {
        cutout?.snapshot()
    }

    fun cutBrush(x: Float, y: Float, radius: Float, restore: Boolean) {
        val s = cutout ?: return
        Cutout.brush(s.px, s.w, s.h, x, y, radius, if (restore) s.orig else null)
    }

    /** Depois de pintar um trecho: passa os pixels para a imagem que aparece na tela. */
    fun cutCommit() {
        cutout?.refresh()
    }

    fun cutSoften() {
        val s = cutout ?: return
        s.snapshot()
        Cutout.softenEdges(s.px, s.w, s.h)
        s.refresh()
    }

    fun cutCrop() {
        val s = cutout ?: return
        val b = Cutout.contentBounds(s.px, s.w, s.h)
        if (b == null) {
            message = "Não sobrou nada para manter"
            return
        }
        val nw = b[2] - b[0]
        val nh = b[3] - b[1]
        if (nw == s.w && nh == s.h) {
            message = "Já está sem sobra"
            return
        }
        s.snapshot()
        val npx = IntArray(nw * nh)
        val nor = IntArray(nw * nh)
        for (y in 0 until nh) {
            System.arraycopy(s.px, (y + b[1]) * s.w + b[0], npx, y * nw, nw)
            System.arraycopy(s.orig, (y + b[1]) * s.w + b[0], nor, y * nw, nw)
        }
        s.offX += (b[0] + b[2]) / 2f - s.w / 2f
        s.offY += (b[1] + b[3]) / 2f - s.h / 2f
        s.px = npx
        s.orig = nor
        s.w = nw
        s.h = nh
        s.refresh()
    }

    fun cutUndo() {
        val s = cutout ?: return
        val snap = s.undo.removeLastOrNull() ?: return
        s.offX = snap.offX
        s.offY = snap.offY
        s.px = snap.px
        s.orig = snap.orig
        s.w = snap.w
        s.h = snap.h
        s.refresh()
    }

    fun applyCutout() {
        val s = cutout ?: return
        val layer = project.get(s.layerId) as? ImageLayer
        if (layer == null) {
            cutout = null
            return
        }
        val key = "img:${imgCounter++}"
        images[key] = s.bitmap
        val (ncx, ncy) = toCanvas(layer, centerShiftX(s), centerShiftY(s))
        mutate { p -> p.replace(layer.copy(src = key, srcW = s.w, srcH = s.h, cx = ncx, cy = ncy)) }
        cutout = null
        imageVersion++
    }

    // O centro da imagem cortada, em coordenadas da imagem original, é (offX, offY) a partir do centro antigo.
    private fun centerShiftX(s: CutoutSession) = s.offX
    private fun centerShiftY(s: CutoutSession) = s.offY

    // ---------- projetos salvos ----------

    var projects by mutableStateOf<List<ProjectStore.Meta>>(emptyList())
        private set
    var currentId by mutableStateOf<String?>(null)
        private set
    var projectName by mutableStateOf("")
        private set
    var projectBusy by mutableStateOf(false)
        private set

    /** O que está gravado no disco (ou o ponto de partida): se [project] for diferente, há mudanças sem salvar. */
    private var savedProject by mutableStateOf(project)

    val hasUnsaved: Boolean get() = project != savedProject

    fun refreshProjects() {
        viewModelScope.launch {
            projects = withContext(Dispatchers.IO) { runCatching { store.list() }.getOrDefault(emptyList()) }
        }
    }

    /** Grava o projeto atual. [asNew] cria uma cópia em vez de sobrescrever o aberto. Devolve false se falhou. */
    private suspend fun persist(asNew: Boolean = false): Boolean {
        val snap = project
        val name = projectTitle(snap)
        val id = if (asNew) null else currentId
        val saved = withContext(Dispatchers.IO) {
            runCatching { store.save(id, name, snap, ::image) }.getOrNull()
        }
        if (saved == null) return false
        currentId = saved
        projectName = name
        savedProject = snap
        projects = withContext(Dispatchers.IO) { runCatching { store.list() }.getOrDefault(projects) }
        return true
    }

    fun saveProject(asNew: Boolean = false) {
        if (projectBusy) return
        projectBusy = true
        viewModelScope.launch {
            try {
                val ok = persist(asNew)
                message = if (ok) "Projeto salvo" else "Não foi possível salvar o projeto"
            } finally {
                projectBusy = false
            }
        }
    }

    /** Ao sair do app: se o projeto já foi salvo alguma vez e mudou, grava de novo sem perguntar. */
    fun autosave() {
        if (currentId == null || !hasUnsaved || projectBusy) return
        projectBusy = true
        viewModelScope.launch {
            try {
                persist()
            } finally {
                projectBusy = false
            }
        }
    }

    private fun resetEditor(newProject: Project, id: String?, name: String) {
        project = newProject
        savedProject = newProject
        currentId = id
        projectName = name
        undo.clear()
        redo.clear()
        lastKey = null
        selectedId = null
        guideX = false
        guideY = false
        cutout = null
        syncUndoFlags()
        imageVersion++
    }

    /**
     * Salva o projeto atual (se tinha mudanças) antes de trocar. Devolve false se não deve trocar:
     * o salvamento falhou, ou o usuário mexeu no projeto enquanto salvava (a troca apagaria essa edição).
     */
    private suspend fun saveBeforeSwitch(): Boolean {
        if (!hasUnsaved) return true
        val before = project
        if (!persist()) {
            message = "Não consegui salvar o projeto atual. Nada foi trocado"
            return false
        }
        if (project !== before) {
            message = "O projeto mudou durante o salvamento. Tente de novo"
            return false
        }
        return true
    }

    /** Começa um projeto em branco. Se o atual tinha mudanças, ele é salvo antes para não perder nada. */
    fun newProject() {
        if (projectBusy) return
        projectBusy = true
        viewModelScope.launch {
            try {
                if (!saveBeforeSwitch()) return@launch
                images.keys.filter { it != MASCOT_SRC }.forEach { images.remove(it) }
                imgCounter = 1
                resetEditor(Project(), null, "")
                message = "Projeto novo"
            } finally {
                projectBusy = false
            }
        }
    }

    fun openProject(id: String) {
        if (projectBusy) return
        if (id == currentId) {
            message = "Esse projeto já está aberto"
            return
        }
        projectBusy = true
        viewModelScope.launch {
            try {
                val opened = withContext(Dispatchers.IO) { runCatching { store.load(id) }.getOrNull() }
                if (opened == null) {
                    message = "Não consegui abrir esse projeto"
                    return@launch
                }
                if (!saveBeforeSwitch()) return@launch
                // camadas cuja imagem sumiu do disco são descartadas (aparece o aviso abaixo)
                val all = opened.loaded.project
                val kept = all.layers.filter { it !is ImageLayer || it.src == MASCOT_SRC || opened.images.containsKey(it.src) }
                val missing = all.layers.size - kept.size
                images.keys.filter { it != MASCOT_SRC }.forEach { images.remove(it) }
                images.putAll(opened.images)
                imgCounter = ProjectJson.maxImageNumber(opened.images.keys) + 1
                resetEditor(all.copy(layers = kept), id, opened.loaded.name)
                message = if (missing == 0) "Projeto aberto" else "Aberto, mas $missing imagem(ns) não foram encontradas"
            } finally {
                projectBusy = false
            }
        }
    }

    fun deleteProject(id: String) {
        if (projectBusy) return
        projectBusy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { runCatching { store.delete(id) } }
                if (id == currentId) {
                    // o projeto aberto continua na tela, mas agora é "novo" e conta como não salvo
                    currentId = null
                    projectName = ""
                    savedProject = Project()
                }
                projects = withContext(Dispatchers.IO) { runCatching { store.list() }.getOrDefault(emptyList()) }
                message = "Projeto apagado"
            } finally {
                projectBusy = false
            }
        }
    }

    fun thumbFile(id: String) = store.thumbFile(id)

    // ---------- prévia estilo YouTube ----------

    var previewOpen by mutableStateOf(false)
        private set
    var previewBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var previewTitle by mutableStateOf("Título do seu vídeo vai aparecer assim")
    private var previewGen = 0

    fun openPreview() {
        selectedId = null
        previewOpen = true
        previewBitmap = null
        val gen = ++previewGen
        val snapshot = project
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.Default) { Renderer.exportBitmap(snapshot, ::image) }
            if (gen == previewGen && previewOpen) previewBitmap = bmp
        }
    }

    fun closePreview() {
        previewOpen = false
        previewGen++
        previewBitmap = null
    }

    // ---------- exportar ----------

    var exportOpen by mutableStateOf(false)
        private set
    var exportPng by mutableStateOf(false)
        private set
    var exportEnc by mutableStateOf<ThumbIo.Encoded?>(null)
        private set
    var exportBusy by mutableStateOf(false)
        private set
    private var savedUri: Uri? = null
    private var exportGen = 0

    fun openExport() {
        selectedId = null
        exportOpen = true
        prepareExport()
    }

    fun closeExport() {
        exportOpen = false
        exportGen++
    }

    fun chooseExportPng(png: Boolean) {
        exportPng = png
        prepareExport()
    }

    private fun prepareExport() {
        val gen = ++exportGen
        val snapshot = project
        exportBusy = true
        exportEnc = null
        savedUri = null
        viewModelScope.launch {
            val enc = withContext(Dispatchers.Default) {
                val bmp = Renderer.exportBitmap(snapshot, ::image)
                try {
                    ThumbIo.encode(bmp, exportPng)
                } finally {
                    bmp.recycle()
                }
            }
            if (gen == exportGen) {
                exportEnc = enc
                exportBusy = false
            }
        }
    }

    /** Salva na galeria (uma vez só por exportação) e devolve o endereço, ou null se falhou. */
    fun save(onDone: (Uri) -> Unit) {
        val enc = exportEnc ?: return
        savedUri?.let { onDone(it); return }
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { ThumbIo.saveToGallery(getApplication(), enc) }
            if (uri == null) {
                message = "Não foi possível salvar"
            } else {
                savedUri = uri
                message = "Salvo em Imagens/UsagiThumb"
                onDone(uri)
            }
        }
    }
}
