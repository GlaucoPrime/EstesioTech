package com.code.EstesioTech.ui.screens

import android.content.Context
import com.code.EstesioTech.ui.screens.SelectionActivity
import com.code.EstesioTech.ui.screens.SettingsActivity
import com.code.EstesioTech.ui.screens.LoginActivity
import com.code.EstesioTech.ui.screens.MainActivity
import android.content.Intent
import com.code.EstesioTech.HistoryActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.code.EstesioTech.R
import com.code.EstesioTech.data.bluetooth.BleManager
import com.code.EstesioTech.data.cloud.EstesioCloud
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.CpfVisualTransformation
import com.code.EstesioTech.utils.LocaleUtils
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tela principal do app apos login.
 *
 * Exibe:
 *  - Status de conexao BLE com o Estesiometro (atualizado a cada 1.5s)
 *  - Acoes rapidas: Novo Exame e Historico
 *  - Lista dos 3 ultimos pacientes atendidos
 *
 * Diferencas da versao anterior:
 *  - Usa LocaleUtils.wrapContext para locale correto
 *  - hasSettingsChanged() verifica lazy sem causar recreate em loop
 *  - Dialog de busca de paciente com feedback visual melhorado
 */
class HomeActivity : ComponentActivity() {

    private var lastLocale: String?   = null
    private var lastTheme: Boolean    = true
    private var lastColorMode: Int    = 0
    private var lastFontScale: Float  = 1.0f

    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(LocaleUtils.wrapContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        saveCurrentSettings()
        loadUI()
    }

    override fun onResume() {
        super.onResume()
        if (hasSettingsChanged()) recreate()
        else saveCurrentSettings()
    }

    private fun saveCurrentSettings() {
        val p = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        lastLocale    = p.getString("language", "pt")
        lastTheme     = p.getBoolean("dark_theme", true)
        lastColorMode = p.getInt("color_blind_mode", 0)
        lastFontScale = p.getFloat("font_scale", 1.0f)
    }

    private fun hasSettingsChanged(): Boolean {
        val p = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        return lastLocale    != p.getString("language", "pt")  ||
                lastTheme     != p.getBoolean("dark_theme", true) ||
                lastColorMode != p.getInt("color_blind_mode", 0)  ||
                lastFontScale != p.getFloat("font_scale", 1.0f)
    }

