package com.exor.mediaserver.core

import kotlinx.coroutines.flow.MutableStateFlow

data class ServerStatus(
    val running: Boolean = false,
    val name: String = "",
    val ip: String? = null,
    val port: Int = 0,
    val activeStreams: Int = 0,
    val message: String? = null,
) {
    val url: String? get() = if (running && ip != null) "http://$ip:$port" else null
}

/** Process-wide status, written by the service, read by the UI and the floating window. */
object ServerState {
    val status = MutableStateFlow(ServerStatus())
}
