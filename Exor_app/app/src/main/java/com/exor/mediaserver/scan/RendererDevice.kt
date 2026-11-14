package com.exor.mediaserver.scan

enum class DeviceKind { TV, RECEIVER, OTHER }

data class RendererDevice(
    val udn: String,
    val name: String,
    val manufacturer: String,
    val model: String,
    val ip: String,
    val location: String,
    val avTransportControlUrl: String?,   // used by casting in part 2
    val renderingControlUrl: String?,
) {
    val kind: DeviceKind
        get() {
            val all = "$name $manufacturer $model".lowercase()
            return when {
                "bravia" in all || ("sony" in all && "tv" in all) || " tv" in " $all" -> DeviceKind.TV
                listOf("pioneer", "denon", "onkyo", "yamaha", "marantz", "receiver", "avr").any { it in all } -> DeviceKind.RECEIVER
                else -> DeviceKind.OTHER
            }
        }
    val isSony: Boolean get() = "sony" in manufacturer.lowercase() || "bravia" in name.lowercase()
}
