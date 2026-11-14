package com.exor.mediaserver.core

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

data class LocalNet(val ip: Inet4Address, val nif: NetworkInterface)

object NetUtil {
    private val ignored = listOf("rmnet", "ccmni", "tun", "dummy", "v4-", "lo")

    /** Best LAN interface: Wi-Fi first, then hotspot, then ethernet. Never mobile data. */
    fun findLocal(): LocalNet? {
        val found = mutableListOf<LocalNet>()
        val all = try {
            Collections.list(NetworkInterface.getNetworkInterfaces() ?: return null)
        } catch (e: Exception) {
            return null
        }
        for (nif in all) {
            try {
                if (!nif.isUp || nif.isLoopback || !nif.supportsMulticast()) continue
                if (ignored.any { nif.name.startsWith(it) }) continue
                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr is Inet4Address && addr.isSiteLocalAddress) found += LocalNet(addr, nif)
                }
            } catch (_: Exception) {
            }
        }
        fun rank(n: LocalNet) = when {
            n.nif.name.startsWith("wlan") -> 0
            n.nif.name.startsWith("swlan") || n.nif.name.startsWith("ap") -> 1
            n.nif.name.startsWith("eth") -> 2
            else -> 3
        }
        return found.minByOrNull(::rank)
    }

    fun localIp(ctx: android.content.Context? = null): String? = findLocal()?.ip?.hostAddress
}
