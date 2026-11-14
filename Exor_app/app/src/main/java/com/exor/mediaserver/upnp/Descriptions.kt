package com.exor.mediaserver.upnp

/** Static device / service description documents. */
object Descriptions {

    fun device(udn: String, friendlyName: String, model: String): String = """<?xml version="1.0" encoding="utf-8"?>
<root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
<specVersion><major>1</major><minor>0</minor></specVersion>
<device>
<dlna:X_DLNADOC>DMS-1.50</dlna:X_DLNADOC>
<deviceType>${Upnp.DEVICE_TYPE}</deviceType>
<friendlyName>${Upnp.esc(friendlyName)}</friendlyName>
<manufacturer>Exor</manufacturer>
<modelName>Exor Media Server</modelName>
<modelDescription>${Upnp.esc(model)}</modelDescription>
<modelNumber>1</modelNumber>
<UDN>$udn</UDN>
<iconList>
<icon><mimetype>image/png</mimetype><width>48</width><height>48</height><depth>24</depth><url>/icon/48.png</url></icon>
<icon><mimetype>image/png</mimetype><width>120</width><height>120</height><depth>24</depth><url>/icon/120.png</url></icon>
</iconList>
<serviceList>
<service><serviceType>${Upnp.CD_TYPE}</serviceType><serviceId>urn:upnp-org:serviceId:ContentDirectory</serviceId><SCPDURL>/cd/scpd.xml</SCPDURL><controlURL>/cd/control</controlURL><eventSubURL>/cd/event</eventSubURL></service>
<service><serviceType>${Upnp.CM_TYPE}</serviceType><serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId><SCPDURL>/cm/scpd.xml</SCPDURL><controlURL>/cm/control</controlURL><eventSubURL>/cm/event</eventSubURL></service>
</serviceList>
</device>
</root>"""

    private fun arg(name: String, dir: String, rel: String) =
        "<argument><name>$name</name><direction>$dir</direction><relatedStateVariable>$rel</relatedStateVariable></argument>"

    private fun v(name: String, type: String, events: Boolean = false, allowed: List<String> = emptyList()): String {
        val list = if (allowed.isEmpty()) "" else
            "<allowedValueList>" + allowed.joinToString("") { "<allowedValue>$it</allowedValue>" } + "</allowedValueList>"
        return "<stateVariable sendEvents=\"${if (events) "yes" else "no"}\"><name>$name</name><dataType>$type</dataType>$list</stateVariable>"
    }

    private fun action(name: String, vararg args: String) =
        "<action><name>$name</name><argumentList>${args.joinToString("")}</argumentList></action>"

    private const val SCPD_HEAD = """<?xml version="1.0" encoding="utf-8"?>
<scpd xmlns="urn:schemas-upnp-org:service-1-0"><specVersion><major>1</major><minor>0</minor></specVersion>"""

    val contentDirectoryScpd: String = SCPD_HEAD +
        "<actionList>" +
        action("GetSearchCapabilities", arg("SearchCaps", "out", "SearchCapabilities")) +
        action("GetSortCapabilities", arg("SortCaps", "out", "SortCapabilities")) +
        action("GetSystemUpdateID", arg("Id", "out", "SystemUpdateID")) +
        action(
            "Browse",
            arg("ObjectID", "in", "A_ARG_TYPE_ObjectID"), arg("BrowseFlag", "in", "A_ARG_TYPE_BrowseFlag"),
            arg("Filter", "in", "A_ARG_TYPE_Filter"), arg("StartingIndex", "in", "A_ARG_TYPE_Index"),
            arg("RequestedCount", "in", "A_ARG_TYPE_Count"), arg("SortCriteria", "in", "A_ARG_TYPE_SortCriteria"),
            arg("Result", "out", "A_ARG_TYPE_Result"), arg("NumberReturned", "out", "A_ARG_TYPE_Count"),
            arg("TotalMatches", "out", "A_ARG_TYPE_Count"), arg("UpdateID", "out", "A_ARG_TYPE_UpdateID"),
        ) +
        "</actionList><serviceStateTable>" +
        v("A_ARG_TYPE_ObjectID", "string") + v("A_ARG_TYPE_Result", "string") +
        v("A_ARG_TYPE_BrowseFlag", "string", allowed = listOf("BrowseMetadata", "BrowseDirectChildren")) +
        v("A_ARG_TYPE_Filter", "string") + v("A_ARG_TYPE_SortCriteria", "string") +
        v("A_ARG_TYPE_Index", "ui4") + v("A_ARG_TYPE_Count", "ui4") + v("A_ARG_TYPE_UpdateID", "ui4") +
        v("SearchCapabilities", "string") + v("SortCapabilities", "string") +
        v("SystemUpdateID", "ui4", events = true) +
        "</serviceStateTable></scpd>"

    val connectionManagerScpd: String = SCPD_HEAD +
        "<actionList>" +
        action("GetProtocolInfo", arg("Source", "out", "SourceProtocolInfo"), arg("Sink", "out", "SinkProtocolInfo")) +
        action("GetCurrentConnectionIDs", arg("ConnectionIDs", "out", "CurrentConnectionIDs")) +
        action(
            "GetCurrentConnectionInfo",
            arg("ConnectionID", "in", "A_ARG_TYPE_ConnectionID"),
            arg("RcsID", "out", "A_ARG_TYPE_RcsID"), arg("AVTransportID", "out", "A_ARG_TYPE_AVTransportID"),
            arg("ProtocolInfo", "out", "A_ARG_TYPE_ProtocolInfo"),
            arg("PeerConnectionManager", "out", "A_ARG_TYPE_ConnectionManager"),
            arg("PeerConnectionID", "out", "A_ARG_TYPE_ConnectionID"),
            arg("Direction", "out", "A_ARG_TYPE_Direction"), arg("Status", "out", "A_ARG_TYPE_ConnectionStatus"),
        ) +
        "</actionList><serviceStateTable>" +
        v("SourceProtocolInfo", "string", events = true) + v("SinkProtocolInfo", "string", events = true) +
        v("CurrentConnectionIDs", "string", events = true) +
        v(
            "A_ARG_TYPE_ConnectionStatus", "string",
            allowed = listOf("OK", "ContentFormatMismatch", "InsufficientBandwidth", "UnreliableChannel", "Unknown"),
        ) +
        v("A_ARG_TYPE_ConnectionManager", "string") + v("A_ARG_TYPE_Direction", "string", allowed = listOf("Input", "Output")) +
        v("A_ARG_TYPE_ProtocolInfo", "string") + v("A_ARG_TYPE_ConnectionID", "i4") +
        v("A_ARG_TYPE_AVTransportID", "i4") + v("A_ARG_TYPE_RcsID", "i4") +
        "</serviceStateTable></scpd>"
}
