package com.eagle.vpn

data class EagleProfile(
    val id: String,
    val name: String,
    val address: String,
    val config: String,
    val ping: Long = 0L,
    val isFavorite: Boolean = false
)
