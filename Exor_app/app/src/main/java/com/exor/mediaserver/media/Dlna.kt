package com.exor.mediaserver.media

/** DLNA helper strings. Kept deliberately simple: no transcoding, no PN guessing. */
object Dlna {
    private const val FLAGS_STREAM = "01700000000000000000000000000000"
    private const val FLAGS_IMAGE = "00f00000000000000000000000000000"

    // OP=01 -> byte range requests supported (seek). CI=0 -> not transcoded.
    fun features(kind: Kind): String = when (kind) {
        Kind.PHOTO -> "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=$FLAGS_IMAGE"
        else -> "DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=$FLAGS_STREAM"
    }

    fun protocolInfo(kind: Kind, mime: String) = "http-get:*:$mime:${features(kind)}"

    fun upnpClass(kind: Kind) = when (kind) {
        Kind.VIDEO -> "object.item.videoItem"
        Kind.AUDIO -> "object.item.audioItem.musicTrack"
        Kind.PHOTO -> "object.item.imageItem.photo"
    }

    fun duration(ms: Long): String {
        val total = ms / 1000
        return "%d:%02d:%02d.%03d".format(total / 3600, (total / 60) % 60, total % 60, ms % 1000)
    }

    /** Fix missing / generic MIME types coming from MediaStore. */
    fun fixMime(mime: String?, name: String, kind: Kind): String {
        if (!mime.isNullOrBlank() && mime != "application/octet-stream") return mime
        return when (name.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "ts" -> "video/mp2t"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> when (kind) {
                Kind.VIDEO -> "video/mp4"
                Kind.AUDIO -> "audio/mpeg"
                Kind.PHOTO -> "image/jpeg"
            }
        }
    }

    /** What we claim we can serve (ConnectionManager.GetProtocolInfo). */
    val sourceProtocolInfo: String = listOf(
        "video/mp4", "video/x-matroska", "video/x-msvideo", "video/mpeg", "video/quicktime",
        "video/webm", "video/3gpp", "video/x-ms-wmv", "video/mp2t",
        "audio/mpeg", "audio/mp4", "audio/flac", "audio/x-flac", "audio/ogg", "audio/wav",
        "audio/x-wav", "audio/aac", "audio/x-ms-wma",
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
    ).joinToString(",") { "http-get:*:$it:*" }
}
