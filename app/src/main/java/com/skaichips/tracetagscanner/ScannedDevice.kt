package com.skaichips.tracetagscanner

/**
 * 스캔으로 감지된 BLE 기기 한 건을 표현하는 모델.
 *
 * @param address  기기 MAC 주소 (예: "AC:23:3F:11:22:33")
 * @param name     광고에 담긴 기기 이름 (없으면 null)
 * @param rssi     최근 측정된 신호 세기 (dBm, 보통 -30 ~ -100)
 * @param lastSeen 마지막으로 감지된 시각 (epoch millis)
 * @param sampleCount 이 기기가 감지된 누적 횟수
 */
data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val lastSeen: Long,
    val sampleCount: Int
) {
    /**
     * RSSI를 0~4 단계의 신호 막대 수준으로 변환.
     * 신호세기 시각화에 사용.
     */
    val signalLevel: Int
        get() = when {
            rssi >= -55 -> 4
            rssi >= -67 -> 3
            rssi >= -78 -> 2
            rssi >= -90 -> 1
            else -> 0
        }
}
