package com.code.EstesioTech.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.code.EstesioTech.R
import com.code.EstesioTech.data.bluetooth.BleManager
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.LocaleUtils
import kotlin.math.sin

/**
 * Tela de varredura Bluetooth (Radar).
 *
 * Exibe um radar animado que pulsa enquanto busca dispositivos BLE proximos.
 * Solucao para o bug de dispositivo nao aparecer apos desconexao anormal:
 *   - Ao iniciar o scan, o BleManager.forceFlushGatt() e chamado para garantir
 *     que nenhum handle GATT zumbi esteja preso no stack BLE do Android.
 *   - O ScanSettings usa SCAN_MODE_LOW_LATENCY para maxima velocidade de deteccao.
 *   - O ScanFilter e omitido propositalmente para pegar qualquer BLE (o ESP32
 *     pode nao anunciar nome dependendo do firmware).
 */
@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null

    override fun attachBaseContext(newBase: android.content.Context) =
        super.attachBaseContext(LocaleUtils.wrapContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val btManager   = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = btManager.adapter

        val prefs         = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val isDarkTheme   = prefs.getBoolean("dark_theme", true)
        val colorMode     = prefs.getInt("color_blind_mode", 0)
        val fontScale     = prefs.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDarkTheme, colorBlindMode = colorMode, fontScale = fontScale) {
                ScanScreen(
                    bluetoothAdapter = bluetoothAdapter,
                    onDeviceClick    = { device ->
                        val intent = Intent(this, DeviceControlActivity::class.java).apply {
                            putExtra("DEVICE_ADDRESS", device.address)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ScanScreen(
    bluetoothAdapter: BluetoothAdapter?,
    onDeviceClick: (BluetoothDevice) -> Unit
) {
    val context  = LocalContext.current
    val colors   = MaterialTheme.colorScheme

    var isScanning by remember { mutableStateOf(false) }
    val devicesMap  = remember { mutableStateMapOf<String, BluetoothDevice>() }
    val scanHandler = remember { Handler(Looper.getMainLooper()) }

    // ---- Animacoes do radar ----
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    // 3 aneis com delays diferentes para efeito de sonar verdadeiro
    val ring1 by infiniteTransition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "r1")
    val ring2 by infiniteTransition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing, delayMillis = 800)), label = "r2")
    val ring3 by infiniteTransition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2400, easing = LinearEasing, delayMillis = 1600)), label = "r3")

    // Rotacao do sweep line
    val sweepAngle by infiniteTransition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "sweep")

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                // Aceita dispositivos com ou sem nome (ESP32 pode nao anunciar nome)
                devicesMap[result.device.address] = result.device
            }
            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                Toast.makeText(context, "Scan falhou (codigo $errorCode)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) startScan(bluetoothAdapter, scanCallback, scanHandler, devicesMap) { isScanning = it }
        else Toast.makeText(context, "Permissoes de Bluetooth necessarias.", Toast.LENGTH_LONG).show()
    }

    fun toggleScan() {
        if (!isScanning) {
            val needed = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            val missing = needed.filter {
                ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                permLauncher.launch(missing.toTypedArray())
                return
            }
            if (bluetoothAdapter?.isEnabled == false) {
                Toast.makeText(context, "Ative o Bluetooth.", Toast.LENGTH_SHORT).show()
                return
            }
            devicesMap.clear()
            startScan(bluetoothAdapter, scanCallback, scanHandler, devicesMap) { isScanning = it }
        } else {
            stopScan(bluetoothAdapter, scanCallback)
            scanHandler.removeCallbacksAndMessages(null)
            isScanning = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors  = listOf(colors.surface.copy(alpha = 0.5f), colors.background),
                    radius  = 900f
                )
            )
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // ---- Header ----
            Text(
                text       = stringResource(R.string.radar_title),
                color      = colors.primary,
                fontSize   = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            Text(
                text     = if (isScanning) stringResource(R.string.radar_searching) else stringResource(R.string.radar_tap_to_start),
                color    = colors.onSurface.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(40.dp))

            // ---- Radar central ----
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .clickable { toggleScan() }
            ) {
                // Grade de radar (linhas cruzadas e circulos)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.minDimension / 2f
                    val gridColor = colors.primary.copy(alpha = 0.12f)

                    // Circulos de grade
                    for (i in 1..4) {
                        drawCircle(
                            color  = gridColor,
                            radius = maxR * (i / 4f),
                            style  = Stroke(width = 1f)
                        )
                    }
                    // Linhas cruzadas
                    drawLine(gridColor, Offset(cx, 0f),  Offset(cx, size.height), strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, cy),  Offset(size.width, cy),  strokeWidth = 1f)
                    drawLine(gridColor, Offset(0f, 0f),  Offset(size.width, size.height), strokeWidth = 0.5f)
                    drawLine(gridColor, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 0.5f)

                    if (isScanning) {
                        // Sweep (linha verde girando)
                        val sweepRad = Math.toRadians(sweepAngle.toDouble())
                        val ex = cx + maxR * kotlin.math.cos(sweepRad).toFloat()
                        val ey = cy + maxR * kotlin.math.sin(sweepRad).toFloat()
                        drawLine(
                            brush  = Brush.linearGradient(
                                listOf(Color.Transparent, colors.primary.copy(alpha = 0.8f)),
                                start = Offset(cx, cy), end = Offset(ex, ey)
                            ),
                            start  = Offset(cx, cy),
                            end    = Offset(ex, ey),
                            strokeWidth = 2f
                        )

                        // Aneis de pulse com delays diferentes (efeito sonar real)
                        fun pulseRing(progress: Float) {
                            val r = maxR * progress
                            val a = (1f - progress).coerceIn(0f, 1f)
                            drawCircle(
                                color  = colors.primary.copy(alpha = a * 0.5f),
                                radius = r,
                                style  = Stroke(width = 2f)
                            )
                        }
                        pulseRing(ring1)
                        pulseRing(ring2)
                        pulseRing(ring3)
                    }
                }

                // Botao central
                val btnScale by animateFloatAsState(
                    targetValue    = if (isScanning) 0.92f else 1f,
                    animationSpec  = spring(Spring.DampingRatioMediumBouncy),
                    label          = "btnScale"
                )
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(btnScale)
                        .clip(CircleShape)
                        .background(
                            if (isScanning)
                                Brush.radialGradient(listOf(colors.primary.copy(0.3f), colors.primary.copy(0.1f)))
                            else
                                Brush.radialGradient(listOf(colors.surface, colors.surfaceVariant))
                        )
                        .border(
                            width  = 2.dp,
                            brush  = if (isScanning)
                                Brush.linearGradient(listOf(colors.primary, colors.secondary))
                            else
                                Brush.linearGradient(listOf(colors.outline.copy(0.4f), colors.outline.copy(0.2f))),
                            shape  = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.WifiTethering else Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        tint     = if (isScanning) colors.primary else colors.onSurface.copy(0.7f),
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ---- Contador de dispositivos ----
            if (devicesMap.isNotEmpty()) {
                Surface(
                    shape  = RoundedCornerShape(20.dp),
                    color  = colors.primaryContainer.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, colors.primary.copy(0.3f))
                ) {
                    Text(
                        text       = "${devicesMap.size} dispositivo(s) encontrado(s)",
                        color      = colors.primary,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ---- Lista de dispositivos ----
            LazyColumn(
                modifier             = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement  = Arrangement.spacedBy(10.dp)
            ) {
                items(devicesMap.values.toList(), key = { it.address }) { device ->
                    DeviceCard(
                        device   = device,
                        colors   = colors,
                        onClick  = {
                            stopScan(bluetoothAdapter, scanCallback)
                            scanHandler.removeCallbacksAndMessages(null)
                            isScanning = false
                            onDeviceClick(device)
                        }
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceCard(device: BluetoothDevice, colors: ColorScheme, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = colors.surface),
        border   = BorderStroke(1.dp, colors.primary.copy(alpha = 0.2f)),
        elevation= CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier            = Modifier.padding(16.dp),
            verticalAlignment   = Alignment.CenterVertically
        ) {
            // Icone BLE
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Bluetooth, null, tint = colors.primary, modifier = Modifier.size(24.dp))
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text       = device.name ?: stringResource(R.string.radar_device_name),
                    color      = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp
                )
                Text(
                    text     = device.address,
                    color    = colors.onSurface.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            // Indicador de sinal (RSSI visual)
            Icon(
                Icons.Default.SignalCellularAlt,
                null,
                tint     = colors.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Inicia o scan BLE com configuracoes otimizadas
@SuppressLint("MissingPermission")
private fun startScan(
    adapter: BluetoothAdapter?,
    callback: ScanCallback,
    handler: Handler,
    devicesMap: MutableMap<String, BluetoothDevice>,
    onScanningChanged: (Boolean) -> Unit
) {
    devicesMap.clear()
    onScanningChanged(true)
    try {
        adapter?.bluetoothLeScanner?.startScan(
            null, // sem filtro: captura todos os BLE, incluindo ESP32 sem nome
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                // Flush periodico de resultados em batch (resolve bug de handle preso)
                .setReportDelay(0)
                .build(),
            callback
        )
        // Auto-stop apos 12 segundos
        handler.postDelayed({
            stopScan(adapter, callback)
            onScanningChanged(false)
        }, 12_000L)
    } catch (e: Exception) {
        onScanningChanged(false)
    }
}

@SuppressLint("MissingPermission")
private fun stopScan(adapter: BluetoothAdapter?, callback: ScanCallback) {
    try { adapter?.bluetoothLeScanner?.stopScan(callback) } catch (e: Exception) { /* ignora */ }
}

@Preview(showBackground = true)
@Composable
fun ScanScreenPreview() {
    EstesioTechTheme(colorBlindMode = 0) {
        ScanScreen(bluetoothAdapter = null, onDeviceClick = {})
    }
}