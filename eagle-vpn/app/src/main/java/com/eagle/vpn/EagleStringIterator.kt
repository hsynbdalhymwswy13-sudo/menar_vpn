package com.eagle.vpn

import io.nekohasekai.libbox.StringIterator

class EagleStringIterator(
    private val values: List<String>
) : StringIterator {
    private var index = 0

    override fun hasNext(): Boolean = index < values.size

    override fun len(): Int = values.size

    override fun next(): String = values[index++]
}
