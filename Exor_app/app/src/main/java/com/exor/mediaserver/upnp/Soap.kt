package com.exor.mediaserver.upnp

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream

class SoapCall(val action: String, val args: Map<String, String>)

class SoapError(val code: Int, val description: String) : Exception(description)

object Soap {
    /** Parses `Envelope/Body/<Action>/<Arg>text</Arg>`. Namespaces are ignored on purpose. */
    fun parse(body: ByteArray): SoapCall? {
        return try {
            val p = Xml.newPullParser()
            p.setInput(ByteArrayInputStream(body), "UTF-8")
            var depth = 0
            var action: String? = null
            var arg: String? = null
            val args = LinkedHashMap<String, String>()
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        val local = p.name.substringAfter(':')
                        if (depth == 3) action = local
                        else if (depth == 4) {
                            arg = local
                            args[local] = ""
                        }
                    }
                    XmlPullParser.TEXT -> if (depth == 4 && arg != null) args[arg] = (args[arg] ?: "") + p.text
                    XmlPullParser.END_TAG -> {
                        if (depth == 4) arg = null
                        depth--
                    }
                }
                ev = p.next()
            }
            action?.let { SoapCall(it, args) }
        } catch (e: Exception) {
            null
        }
    }

    /** [out] values are written verbatim in order; this function escapes them. */
    fun response(serviceType: String, action: String, out: List<Pair<String, String>>): String {
        val sb = StringBuilder(512)
        sb.append("""<?xml version="1.0" encoding="utf-8"?>""")
        sb.append("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>""")
        sb.append("<u:${action}Response xmlns:u=\"$serviceType\">")
        for ((k, v) in out) sb.append('<').append(k).append('>').append(Upnp.esc(v)).append("</").append(k).append('>')
        sb.append("</u:${action}Response></s:Body></s:Envelope>")
        return sb.toString()
    }

    fun fault(code: Int, description: String): String =
        """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>""" +
            """<s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring><detail>""" +
            """<UPnPError xmlns="urn:schemas-upnp-org:control-1-0"><errorCode>$code</errorCode>""" +
            """<errorDescription>${Upnp.esc(description)}</errorDescription></UPnPError></detail></s:Fault></s:Body></s:Envelope>"""
}
