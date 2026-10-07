package com.eagle.vpn

import io.nekohasekai.libbox.StatusMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EagleState {
    data class UiState(
        val connected: Boolean = false,
        val connecting: Boolean = false,
        val configReady: Boolean = false,
        val configName: String = "No configuration",
        val startedAt: Long = 0L,
        val upload: Long = 0L,
        val download: Long = 0L,
        val uploadTotal: Long = 0L,
        val downloadTotal: Long = 0L,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun update(block: (UiState) -> UiState) {
        _state.value = block(_state.value)
    }

    fun updateStatus(status: StatusMessage) {
        _state.value = _state.value.copy(
            upload = status.uplink,
            download = status.downlink,
            uploadTotal = status.uplinkTotal,
            downloadTotal = status.downlinkTotal
        )
    }

    fun reset() {
        _state.value = UiState()
    }
}
