package com.usaginoyami.thumb

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import kotlin.math.max

/** Entrada e saída de arquivos (leitura de imagens, JPG/PNG e salvar na galeria). */
object ThumbIo {

    /** Maior lado das imagens importadas: o canvas tem 1280, então mais que isso só gasta memória. */
    const val MAX_SIDE = 1280

    fun decode(context: Context, uri: Uri): Bitmap? = try {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val s = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * s).toInt().coerceAtLeast(1),
                    (info.size.height * s).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // pixels editáveis (recorte)
        }.let { ensureArgb(it) }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    fun decodeResource(context: Context, resId: Int): Bitmap? = try {
        BitmapFactory.decodeResource(context.resources, resId, BitmapFactory.Options().apply { inScaled = false })
    } catch (e: Exception) {
        null
    }

    private fun ensureArgb(b: Bitmap): Bitmap =
        if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, true)

    fun compress(bmp: Bitmap, png: Boolean, quality: Int): ByteArray {
        val out = ByteArrayOutputStream(512 * 1024)
        if (png) bmp.compress(Bitmap.CompressFormat.PNG, 100, out) else bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    class Encoded(val bytes: ByteArray, val png: Boolean, val quality: Int) {
        val overLimit get() = bytes.size > YT_MAX_BYTES
    }

    /** PNG sem perdas, ou JPG com a melhor qualidade que cabe em 2 MB. */
    fun encode(bmp: Bitmap, png: Boolean): Encoded {
        if (png) return Encoded(compress(bmp, true, 100), true, 100)
        val q = Export.bestQuality(YT_MAX_BYTES) { compress(bmp, false, it).size }
        return Encoded(compress(bmp, false, q), false, q)
    }

    /** Grava em Imagens/UsagiThumb. No Android 10+ não precisa de permissão. */
    fun saveToGallery(context: Context, enc: Encoded): Uri? {
        val name = Export.fileName(System.currentTimeMillis(), enc.png)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, if (enc.png) "image/png" else "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/UsagiThumb")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { it.write(enc.bytes) } ?: throw IllegalStateException("sem saída")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
