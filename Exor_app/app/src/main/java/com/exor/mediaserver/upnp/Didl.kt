package com.exor.mediaserver.upnp

import com.exor.mediaserver.media.Dlna
import com.exor.mediaserver.media.Kind
import com.exor.mediaserver.media.MediaItem

/** Minimal DIDL-Lite writer. */
class Didl {
    private val sb = StringBuilder(2048).append(
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """ +
            """xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
            """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" """ +
            """xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">"""
    )

    fun container(id: String, parentId: String, title: String, childCount: Int, cls: String, creator: String? = null) {
        sb.append("<container id=\"").append(Upnp.esc(id)).append("\" parentID=\"").append(Upnp.esc(parentId))
            .append("\" restricted=\"1\" searchable=\"0\" childCount=\"").append(childCount).append("\">")
        sb.append("<dc:title>").append(Upnp.esc(title)).append("</dc:title>")
        if (!creator.isNullOrBlank()) sb.append("<dc:creator>").append(Upnp.esc(creator)).append("</dc:creator>")
        sb.append("<upnp:class>").append(cls).append("</upnp:class></container>")
    }

    fun item(m: MediaItem, parentId: String, base: String) {
        val code = m.kind.code
        sb.append("<item id=\"i:").append(code).append(':').append(m.id).append("\" parentID=\"")
            .append(Upnp.esc(parentId)).append("\" restricted=\"1\">")
        sb.append("<dc:title>").append(Upnp.esc(m.title)).append("</dc:title>")
        sb.append("<upnp:class>").append(Dlna.upnpClass(m.kind)).append("</upnp:class>")
        if (m.kind == Kind.AUDIO) {
            m.artist?.let {
                sb.append("<dc:creator>").append(Upnp.esc(it)).append("</dc:creator>")
                sb.append("<upnp:artist>").append(Upnp.esc(it)).append("</upnp:artist>")
            }
            m.album?.let { sb.append("<upnp:album>").append(Upnp.esc(it)).append("</upnp:album>") }
            if (m.track > 0) sb.append("<upnp:originalTrackNumber>").append(m.track).append("</upnp:originalTrackNumber>")
            if (m.albumId > 0) sb.append("<upnp:albumArtURI>").append(base).append("/art/").append(m.albumId).append("</upnp:albumArtURI>")
        } else {
            sb.append("<upnp:albumArtURI>").append(base).append("/thumb/").append(code).append('/').append(m.id).append("</upnp:albumArtURI>")
        }
        sb.append("<res protocolInfo=\"").append(Upnp.esc(Dlna.protocolInfo(m.kind, m.mime))).append('"')
        if (m.size > 0) sb.append(" size=\"").append(m.size).append('"')
        if (m.kind != Kind.PHOTO && m.durationMs > 0) sb.append(" duration=\"").append(Dlna.duration(m.durationMs)).append('"')
        if (m.kind != Kind.AUDIO && m.width > 0 && m.height > 0) sb.append(" resolution=\"").append(m.width).append('x').append(m.height).append('"')
        sb.append('>').append(base).append("/media/").append(code).append('/').append(m.id).append("</res></item>")
    }

    fun build(): String = sb.append("</DIDL-Lite>").toString()
}
