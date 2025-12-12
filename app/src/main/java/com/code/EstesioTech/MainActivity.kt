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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.code.EstesioTech.ui.theme.EstesioTechTheme

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setContent {
            EstesioTechTheme {
                ScanScreen(
                    bluetoothAdapter = bluetoothAdapter,
                    onDeviceClick = { device ->
                        // Para o scan antes de conectar para evitar conflitos
                        try {
                            // Não precisamos parar explicitamente aqui se a lógica do scan já cuidar disso,
                            // mas é bom garantir.
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
    var isScanning by remember { mutableStateOf(false) }
    val foundDevicesMap = remember { mutableStateMapOf<String, BluetoothDevice>() }

    // Animação do Radar
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

    // Gerenciador de Permissões
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Se todas as permissões foram aceitas, tenta escanear
        if (permissions.values.all { it }) {
            Toast.makeText(context, "Permissões concedidas. Toque no radar novamente.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissões necessárias para Bluetooth.", Toast.LENGTH_LONG).show()
        }
    }

    fun startScanningLogic() {
        // Verifica permissões ANTES de chamar startScan
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missingPermissions = permissions.filter {
            ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            // Se falta permissão, PEDE e não escaneia ainda
            permissionsLauncher.launch(missingPermissions.toTypedArray())
            return
        }

        // Se tem permissão, começa
        if (bluetoothAdapter?.isEnabled == false) {
            Toast.makeText(context, "Ative o Bluetooth do celular.", Toast.LENGTH_SHORT).show()
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
            // Timeout de 10s
            Handler(Looper.getMainLooper()).postDelayed({
                if(isScanning) {
                    try { bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback) } catch(e:Exception){}
                    isScanning = false
                }
            }, 10000)
        } catch (e: SecurityException) {
            Toast.makeText(context, "Erro de permissão de segurança.", Toast.LENGTH_SHORT).show()
            isScanning = false
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao iniciar scan: ${e.message}", Toast.LENGTH_SHORT).show()
            isScanning = false
        }
    }

    fun stopScanningLogic() {
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            // Ignora erro se já estiver parado
        }
        isScanning = false
    }

    fun toggleScan() {
        if (!isScanning) startScanningLogic() else stopScanningLogic()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF0F2027), Color(0xFF2C5364))))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("RADAR BLUETOOTH", color = Color(0xFF00ACC1), fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp).clickable { toggleScan() }) {
            if (isScanning) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = Color(0xFF00ACC1).copy(alpha = alphaAnim), radius = size.minDimension / 2 * scale, style = Stroke(width = 4f))
                }
            }
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(if(isScanning) Color(0xFF00ACC1).copy(alpha=0.2f) else Color.Black.copy(alpha=0.3f))
                    .border(2.dp, Color(0xFF00ACC1), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.BluetoothSearching, null, tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(if (isScanning) "Buscando Estesiômetro..." else "Toque no radar para iniciar", color = Color.Gray)
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634).copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00ACC1).copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, null, tint = Color(0xFF00ACC1))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(device.name ?: "Dispositivo Desconhecido", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(device.address, color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun ScanScreenPreview() {
    com.code.EstesioTech.ui.theme.EstesioTechTheme {
        // Passamos null no adapter pois o preview não tem bluetooth real
        ScanScreen(bluetoothAdapter = null, onDeviceClick = {})
    }
}