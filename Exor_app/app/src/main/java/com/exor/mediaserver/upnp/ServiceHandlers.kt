package com.exor.mediaserver.upnp

import com.exor.mediaserver.media.Dlna

/** SOAP action dispatch for ContentDirectory and ConnectionManager. */
class ServiceHandlers(private val cd: ContentDirectory) {

    fun contentDirectory(call: SoapCall): String {
        val a = call.args
        return when (call.action) {
            "Browse" -> {
                val r = cd.browse(
                    objectId = a["ObjectID"] ?: "0",
                    flag = a["BrowseFlag"] ?: "BrowseDirectChildren",
                    start = a["StartingIndex"]?.toIntOrNull() ?: 0,
                    requested = a["RequestedCount"]?.toIntOrNull() ?: 0,
                )
                Soap.response(
                    Upnp.CD_TYPE, "Browse",
                    listOf(
                        "Result" to r.didl, "NumberReturned" to r.returned.toString(),
                        "TotalMatches" to r.total.toString(), "UpdateID" to cd.updateId.toString(),
                    ),
                )
            }
            "GetSystemUpdateID" -> Soap.response(Upnp.CD_TYPE, "GetSystemUpdateID", listOf("Id" to cd.updateId.toString()))
            "GetSearchCapabilities" -> Soap.response(Upnp.CD_TYPE, "GetSearchCapabilities", listOf("SearchCaps" to ""))
            "GetSortCapabilities" -> Soap.response(Upnp.CD_TYPE, "GetSortCapabilities", listOf("SortCaps" to ""))
            else -> throw SoapError(401, "Invalid Action")
        }
    }

    fun connectionManager(call: SoapCall): String = when (call.action) {
        "GetProtocolInfo" -> Soap.response(
            Upnp.CM_TYPE, "GetProtocolInfo", listOf("Source" to Dlna.sourceProtocolInfo, "Sink" to ""),
        )
        "GetCurrentConnectionIDs" -> Soap.response(Upnp.CM_TYPE, "GetCurrentConnectionIDs", listOf("ConnectionIDs" to "0"))
        "GetCurrentConnectionInfo" -> Soap.response(
            Upnp.CM_TYPE, "GetCurrentConnectionInfo",
            listOf(
                "RcsID" to "-1", "AVTransportID" to "-1", "ProtocolInfo" to "",
                "PeerConnectionManager" to "", "PeerConnectionID" to "-1",
                "Direction" to "Output", "Status" to "OK",
            ),
        )
        else -> throw SoapError(401, "Invalid Action")
    }
}
