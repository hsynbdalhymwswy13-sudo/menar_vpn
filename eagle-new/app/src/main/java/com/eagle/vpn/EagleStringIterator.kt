package com.eagle.vpn

import io.nekohasekai.libbox.StringIterator

class EagleStringIterator(private val iterator: Iterator<String>) : StringIterator {
    override fun hasNext(): Boolean = iterator.hasNext()
    override fun next(): String = iterator.next()
    override fun len(): Int = 0
}
