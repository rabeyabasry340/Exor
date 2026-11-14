package com.exor.mediaserver.server

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import com.exor.mediaserver.media.Dlna
import com.exor.mediaserver.media.Kind
import com.exor.mediaserver.media.MediaStoreRepo
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException

/** Streams files with HTTP Range support (needed for seeking on the TV) and serves thumbnails. */
class MediaStreamer(
    context: Context,
    private val repo: MediaStoreRepo,
    private val onStreamStart: () -> Unit,
    private val onStreamEnd: () -> Unit,
) {
    private val cr = context.applicationContext.contentResolver

    // Thumbnail cache capped at ~4 MB of JPEG bytes.
    private val thumbs = object : LruCache<String, ByteArray>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    fun clearCache() = thumbs.evictAll()

    fun serveMedia(req: HttpRequest, r: HttpResponder, kindCode: String, idText: String) {
        val kind = Kind.fromCode(kindCode)
        val id = idText.toLongOrNull()
        if (kind == null || id == null) return r.text(404, "Not found")
        val item = repo.item(kind, id) ?: return r.text(404, "Not found")
        val pfd = try { cr.openFileDescriptor(repo.itemUri(kind, id), "r") } catch (e: Exception) { null }
            ?: return r.text(404, "Not found")

        pfd.use {
            val total = if (pfd.statSize >= 0) pfd.statSize else item.size
            var start = 0L
            var end = total - 1
            var partial = false
            val range = req.headers["range"]
            if (range != null && range.startsWith("bytes=") && total > 0) {
                val spec = range.removePrefix("bytes=").substringBefore(',').trim()
                val a = spec.substringBefore('-').trim()
                val b = spec.substringAfter('-', "").trim()
                try {
                    if (a.isEmpty()) {            // suffix: last N bytes
                        start = (total - b.toLong()).coerceAtLeast(0)
                    } else {
                        start = a.toLong()
                        if (b.isNotEmpty()) end = minOf(b.toLong(), total - 1)
                    }
                    partial = true
                } catch (e: NumberFormatException) {
                    partial = false
                }
                if (partial && (start > end || start >= total)) {
                    return r.header(416, null, 0, mapOf("Content-Range" to "bytes */$total"))
                }
            }
            val length = if (total <= 0) 0 else end - start + 1
            val extra = LinkedHashMap<String, String>()
            extra["Accept-Ranges"] = "bytes"
            if (partial) extra["Content-Range"] = "bytes $start-$end/$total"
            extra["transferMode.dlna.org"] = if (kind == Kind.PHOTO) "Interactive" else "Streaming"
            extra["contentFeatures.dlna.org"] = Dlna.features(kind)

            r.header(if (partial) 206 else 200, item.mime, length, extra)
            if (r.isHead || length == 0L) return

            onStreamStart()
            try {
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val ch = fis.channel
                    ch.position(start)
                    val buf = ByteArray(64 * 1024)
                    var left = length
                    val out = r.stream
                    while (left > 0) {
                        val n = fis.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) break
                        out.write(buf, 0, n)
                        left -= n
                    }
                    out.flush()
                }
            } finally {
                onStreamEnd()
            }
        }
    }

    fun serveThumb(r: HttpResponder, kindCode: String, idText: String) {
        val kind = Kind.fromCode(kindCode)
        val id = idText.toLongOrNull()
        if (kind == null || id == null) return r.text(404, "Not found")
        sendThumb(r, "t:$kindCode:$id", repo.itemUri(kind, id))
    }

    fun serveAlbumArt(r: HttpResponder, idText: String) {
        val id = idText.toLongOrNull() ?: return r.text(404, "Not found")
        sendThumb(r, "al:$id", repo.albumUri(id))
    }

    private fun sendThumb(r: HttpResponder, key: String, uri: android.net.Uri) {
        var bytes = thumbs.get(key)
        if (bytes == null) {
            bytes = try {
                val bmp = cr.loadThumbnail(uri, Size(320, 320), null)
                val bo = ByteArrayOutputStream(24 * 1024)
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, bo)
                bmp.recycle()
                bo.toByteArray()
            } catch (e: Exception) {
                null
            }
            if (bytes == null) return r.text(404, "No thumbnail")
            thumbs.put(key, bytes)
        }
        r.send(
            200, "image/jpeg", bytes,
            mapOf("Cache-Control" to "max-age=86400", "contentFeatures.dlna.org" to "DLNA.ORG_OP=00;DLNA.ORG_CI=1"),
        )
    }
}
