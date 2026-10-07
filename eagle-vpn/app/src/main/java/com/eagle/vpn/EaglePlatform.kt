package com.eagle.vpn

import android.net.VpnService
import io.nekohasekai.libbox.*
import java.net.NetworkInterface as JavaNetworkInterface

class EaglePlatform(private val vpnService: VpnService) : PlatformInterface {
    override fun autoDetectInterfaceControl(fd: Int) { vpnService.protect(fd) }

    override fun openTun(options: TunOptions): Int {
        val addresses = mutableListOf<Pair<String, Int>>()
        val routes = mutableListOf<Pair<String, Int>>()
        val dns = mutableListOf<String>()

        val a4 = options.inet4Address
        while (a4.hasNext()) {
            val p = a4.next()
            addresses += p.address() to p.prefix()
        }
        val a6 = options.inet6Address
        while (a6.hasNext()) {
            val p = a6.next()
            addresses += p.address() to p.prefix()
        }

        if (options.autoRoute) {
            val d = options.dnsServerAddress
            while (d.hasNext()) dns += d.next()

            val r4 = options.inet4RouteRange
            while (r4.hasNext()) {
                val p = r4.next()
                routes += p.address() to p.prefix()
            }
            val r6 = options.inet6RouteRange
            while (r6.hasNext()) {
                val p = r6.next()
                routes += p.address() to p.prefix()
            }
        }

        return (vpnService as EagleVpnService).establishTun(
            "EAGLE VPN", options.mtu, addresses, routes, dns
        )
    }

    override fun createBridge(options: BridgeOptions): BridgeSession {
        throw UnsupportedOperationException("Bridge is not enabled")
    }

    override fun usePlatformAutoDetectInterfaceControl() = true
    override fun useProcFS() = false
    override fun underNetworkExtension() = false
    override fun includeAllNetworks() = false
    override fun usePlatformBridge() = false
    override fun usePlatformShell() = false

    override fun getInterfaces(): NetworkInterfaceIterator {
        val list = JavaNetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        return object : NetworkInterfaceIterator {
            var i = 0
            override fun hasNext() = i < list.size
            override fun next(): io.nekohasekai.libbox.NetworkInterface {
                val n = list[i++]
                return io.nekohasekai.libbox.NetworkInterface().apply {
                    index = n.index
                    name = n.name
                    mtu = n.mtu
                }
            }
        }
    }

    override fun clearDNSCache() {}
    override fun readWIFIState(): WIFIState? = null
    override fun localDNSTransport(): LocalDNSTransport? = null
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}
    override fun startNeighborMonitor(listener: NeighborUpdateListener) {}
    override fun closeNeighborMonitor(listener: NeighborUpdateListener) {}
    override fun findConnectionOwner(ipProtocol: Int, sourceAddress: String, sourcePort: Int, destinationAddress: String, destinationPort: Int) = ConnectionOwner()
    override fun registerMyInterface(name: String) {}
    override fun tailscaleHostname() = ""
    override fun checkPlatformShell() {}
    override fun lookupSFTPServer() = ""
    override fun lookupUser(name: String): PlatformUser = throw UnsupportedOperationException("Platform user lookup is not available")
    override fun openShellSession(user: PlatformUser, workingDirectory: String, environment: StringIterator, command: String, timeout: Int, maxOutput: Int): ShellSession = throw UnsupportedOperationException("Platform shell is not available")
    override fun readSystemSSHHostKey() = ""
    override fun sendNotification(notification: Notification) {}
    override fun cancelNotification(identifier: String, typeID: Int) {}
}
