package com.eagle.vpn

import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator

class EagleNetworkInterfaceIterator(
    private val iterator: Iterator<NetworkInterface>
) : NetworkInterfaceIterator {
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): NetworkInterface = iterator.next()
}
