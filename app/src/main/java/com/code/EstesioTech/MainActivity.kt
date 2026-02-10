package com.code.EstesioTech

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.code.EstesioTech.ui.theme.EstesioTechTheme

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LocaleUtils.setLocale(this) // Aplica Idioma

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val prefs = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", true)
        val colorBlindMode = prefs.getInt("color_blind_mode", 0)
        val fontScale = prefs.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDarkTheme, colorBlindMode = colorBlindMode, fontScale = fontScale) {
                ScanScreen(
                    bluetoothAdapter = bluetoothAdapter,
                    onDeviceClick = { device ->
                        try {
                            // Lógica de parar scan se necessário
                        } catch (e: Exception) { e.printStackTrace() }

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
fun ScanScreen(bluetoothAdapter: BluetoothAdapter?, onDeviceClick: (BluetoothDevice) -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    var isScanning by remember { mutableStateOf(false) }
    val foundDevicesMap = remember { mutableStateMapOf<String, BluetoothDevice>() }

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "scale"
    )
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), label = "alpha"
    )

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.name != null) foundDevicesMap[result.device.address] = result.device
            }
            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                Toast.makeText(context, "Falha no Scan: $errorCode", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            Toast.makeText(context, "Permissões concedidas.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissões necessárias.", Toast.LENGTH_LONG).show()
        }
    }

    fun startScanningLogic() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (permissions.any { ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
            permissionsLauncher.launch(permissions.toTypedArray())
            return
        }

        if (bluetoothAdapter?.isEnabled == false) {
            Toast.makeText(context, "Ative o Bluetooth.", Toast.LENGTH_SHORT).show()
            return
        }

        foundDevicesMap.clear()
        isScanning = true
        try {
            bluetoothAdapter?.bluetoothLeScanner?.startScan(
                null,
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                scanCallback
            )
            Handler(Looper.getMainLooper()).postDelayed({
                if(isScanning) {
                    try { bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch(e:Exception){}
                    isScanning = false
                }
            }, 10000)
        } catch (e: Exception) {
            isScanning = false
            Toast.makeText(context, "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopScanningLogic() {
        try { bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch (e: Exception) {}
        isScanning = false
    }

    fun toggleScan() {
        if (!isScanning) startScanningLogic() else stopScanningLogic()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(colors.background, colors.surface)))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("RADAR BLUETOOTH", color = colors.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp).clickable { toggleScan() }) {
            if (isScanning) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = colors.primary.copy(alpha = alphaAnim), radius = size.minDimension / 2 * scale, style = Stroke(width = 4f))
                }
            }
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(if(isScanning) colors.primary.copy(alpha=0.2f) else colors.onBackground.copy(alpha=0.1f))
                    .border(2.dp, if(isScanning) colors.primary else colors.onBackground.copy(alpha=0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.BluetoothSearching, null, tint = if(isScanning) colors.primary else colors.onBackground, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(if (isScanning) "Buscando..." else "Toque para iniciar", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface.copy(alpha=0.7f))
        Spacer(modifier = Modifier.height(32.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(foundDevicesMap.values.toList()) { device ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        stopScanningLogic()
                        onDeviceClick(device)
                    },
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.primary.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, null, tint = colors.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(device.name ?: "Dispositivo", color = colors.onSurface, fontWeight = FontWeight.Bold)
                            Text(device.address, color = colors.onSurface.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ScanScreenPreview() {
    com.code.EstesioTech.ui.theme.EstesioTechTheme(colorBlindMode = 0) {
        ScanScreen(bluetoothAdapter = null, onDeviceClick = {})
    }
}