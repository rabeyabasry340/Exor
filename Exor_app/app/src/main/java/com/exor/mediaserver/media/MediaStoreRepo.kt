package com.exor.mediaserver.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore

/**
 * Thin, on-demand MediaStore reader. Nothing is scanned or indexed in the background:
 * every call is a small paged query, so the CPU stays idle until a TV actually browses.
 */
class MediaStoreRepo(context: Context) {
    private val cr: ContentResolver = context.applicationContext.contentResolver

    @Volatile private var folderCache = HashMap<Kind, List<Group>>()

    fun invalidate() {
        folderCache = HashMap()
    }

    fun baseUri(kind: Kind): Uri = when (kind) {
        Kind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        Kind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        Kind.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    fun itemUri(kind: Kind, id: Long): Uri = ContentUris.withAppendedId(baseUri(kind), id)

    fun albumUri(albumId: Long): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)

    private fun query(
        uri: Uri,
        projection: Array<String>,
        selection: String? = null,
        args: Array<String>? = null,
        sort: String? = null,
        offset: Int = 0,
        limit: Int = 0,
    ): Cursor? {
        val b = Bundle()
        if (selection != null) {
            b.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            b.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
        }
        if (sort != null) b.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
        if (limit > 0) {
            b.putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            b.putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        return try {
            cr.query(uri, projection, b, null)
        } catch (e: Exception) {
            null
        }
    }

    // ---------- counts ----------

    fun count(kind: Kind, selection: String? = null, args: Array<String>? = null): Int =
        query(baseUri(kind), arrayOf(MediaStore.MediaColumns._ID), baseSel(kind, selection), args)
            ?.use { it.count } ?: 0

    private fun baseSel(kind: Kind, selection: String?): String? {
        val music = if (kind == Kind.AUDIO) "${MediaStore.Audio.Media.IS_MUSIC} != 0" else null
        return listOfNotNull(music, selection).takeIf { it.isNotEmpty() }?.joinToString(" AND ")
    }

    fun libraryCounts(): LibraryCounts {
        val albums = query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Albums._ID))
            ?.use { it.count } ?: 0
        val recent = items(Kind.AUDIO, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC", 0, 2)
            .map { it.title }
        return LibraryCounts(count(Kind.VIDEO), albums, count(Kind.PHOTO), recent)
    }

    // ---------- items ----------

    fun items(
        kind: Kind,
        selection: String?,
        args: Array<String>?,
        sort: String,
        offset: Int,
        limit: Int,
    ): List<MediaItem> {
        val c = query(baseUri(kind), projection(kind), baseSel(kind, selection), args, sort, offset, limit)
            ?: return emptyList()
        val out = ArrayList<MediaItem>()
        c.use {
            while (it.moveToNext()) out += read(kind, it)
        }
        return out
    }

    fun item(kind: Kind, id: Long): MediaItem? =
        items(kind, "${MediaStore.MediaColumns._ID} = ?", arrayOf(id.toString()), "${MediaStore.MediaColumns._ID} ASC", 0, 1)
            .firstOrNull()

    private fun projection(kind: Kind): Array<String> {
        val common = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE,
        )
        return when (kind) {
            Kind.VIDEO -> common + arrayOf(
                MediaStore.MediaColumns.DURATION, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT,
            )
            Kind.PHOTO -> common + arrayOf(MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT)
            Kind.AUDIO -> common + arrayOf(
                MediaStore.MediaColumns.DURATION, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.TRACK,
            )
        }
    }

    private fun read(kind: Kind, c: Cursor): MediaItem {
        fun s(col: String): String? = c.getColumnIndex(col).takeIf { it >= 0 }?.let { c.getString(it) }
        fun l(col: String): Long = c.getColumnIndex(col).takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L
        val name = s(MediaStore.MediaColumns.DISPLAY_NAME).orEmpty()
        val title = s(MediaStore.MediaColumns.TITLE)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
        return MediaItem(
            kind = kind,
            id = l(MediaStore.MediaColumns._ID),
            title = title.ifBlank { "Untitled" },
            mime = Dlna.fixMime(s(MediaStore.MediaColumns.MIME_TYPE), name, kind),
            size = l(MediaStore.MediaColumns.SIZE),
            durationMs = l(MediaStore.MediaColumns.DURATION),
            width = l(MediaStore.MediaColumns.WIDTH).toInt(),
            height = l(MediaStore.MediaColumns.HEIGHT).toInt(),
            artist = s(MediaStore.Audio.Media.ARTIST)?.takeIf { it != "<unknown>" },
            album = s(MediaStore.Audio.Media.ALBUM)?.takeIf { it != "<unknown>" },
            albumId = l(MediaStore.Audio.Media.ALBUM_ID),
            track = (l(MediaStore.Audio.Media.TRACK) % 1000).toInt(),
        )
    }

    // ---------- groups ----------

    /** Folders (MediaStore buckets) for videos or photos. Cached until MediaStore changes. */
    fun folders(kind: Kind): List<Group> {
        folderCache[kind]?.let { return it }
        val seen = LinkedHashMap<String, String>()
        query(
            baseUri(kind),
            arrayOf(MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME),
            sort = "${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} ASC",
        )?.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                if (id !in seen) seen[id] = it.getString(1) ?: "Unknown"
            }
        }
        val list = seen.map { (id, name) ->
            Group(id, name, count(kind, "${MediaStore.MediaColumns.BUCKET_ID} = ?", arrayOf(id)))
        }
        folderCache[kind] = list
        return list
    }

    fun albumCount(): Int =
        query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Albums._ID))?.use { it.count } ?: 0

    fun albums(offset: Int, limit: Int): List<Group> {
        val out = ArrayList<Group>()
        query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.NUMBER_OF_SONGS,
            ),
            sort = "${MediaStore.Audio.Albums.ALBUM} ASC", offset = offset, limit = limit,
        )?.use {
            while (it.moveToNext()) {
                out += Group(
                    it.getLong(0).toString(), it.getString(1) ?: "Unknown album", it.getInt(3), it.getString(2),
                )
            }
        }
        return out
    }

    fun artistCount(): Int =
        query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Artists._ID))?.use { it.count } ?: 0

    fun artists(offset: Int, limit: Int): List<Group> {
        val out = ArrayList<Group>()
        query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST,
                MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
            ),
            sort = "${MediaStore.Audio.Artists.ARTIST} ASC", offset = offset, limit = limit,
        )?.use {
            while (it.moveToNext()) {
                out += Group(it.getLong(0).toString(), it.getString(1) ?: "Unknown artist", it.getInt(2))
            }
        }
        return out
    }

    fun albumTitle(albumId: Long): String? =
        query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Albums.ALBUM),
            "${MediaStore.Audio.Albums._ID} = ?", arrayOf(albumId.toString()),
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    fun artistTitle(artistId: Long): String? =
        query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Artists.ARTIST),
            "${MediaStore.Audio.Artists._ID} = ?", arrayOf(artistId.toString()),
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
}