    private fun loadUI() {
        val p           = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val isDark      = p.getBoolean("dark_theme", true)
        val colorMode   = p.getInt("color_blind_mode", 0)
        val fontScale   = p.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDark, colorBlindMode = colorMode, fontScale = fontScale) {
                HomeScreen(
                    onNavigateToSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onLogout = {
                        EstesioCloud.logout()
                        startActivity(Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                        finish()
                    },
                    onNavigateToSelection = { name, cpf ->
                        startActivity(Intent(this, SelectionActivity::class.java).apply {
                            putExtra("PATIENT_NAME", name)
                            putExtra("PATIENT_CPF", cpf)
                        })
                    },
                    onNavigateToHistory = { startActivity(Intent(this, HistoryActivity::class.java)) },
                    onNavigateToDevice  = { startActivity(Intent(this, MainActivity::class.java)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToSelection: (String, String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDevice: () -> Unit
) {
    var doctorName       by remember { mutableStateOf("") }
    var showCpfDialog    by remember { mutableStateOf(false) }
    var showNewPatient   by remember { mutableStateOf(false) }
    var showOffline      by remember { mutableStateOf(false) }
    var tempCpf          by remember { mutableStateOf("") }
    var isSearching      by remember { mutableStateOf(false) }
    var isDeviceConn     by remember { mutableStateOf(false) }
    var recentPatients   by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    val colors  = MaterialTheme.colorScheme
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        EstesioCloud.getUserName { name -> doctorName = name }
        EstesioCloud.getRecentPatients(limit = 3, onSuccess = { recentPatients = it }, onError = {})
        // Poll BLE status a cada 1.5 segundos
        while (true) {
            isDeviceConn = try { BleManager.isConnected() } catch (e: Exception) { false }
            delay(1500L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar com inicial do medico
                        Box(
                            modifier         = Modifier.size(42.dp).clip(CircleShape)
                                .background(colors.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text       = (doctorName.firstOrNull() ?: "D").toString().uppercase(),
                                color      = colors.onPrimaryContainer,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize   = 18.sp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text       = "${stringResource(R.string.welcome)} ${doctorName.ifEmpty { "..." }}",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "EstesioTech Pro",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface.copy(0.55f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, null, tint = colors.onSurface)
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, null, tint = colors.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ---- Card de Status BLE ----
            BleStatusCard(
                isConnected       = isDeviceConn,
                colors            = colors,
                onConnect         = { if (isDeviceConn) { BleManager.disconnect(); isDeviceConn = false } else onNavigateToDevice() }
            )

            // ---- Acoes rapidas ----
            Text(
                stringResource(R.string.quick_actions),
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = colors.onBackground
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ActionCard(
                    title    = stringResource(R.string.new_exam),
                    icon     = Icons.Default.Add,
                    gradient = Brush.linearGradient(listOf(colors.primary, colors.primary.copy(0.7f))),
                    modifier = Modifier.weight(1f),
                    onClick  = { showCpfDialog = true }
                )
                ActionCard(
                    title    = stringResource(R.string.history),
                    icon     = Icons.Default.History,
                    gradient = Brush.linearGradient(listOf(colors.secondary, colors.secondary.copy(0.7f))),
                    modifier = Modifier.weight(1f),
                    onClick  = onNavigateToHistory
                )
            }

            // ---- Pacientes recentes ----
            if (recentPatients.isNotEmpty()) {
                Text(
                    stringResource(R.string.recent_patients),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = colors.onBackground
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    recentPatients.forEach { patient ->
                        val pName = patient["name"] as? String ?: "Paciente"
                        val pCpf  = patient["cpfHash"] as? String ?: (patient["cpf"] as? String ?: "")
                        val pDate = patient["lastExam"] as? java.util.Date ?: java.util.Date()
                        RecentPatientRow(pName, pDate, colors) {
                            onNavigateToSelection(pName, pCpf)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ---- Dialog: Busca de CPF ----
    if (showCpfDialog) {
        CpfSearchDialog(
            isSearching = isSearching,
            onDismiss   = { showCpfDialog = false; tempCpf = "" },
            onSearch    = { cpf ->
                tempCpf      = cpf
                isSearching  = true
                EstesioCloud.checkPatient(cpf,
                    onFound = { name, _, _ ->
                        isSearching    = false
                        showCpfDialog  = false
                        onNavigateToSelection(name, cpf)
                    },
                    onNotFound = {
                        isSearching   = false
                        showCpfDialog = false
                        showNewPatient= true
                    },
                    onError = {
                        isSearching   = false
                        showCpfDialog = false
                        showOffline   = true
                    }
                )
            }
        )
    }

    // ---- Dialog: Offline ----
    if (showOffline) {
        AlertDialog(
            onDismissRequest = { showOffline = false },
            title   = { Text("Sem Conexao", color = colors.error, fontWeight = FontWeight.Bold) },
            text    = { Text("Nao foi possivel verificar o paciente. Iniciar exame offline?") },
            confirmButton = {
                Button(onClick = { showOffline = false; onNavigateToSelection("Paciente Offline", tempCpf) }) {
                    Text("Sim, Offline")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOffline = false }) { Text("Cancelar") }
            },
            containerColor = colors.surface
        )
    }

    // ---- Dialog: Novo Paciente ----
    if (showNewPatient) {
        NewPatientDialog(
            initialCpf = tempCpf,
            onDismiss  = { showNewPatient = false },
            onConfirm  = { name, cpf, age ->
                EstesioCloud.createPatient(cpf, name, age, "",
                    onSuccess = { showNewPatient = false; onNavigateToSelection(name, cpf) },
                    onError   = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                )
            }
        )
    }
}

// ---- Subcomposables ----

@Composable
private fun BleStatusCard(isConnected: Boolean, colors: ColorScheme, onConnect: () -> Unit) {
    val cardColor = if (isConnected) Brush.linearGradient(listOf(Color(0xFF1B5E20), Color(0xFF2E7D32)))
    else Brush.linearGradient(listOf(colors.surfaceVariant, colors.surfaceVariant))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardColor)
    ) {
        Row(
            modifier            = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "Estesiometro",
                    fontSize = 12.sp,
                    color    = if (isConnected) Color.White.copy(0.75f) else colors.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                        null,
                        tint     = if (isConnected) Color.White else colors.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isConnected) "Conectado" else "Nao Conectado",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = if (isConnected) Color.White else colors.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onConnect,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) Color.White else colors.primary,
                    contentColor   = if (isConnected) Color(0xFF2E7D32) else colors.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (isConnected) "Desconectar" else "Conectar",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: Brush,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier  = modifier.height(150.dp).clickable { onClick() },
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            // Gradiente sutil de fundo
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(colors.surface, colors.surfaceVariant.copy(0.3f))
            )))
            Column(
                modifier  = Modifier.padding(18.dp).fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier         = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(gradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
            }
        }
    }
}

@Composable
private fun RecentPatientRow(name: String, date: Date, colors: ColorScheme, onClick: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("dd/MM", Locale.getDefault()) }
    Surface(
        modifier  = Modifier.fillMaxWidth().clickable { onClick() },
        shape     = RoundedCornerShape(14.dp),
        color     = colors.surface,
        border    = BorderStroke(1.dp, colors.outline.copy(0.1f)),
        tonalElevation = 2.dp
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier         = Modifier.size(40.dp).clip(CircleShape).background(colors.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = colors.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = colors.onSurface, modifier = Modifier.weight(1f))
            Text(dateFmt.format(date), fontSize = 13.sp, color = colors.primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CpfSearchDialog(isSearching: Boolean, onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    var cpf    = remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(colors.surface), shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PersonSearch, null, tint = colors.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("Buscar Paciente", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Digite o CPF do paciente", color = colors.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value         = cpf.value,
                    onValueChange = { if (it.filter(Char::isDigit).length <= 11) cpf.value = it.filter(Char::isDigit) },
                    label         = { Text(stringResource(R.string.cpf_hint)) },
                    visualTransformation = CpfVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick  = { if (cpf.value.isNotEmpty()) onSearch(cpf.value) },
                    enabled  = !isSearching,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(12.dp)
                ) {
                    if (isSearching) CircularProgressIndicator(color = colors.onPrimary, modifier = Modifier.size(20.dp))
                    else Text("CONTINUAR", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPatientDialog(initialCpf: String, onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name = remember { mutableStateOf("") }
    var cpf  = remember { mutableStateOf(initialCpf) }
    var age  = remember { mutableStateOf("") }
    val colors  = MaterialTheme.colorScheme
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(colors.surface)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PersonAdd, null, tint = colors.primary, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.new_patient_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(value = name.value, onValueChange = { name.value = it }, label = { Text(stringResource(R.string.name_hint)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = cpf.value,
                    onValueChange = { if (it.filter(Char::isDigit).length <= 11) cpf.value = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.cpf_hint)) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CpfVisualTransformation(), singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = age.value, onValueChange = { age.value = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.age_hint)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Spacer(Modifier.height(24.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.cancel)) }
                    Button(
                        onClick = {
                            if (name.value.isNotBlank() && cpf.value.length == 11) onConfirm(name.value, cpf.value, age.value)
                            else Toast.makeText(context, "Preencha nome e CPF corretamente.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.start), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    EstesioTechTheme(darkTheme = true) {
        HomeScreen({}, {}, { _, _ -> }, {}, {})
    }
}