package com.exor.mediaserver.media

enum class Kind(val code: String) {
    VIDEO("v"), AUDIO("a"), PHOTO("p");

    companion object {
        fun fromCode(c: String): Kind? = entries.firstOrNull { it.code == c }
    }
}

data class MediaItem(
    val kind: Kind,
    val id: Long,
    val title: String,
    val mime: String,
    val size: Long,
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val artist: String? = null,
    val album: String? = null,
    val albumId: Long = 0,
    val track: Int = 0,
)

/** A folder / album / artist shown as a UPnP container. */
data class Group(val id: String, val title: String, val childCount: Int, val subtitle: String? = null)

data class LibraryCounts(
    val videos: Int = 0,
    val albums: Int = 0,
    val photos: Int = 0,
    val recentTracks: List<String> = emptyList(),
)
