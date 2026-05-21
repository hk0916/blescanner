package com.skaichips.tracetagscanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val viewModel: ScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TraceTagTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScannerScreen(viewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopScan()
    }
}

/** 이 기기에서 BLE 스캔에 필요한 권한 목록 (OS 버전별로 다름) */
private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(viewModel: ScannerViewModel) {
    val context = LocalContext.current

    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val filterMode by viewModel.filterMode.collectAsStateWithLifecycle()
    val filterText by viewModel.filterText.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(checkPermissions(context))
    }
    var btEnabled by remember { mutableStateOf(isBluetoothOn(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
        if (hasPermission && btEnabled) viewModel.startScan()
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        btEnabled = isBluetoothOn(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TraceTag Scanner", fontWeight = FontWeight.Bold)
                        Text(
                            "BLE 신호 테스트 · Skaichips",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (!hasPermission) {
                                permissionLauncher.launch(requiredPermissions())
                                return@Button
                            }
                            if (!btEnabled) {
                                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                return@Button
                            }
                            viewModel.toggleScan()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            if (isScanning) "스캔 정지" else "스캔 시작",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::clear,
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("비우기")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            FilterControls(
                filterMode = filterMode,
                filterText = filterText,
                onModeChange = viewModel::setFilterMode,
                onTextChange = viewModel::setFilterText
            )

            Spacer(Modifier.height(12.dp))

            // 스캔 상태 요약줄
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusColor by animateColorAsState(
                    if (isScanning) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "status"
                )
                Text(
                    if (isScanning) "● 스캔 중" else "○ 정지됨",
                    color = statusColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "감지: ${devices.size}대",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // 권한 / BT 상태 안내 배너
            if (!hasPermission || !btEnabled) {
                StatusBanner(
                    hasPermission = hasPermission,
                    btEnabled = btEnabled,
                    onRequestPermission = {
                        permissionLauncher.launch(requiredPermissions())
                    },
                    onEnableBt = {
                        enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            // 기기 리스트
            Box(modifier = Modifier.weight(1f)) {
                if (devices.isEmpty()) {
                    EmptyState(isScanning, filterText.isNotBlank())
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(devices, key = { it.address }) { dev ->
                            DeviceCard(dev)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterControls(
    filterMode: FilterMode,
    filterText: String,
    onModeChange: (FilterMode) -> Unit,
    onTextChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        // 모드 선택 (세그먼트)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val modes = listOf(
                FilterMode.NONE to "전체",
                FilterMode.PREFIX to "Prefix",
                FilterMode.FULL to "전체 MAC"
            )
            modes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = filterMode == mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                ) { Text(label, fontSize = 13.sp) }
            }
        }

        if (filterMode != FilterMode.NONE) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = filterText,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = filterMode == FilterMode.PREFIX,
                label = {
                    Text(
                        if (filterMode == FilterMode.PREFIX)
                            "MAC 접두사 (예: AC:23:3F)"
                        else
                            "전체 MAC (콤마/줄바꿈으로 여러 개)"
                    )
                },
                placeholder = {
                    Text(
                        if (filterMode == FilterMode.PREFIX) "AC:23:3F"
                        else "AC:23:3F:11:22:33",
                        fontFamily = FontFamily.Monospace
                    )
                },
                trailingIcon = {
                    if (filterText.isNotEmpty()) {
                        IconButton(onClick = { onTextChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "지우기")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                )
            )
        }
    }
}

@Composable
private fun DeviceCard(dev: ScannedDevice) {
    val secsAgo = TimeUnit.MILLISECONDS.toSeconds(
        System.currentTimeMillis() - dev.lastSeen
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SignalBars(dev.signalLevel)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dev.name ?: "(이름 없음)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    dev.address,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${secsAgo}초 전 · ${dev.sampleCount}회 감지",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${dev.rssi}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = rssiColor(dev.rssi)
                )
                Text("dBm", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SignalBars(level: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        val heights = listOf(8.dp, 13.dp, 18.dp, 23.dp)
        heights.forEachIndexed { i, h ->
            val active = i < level
            Box(
                modifier = Modifier
                    .padding(end = 2.dp)
                    .width(5.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.dp))
                    .then(
                        Modifier.background(
                            if (active) signalColorForLevel(level)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    )
            )
        }
    }
}

private fun rssiColor(rssi: Int): Color = when {
    rssi >= -67 -> Color(0xFF2E7D32)   // 양호 (초록)
    rssi >= -80 -> Color(0xFFF9A825)   // 보통 (노랑)
    else -> Color(0xFFC62828)          // 약함 (빨강)
}

private fun signalColorForLevel(level: Int): Color = when {
    level >= 3 -> Color(0xFF2E7D32)
    level == 2 -> Color(0xFFF9A825)
    else -> Color(0xFFC62828)
}

@Composable
private fun StatusBanner(
    hasPermission: Boolean,
    btEnabled: Boolean,
    onRequestPermission: () -> Unit,
    onEnableBt: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when {
                !hasPermission -> {
                    Text(
                        "블루투스 스캔 권한이 필요합니다.",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestPermission) { Text("권한 허용") }
                }
                !btEnabled -> {
                    Text(
                        "블루투스가 꺼져 있습니다.",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onEnableBt) { Text("블루투스 켜기") }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(isScanning: Boolean, hasFilter: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                isScanning && hasFilter -> "필터에 맞는 태그를 찾는 중…"
                isScanning -> "신호를 찾는 중…"
                else -> "스캔을 시작하세요"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── 헬퍼 ────────────────────────────────────────────────

private fun checkPermissions(context: Context): Boolean =
    requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun isBluetoothOn(context: Context): Boolean {
    val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return mgr?.adapter?.isEnabled == true
}
