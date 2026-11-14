package com.exor.mediaserver.upnp

import com.exor.mediaserver.core.Prefs
import com.exor.mediaserver.media.Dlna
import com.exor.mediaserver.media.Group
import com.exor.mediaserver.media.Kind
import com.exor.mediaserver.media.MediaItem
import com.exor.mediaserver.media.MediaStoreRepo
import java.util.concurrent.atomic.AtomicInteger

class BrowseResult(val didl: String, val returned: Int, val total: Int)

/**
 * Object id scheme (kept short, easy to debug):
 *   0                root
 *   v | m | p        Videos / Music / Photos
 *   v:all  v:fld  v:b:<bucketId>
 *   m:all  m:new  m:alb  m:al:<albumId>  m:art  m:ar:<artistId>
 *   p:all  p:fld  p:b:<bucketId>
 *   i:<v|a|p>:<mediaStoreId>   (items)
 */
class ContentDirectory(
    private val repo: MediaStoreRepo,
    private val prefs: Prefs,
    private val baseUrl: () -> String,
) {
    private val updateCounter = AtomicInteger(1)
    val updateId: Int get() = updateCounter.get()

    fun bump(): Int {
        repo.invalidate()
        return updateCounter.incrementAndGet()
    }

    // Hard cap per Browse page: keeps memory and CPU low even if a TV asks for "everything".
    private val maxPage = 300
    private val recentLimit = 100

    fun browse(objectId: String, flag: String, start: Int, requested: Int): BrowseResult {
        val count = if (requested <= 0) maxPage else minOf(requested, maxPage)
        val s = start.coerceAtLeast(0)
        return when (flag) {
            "BrowseMetadata" -> metadata(objectId)
            "BrowseDirectChildren" -> children(objectId, s, count)
            else -> throw SoapError(402, "Invalid args")
        }
    }

    // ---------- metadata ----------

    private fun metadata(id: String): BrowseResult {
        val d = Didl()
        if (id.startsWith("i:")) {
            val item = lookup(id) ?: throw SoapError(701, "No such object")
            d.item(item, parent(item.kind), baseUrl())
        } else {
            val (title, count) = containerInfo(id) ?: throw SoapError(701, "No such object")
            d.container(id, parentOfContainer(id), title, count, containerClass(id))
        }
        return BrowseResult(d.build(), 1, 1)
    }

    private fun lookup(id: String): MediaItem? {
        val p = id.split(':')
        if (p.size != 3) return null
        val kind = Kind.fromCode(p[1]) ?: return null
        return repo.item(kind, p[2].toLongOrNull() ?: return null)
    }

    private fun parent(kind: Kind) = when (kind) {
        Kind.VIDEO -> "v:all"
        Kind.AUDIO -> "m:all"
        Kind.PHOTO -> "p:all"
    }

    private fun containerInfo(id: String): Pair<String, Int>? {
        val p = id.split(':')
        return when {
            id == "0" -> "Exor" to rootChildren().size
            id == "v" -> "Videos" to 2
            id == "m" -> "Music" to 4
            id == "p" -> "Photos" to 2
            id == "v:all" -> "All videos" to repo.count(Kind.VIDEO)
            id == "p:all" -> "All photos" to repo.count(Kind.PHOTO)
            id == "m:all" -> "All tracks" to repo.count(Kind.AUDIO)
            id == "m:new" -> "Recently added" to minOf(repo.count(Kind.AUDIO), recentLimit)
            id == "v:fld" -> "Folders" to repo.folders(Kind.VIDEO).size
            id == "p:fld" -> "Folders" to repo.folders(Kind.PHOTO).size
            id == "m:alb" -> "Albums" to repo.albumCount()
            id == "m:art" -> "Artists" to repo.artistCount()
            p.size == 3 && p[1] == "b" && p[0] == "v" -> folderInfo(Kind.VIDEO, p[2])
            p.size == 3 && p[1] == "b" && p[0] == "p" -> folderInfo(Kind.PHOTO, p[2])
            p.size == 3 && p[0] == "m" && p[1] == "al" ->
                (repo.albumTitle(p[2].toLongOrNull() ?: return null) ?: "Album") to
                    repo.count(Kind.AUDIO, "album_id = ?", arrayOf(p[2]))
            p.size == 3 && p[0] == "m" && p[1] == "ar" ->
                (repo.artistTitle(p[2].toLongOrNull() ?: return null) ?: "Artist") to
                    repo.count(Kind.AUDIO, "artist_id = ?", arrayOf(p[2]))
            else -> null
        }
    }

    private fun folderInfo(kind: Kind, bucket: String): Pair<String, Int>? =
        repo.folders(kind).firstOrNull { it.id == bucket }?.let { it.title to it.childCount }

    private fun parentOfContainer(id: String): String {
        val p = id.split(':')
        return when {
            id == "0" -> "-1"
            p.size == 1 -> "0"
            p.size == 2 -> p[0]
            p[1] == "b" -> "${p[0]}:fld"
            p[1] == "al" -> "m:alb"
            p[1] == "ar" -> "m:art"
            else -> "0"
        }
    }

    private fun containerClass(id: String): String = when {
        id.startsWith("m:al:") -> "object.container.album.musicAlbum"
        id.startsWith("m:ar:") -> "object.container.person.musicArtist"
        else -> "object.container.storageFolder"
    }

    // ---------- children ----------

    private fun rootChildren(): List<Group> = buildList {
        if (prefs.shareVideos) add(Group("v", "Videos", 2))
        if (prefs.shareMusic) add(Group("m", "Music", 4))
        if (prefs.sharePhotos) add(Group("p", "Photos", 2))
    }

    private fun children(id: String, start: Int, count: Int): BrowseResult {
        val p = id.split(':')
        return when {
            id == "0" -> groups(id, rootChildren(), start, count)
            id == "v" -> groups(id, listOf(
                Group("v:all", "All videos", repo.count(Kind.VIDEO)),
                Group("v:fld", "Folders", repo.folders(Kind.VIDEO).size),
            ), start, count)
            id == "p" -> groups(id, listOf(
                Group("p:all", "All photos", repo.count(Kind.PHOTO)),
                Group("p:fld", "Folders", repo.folders(Kind.PHOTO).size),
            ), start, count)
            id == "m" -> groups(id, listOf(
                Group("m:all", "All tracks", repo.count(Kind.AUDIO)),
                Group("m:new", "Recently added", minOf(repo.count(Kind.AUDIO), recentLimit)),
                Group("m:alb", "Albums", repo.albumCount()),
                Group("m:art", "Artists", repo.artistCount()),
            ), start, count)

            id == "v:all" -> items(id, Kind.VIDEO, null, null, "date_added DESC", start, count)
            id == "p:all" -> items(id, Kind.PHOTO, null, null, "date_added DESC", start, count)
            id == "m:all" -> items(id, Kind.AUDIO, null, null, "title ASC", start, count)
            id == "m:new" -> items(id, Kind.AUDIO, null, null, "date_added DESC", start, count, cap = recentLimit)

            id == "v:fld" -> folderGroups(id, Kind.VIDEO, start, count)
            id == "p:fld" -> folderGroups(id, Kind.PHOTO, start, count)
            p.size == 3 && p[0] == "v" && p[1] == "b" ->
                items(id, Kind.VIDEO, "bucket_id = ?", arrayOf(p[2]), "date_added DESC", start, count)
            p.size == 3 && p[0] == "p" && p[1] == "b" ->
                items(id, Kind.PHOTO, "bucket_id = ?", arrayOf(p[2]), "date_added DESC", start, count)

            id == "m:alb" -> {
                val total = repo.albumCount()
                val page = repo.albums(start, count)
                val d = Didl()
                page.forEach { d.container("m:al:${it.id}", id, it.title, it.childCount, "object.container.album.musicAlbum", it.subtitle) }
                BrowseResult(d.build(), page.size, total)
            }
            id == "m:art" -> {
                val total = repo.artistCount()
                val page = repo.artists(start, count)
                val d = Didl()
                page.forEach { d.container("m:ar:${it.id}", id, it.title, it.childCount, "object.container.person.musicArtist") }
                BrowseResult(d.build(), page.size, total)
            }
            p.size == 3 && p[0] == "m" && p[1] == "al" ->
                items(id, Kind.AUDIO, "album_id = ?", arrayOf(p[2]), "track ASC", start, count)
            p.size == 3 && p[0] == "m" && p[1] == "ar" ->
                items(id, Kind.AUDIO, "artist_id = ?", arrayOf(p[2]), "album ASC, track ASC", start, count)

            else -> throw SoapError(701, "No such object")
        }
    }

    private fun groups(parentId: String, list: List<Group>, start: Int, count: Int): BrowseResult {
        val page = list.drop(start).take(count)
        val d = Didl()
        page.forEach { d.container(it.id, parentId, it.title, it.childCount, "object.container.storageFolder") }
        return BrowseResult(d.build(), page.size, list.size)
    }

    private fun folderGroups(parentId: String, kind: Kind, start: Int, count: Int): BrowseResult {
        val list = repo.folders(kind)
        val page = list.drop(start).take(count)
        val d = Didl()
        page.forEach { d.container("${kind.code}:b:${it.id}", parentId, it.title, it.childCount, "object.container.storageFolder") }
        return BrowseResult(d.build(), page.size, list.size)
    }

    private fun items(
        parentId: String, kind: Kind, sel: String?, args: Array<String>?, sort: String,
        start: Int, count: Int, cap: Int = Int.MAX_VALUE,
    ): BrowseResult {
        val total = minOf(repo.count(kind, sel, args), cap)
        val limit = minOf(count, (total - start).coerceAtLeast(0))
        val list = if (limit <= 0) emptyList() else repo.items(kind, sel, args, sort, start, limit)
        val d = Didl()
        val base = baseUrl()
        list.forEach { d.item(it, parentId, base) }
        return BrowseResult(d.build(), list.size, total)
    }
}
