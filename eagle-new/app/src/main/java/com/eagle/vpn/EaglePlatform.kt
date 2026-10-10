package com.eagle.vpn

import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface as JavaNetworkInterface
import io.nekohasekai.libbox.*

class EaglePlatform(private val service: VpnService) : PlatformInterface {

    override fun autoDetectInterfaceControl(fd: Int) {
        service.protect(fd)
    }

    override fun cancelNotification(id: String, type: Int) {}

    override fun checkPlatformShell() {
        throw UnsupportedOperationException("Platform shell is unavailable")
    }

    override fun clearDNSCache() {}

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun closeNeighborMonitor(listener: NeighborUpdateListener) {
        throw UnsupportedOperationException("Neighbor monitoring is unavailable")
    }

    override fun createBridge(options: BridgeOptions): BridgeSession {
        throw UnsupportedOperationException("Bridge is unavailable")
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int
    ): ConnectionOwner? = null

    override fun getInterfaces(): NetworkInterfaceIterator {
        val items = mutableListOf<io.nekohasekai.libbox.NetworkInterface>()
        val interfaces = JavaNetworkInterface.getNetworkInterfaces()
        while (interfaces != null && interfaces.hasMoreElements()) {
            val source = interfaces.nextElement()
            val item = io.nekohasekai.libbox.NetworkInterface()
            item.name = source.name
            item.index = source.index
            item.setMTU(source.mtu.coerceAtLeast(0))
            item.addresses = EagleStringIterator(
                source.interfaceAddresses.mapNotNull { address ->
                    val host = address.address?.hostAddress ?: return@mapNotNull null
                    "$host/${address.networkPrefixLength}"
                }.iterator()
            )
            item.setDNSServer(EagleStringIterator(emptyList<String>().iterator()))
            item.gateway = EagleStringIterator(emptyList<String>().iterator())
            item.flags = 0
            item.type = 0
            item.metered = false
            items.add(item)
        }
        return EagleNetworkInterfaceIterator(items.iterator())
    }

    override fun includeAllNetworks(): Boolean = false

    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun lookupSFTPServer(): String = ""

    override fun lookupUser(name: String): PlatformUser? = null

    override fun openShellSession(
        user: PlatformUser?,
        command: String,
        arguments: StringIterator,
        workingDirectory: String,
        uid: Int,
        gid: Int
    ): ShellSession {
        throw UnsupportedOperationException("Platform shell is unavailable")
    }

    override fun openTun(options: TunOptions): Int {
        val builder = service.Builder()
            .setSession("EAGLE VPN")
            .setMtu(options.getMTU().coerceIn(1280, 9000))

        if (Build.VERSION.SDK_INT >= 29) {
            builder.setMetered(false)
        }

        val addresses = options.inet4Address
        while (addresses.hasNext()) {
            val route = addresses.next()
            builder.addAddress(route.address(), route.prefix())
        }

        val addresses6 = options.inet6Address
        while (addresses6.hasNext()) {
            val route = addresses6.next()
            builder.addAddress(route.address(), route.prefix())
        }

        if (options.autoRoute) {
            if (Build.VERSION.SDK_INT >= 33) {
                val routes4 = options.inet4RouteAddress
                while (routes4.hasNext()) {
                    val route = routes4.next()
                    builder.addRoute(route.address(), route.prefix())
                }

                val routes6 = options.inet6RouteAddress
                while (routes6.hasNext()) {
                    val route = routes6.next()
                    builder.addRoute(route.address(), route.prefix())
                }

                val excluded4 = options.inet4RouteExcludeAddress
                while (excluded4.hasNext()) {
                    val route = excluded4.next()
                    builder.excludeRoute(android.net.IpPrefix(InetAddress.getByName(route.address()), route.prefix()))
                }

                val excluded6 = options.inet6RouteExcludeAddress
                while (excluded6.hasNext()) {
                    val route = excluded6.next()
                    builder.excludeRoute(android.net.IpPrefix(InetAddress.getByName(route.address()), route.prefix()))
                }
            } else {
                val routes4 = options.inet4RouteRange
                while (routes4.hasNext()) {
                    val route = routes4.next()
                    builder.addRoute(route.address(), route.prefix())
                }

                val routes6 = options.inet6RouteRange
                while (routes6.hasNext()) {
                    val route = routes6.next()
                    builder.addRoute(route.address(), route.prefix())
                }
            }
        }

        val dnsMode = options.getDNSMode().getValue()
        if (dnsMode != Libbox.DNSModeDisabled) {
            val dnsServers = options.getDNSServerAddress()
            while (dnsServers.hasNext()) {
                val address = dnsServers.next()
                if (address.isNotBlank()) {
                    try {
                        builder.addDnsServer(address)
                    } catch (e: IllegalArgumentException) {
                        Log.w("EaglePlatform", "Ignoring invalid DNS address: $address")
                    }
                }
            }
        }

        val includePackages = options.includePackage
        while (includePackages.hasNext()) {
            val name = includePackages.next()
            try {
                builder.addAllowedApplication(name)
            } catch (e: Exception) {
                Log.w("EaglePlatform", "Unable to allow package $name", e)
            }
        }

        val excludePackages = options.excludePackage
        while (excludePackages.hasNext()) {
            val name = excludePackages.next()
            try {
                builder.addDisallowedApplication(name)
            } catch (e: Exception) {
                Log.w("EaglePlatform", "Unable to exclude package $name", e)
            }
        }

        if (options.isHTTPProxyEnabled && Build.VERSION.SDK_INT >= 29) {
            val host = options.getHTTPProxyServer()
            val port = options.getHTTPProxyServerPort()
            if (host.isNotBlank() && port in 1..65535) {
                builder.setHttpProxy(ProxyInfo.buildDirectProxy(host, port))
            }
        }

        val descriptor = builder.establish()
            ?: throw IllegalStateException("Android refused to establish the VPN interface")

        return descriptor.detachFd()
    }

    override fun readSystemSSHHostKey(): String = ""

    override fun readWIFIState(): WIFIState = Libbox.newWIFIState("", "")

    override fun registerMyInterface(name: String) {}

    override fun sendNotification(notification: Notification) {}

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun startNeighborMonitor(listener: NeighborUpdateListener) {
        throw UnsupportedOperationException("Neighbor monitoring is unavailable")
    }

    override fun tailscaleHostname(): String = ""

    override fun underNetworkExtension(): Boolean = false

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun usePlatformBridge(): Boolean = false

    override fun usePlatformShell(): Boolean = false

    override fun useProcFS(): Boolean = false
}
