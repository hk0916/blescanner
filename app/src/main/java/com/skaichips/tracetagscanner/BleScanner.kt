package com.skaichips.tracetagscanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BLE 스캔을 관리하는 클래스.
 *
 * 필터링은 ScanFilter(하드웨어 레벨)가 아니라 소프트웨어에서 처리한다.
 * 이유:
 *  - prefix(접두사) 필터는 ScanFilter가 지원하지 않음 (전체 MAC만 가능)
 *  - 모든 기기를 일단 받아서 화면에서 유연하게 거르는 편이 테스트 용도에 적합
 *
 * 즉 "raw 스캔 → 소프트웨어 필터" 방식.
 */
class BleScanner(
    private val adapter: BluetoothAdapter?
) {
    private val scanner: BluetoothLeScanner?
        get() = adapter?.bluetoothLeScanner

    // 주소 -> 기기. 누적 상태 유지용.
    private val deviceMap = LinkedHashMap<String, ScannedDevice>()

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleResult(result: ScanResult) {
        val address = result.device.address ?: return
        val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
        val now = System.currentTimeMillis()

        val existing = deviceMap[address]
        deviceMap[address] = ScannedDevice(
            address = address,
            name = name ?: existing?.name,
            rssi = result.rssi,
            lastSeen = now,
            sampleCount = (existing?.sampleCount ?: 0) + 1
        )
        _devices.value = deviceMap.values.toList()
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val s = scanner ?: return
        if (_isScanning.value) return

        // 저지연 모드 — 신호 테스트에는 빠른 갱신이 중요
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // ScanFilter는 비워둔다 (전체 스캔 후 소프트웨어 필터)
        try {
            s.startScan(null, settings, callback)
            _isScanning.value = true
        } catch (e: SecurityException) {
            Log.e(TAG, "startScan permission error", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val s = scanner ?: return
        if (!_isScanning.value) return
        try {
            s.stopScan(callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "stopScan permission error", e)
        }
        _isScanning.value = false
    }

    /** 누적된 기기 목록 초기화 */
    fun clear() {
        deviceMap.clear()
        _devices.value = emptyList()
    }

    companion object {
        private const val TAG = "BleScanner"
    }
}
