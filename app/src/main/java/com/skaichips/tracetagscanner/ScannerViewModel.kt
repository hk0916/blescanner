package com.skaichips.tracetagscanner

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 필터 모드.
 *  - PREFIX : MAC 접두사로 거름 (예: "AC:23:3F")
 *  - FULL   : 전체 MAC 일치 (콤마/공백/줄바꿈으로 여러 개 가능)
 *  - NONE   : 필터 없음 (전체 표시)
 */
enum class FilterMode { NONE, PREFIX, FULL }

class ScannerViewModel(app: Application) : AndroidViewModel(app) {

    private val scanner: BleScanner = run {
        val mgr = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        BleScanner(mgr?.adapter)
    }

    val isScanning: StateFlow<Boolean> = scanner.isScanning

    private val _filterMode = MutableStateFlow(FilterMode.PREFIX)
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()

    private val _filterText = MutableStateFlow("")
    val filterText: StateFlow<String> = _filterText.asStateFlow()

    /**
     * 필터가 적용된, RSSI 내림차순(가까운 순) 정렬된 기기 목록.
     */
    val devices: StateFlow<List<ScannedDevice>> =
        combine(
            scanner.devices,
            _filterMode,
            _filterText
        ) { list, mode, text ->
            applyFilter(list, mode, text).sortedByDescending { it.rssi }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private fun applyFilter(
        list: List<ScannedDevice>,
        mode: FilterMode,
        text: String
    ): List<ScannedDevice> {
        val query = text.trim().uppercase()
        if (mode == FilterMode.NONE || query.isEmpty()) return list

        return when (mode) {
            FilterMode.PREFIX -> {
                // 콜론 유무 둘 다 허용 (AC:23 / AC23 모두 매칭)
                val norm = query.replace(" ", "")
                list.filter { dev ->
                    val addr = dev.address.uppercase()
                    addr.startsWith(norm) ||
                        addr.replace(":", "").startsWith(norm.replace(":", ""))
                }
            }
            FilterMode.FULL -> {
                // 여러 MAC을 콤마/공백/줄바꿈으로 구분해 입력 가능
                val targets = query.split(",", " ", "\n", "\t")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { it.replace(":", "") }
                    .toSet()
                list.filter { dev ->
                    dev.address.uppercase().replace(":", "") in targets
                }
            }
            FilterMode.NONE -> list
        }
    }

    fun setFilterMode(mode: FilterMode) { _filterMode.value = mode }
    fun setFilterText(text: String) { _filterText.value = text }

    fun startScan() = scanner.startScan()
    fun stopScan() = scanner.stopScan()
    fun clear() = scanner.clear()

    fun toggleScan() {
        if (isScanning.value) scanner.stopScan() else scanner.startScan()
    }

    override fun onCleared() {
        super.onCleared()
        scanner.stopScan()
    }
}
