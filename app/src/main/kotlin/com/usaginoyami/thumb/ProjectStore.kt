package com.usaginoyami.thumb

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Projetos salvos no armazenamento privado do app (filesDir/projects/<id>/):
 *   project.json  — camadas e fundo (ver [ProjectJson])
 *   images/ (arquivos .png)  — as imagens importadas/recortadas (o mascote vem do app)
 *   thumb.jpg     — miniatura 320x180 para a lista
 * Nada sai do aparelho e não precisa de permissão.
 */
class ProjectStore(private val context: Context) {

    class Meta(val id: String, val name: String, val updated: Long)

    class Opened(val loaded: ProjectJson.Loaded, val images: Map<String, Bitmap>)

    private val root: File get() = File(context.filesDir, "projects").also { it.mkdirs() }

    private fun dir(id: String) = File(root, id)

    fun thumbFile(id: String) = File(dir(id), "thumb.jpg")

    private fun imageFile(id: String, key: String) = File(File(dir(id), "images"), key.replace(':', '_') + ".png")

    fun list(): List<Meta> =
        (root.listFiles { f -> f.isDirectory }.orEmpty()).mapNotNull { d ->
            val json = File(d, "project.json")
            if (!json.isFile) return@mapNotNull null
            val name = try {
                ProjectJson.decode(json.readText())?.name
            } catch (e: Exception) {
                null
            } ?: return@mapNotNull null
            Meta(d.name, name, json.lastModified())
        }.sortedByDescending { it.updated }

    /**
     * Grava o projeto. [id] nulo cria um projeto novo. Devolve o id usado.
     * As imagens só são regravadas se o arquivo ainda não existe (cada imagem tem uma chave própria que nunca é reaproveitada).
     */
    fun save(id: String?, name: String, project: Project, image: (String) -> Bitmap?): String {
        val pid = id ?: newId()
        val d = dir(pid)
        val imgDir = File(d, "images").also { it.mkdirs() }
        val keys = ProjectJson.imageKeys(project)
        for (k in keys) {
            val f = imageFile(pid, k)
            if (f.isFile) continue
            val bmp = image(k) ?: continue
            val tmp = File(imgDir, f.name + ".tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            move(tmp, f)
        }
        // miniatura da lista
        val thumb = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(thumb)
            c.scale(320f / CANVAS_W, 180f / CANVAS_H)
            Renderer.render(c, project, image)
            val tmp = File(d, "thumb.jpg.tmp")
            tmp.outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            move(tmp, thumbFile(pid))
        } finally {
            thumb.recycle()
        }

        // o JSON por último: se algo falhar antes, o projeto antigo continua válido
        val tmp = File(d, "project.json.tmp")
        tmp.writeText(ProjectJson.encode(project, name))
        move(tmp, File(d, "project.json"))

        // só agora apaga imagens que o projeto não usa mais (recortes antigos etc.)
        val keep = keys.map { imageFile(pid, it).name }.toSet()
        imgDir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
        return pid
    }

    fun load(id: String): Opened? {
        val json = File(dir(id), "project.json")
        if (!json.isFile) return null
        val loaded = ProjectJson.decode(json.readText()) ?: return null
        val images = HashMap<String, Bitmap>()
        for (k in ProjectJson.imageKeys(loaded.project)) {
            val f = imageFile(id, k)
            if (!f.isFile) continue
            val opts = BitmapFactory.Options().apply { inScaled = false }
            BitmapFactory.decodeFile(f.path, opts)?.let { images[k] = it }
        }
        return Opened(loaded, images)
    }

    fun delete(id: String) {
        dir(id).deleteRecursively()
    }

    private fun newId(): String {
        var n = System.currentTimeMillis()
        while (File(root, n.toString()).exists()) n++
        return n.toString()
    }

    private fun move(from: File, to: File) {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}
