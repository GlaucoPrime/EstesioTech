package com.code.EstesioTech.ui.screens

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.code.EstesioTech.ClinicalScale
import com.code.EstesioTech.R
import com.code.EstesioTech.data.bletooth.BleManager
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.LocaleUtils

class TesteActivity : ComponentActivity(), BleManager.ConnectionListener {

    private val activePointIndex = mutableIntStateOf(-1)
    private val currentBleValue = mutableIntStateOf(0)
    private var lastValidValue = 0
    private val resultsMap = mutableStateMapOf<Int, Int>()
    private val isBleConnected = mutableStateOf(false)

    private var currentBodyPart = ""
    private var patientCpf = ""
    private var patientName = ""
    private var sessionId = ""

    private val isSaving = mutableStateOf(false)
    private val showResetReminder = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleUtils.setLocale(this)

        currentBodyPart = intent.getStringExtra("BODY_PART") ?: "mao_direita"
        patientCpf = intent.getStringExtra("PATIENT_CPF") ?: ""
        patientName = intent.getStringExtra("PATIENT_NAME") ?: "Desconhecido"
        sessionId = intent.getStringExtra("SESSION_ID") ?: ""

        BleManager.setListener(this)
        isBleConnected.value = BleManager.isConnected()

        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", true)
        val colorBlindMode = prefs.getInt("color_blind_mode", 0)
        val fontScale = prefs.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDarkTheme, colorBlindMode = colorBlindMode, fontScale = fontScale) {
                MainContent()
            }
        }
    }

    @Composable
    fun MainContent() {
        val colors = MaterialTheme.colorScheme

        if (showResetReminder.value) {
            AlertDialog(
                onDismissRequest = { },
                icon = { Icon(Icons.Default.Info, null, tint = colors.primary) },
                title = { Text("Atenção", color = colors.onSurface) },
                text = { Text("Antes de iniciar, aperte RESET no aparelho.", color = colors.onSurface) },
                containerColor = colors.surface,
                confirmButton = {
                    Button(onClick = { showResetReminder.value = false }, colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) {
                        Text("OK")
                    }
                }
            )
        }

        TesteScreen(
            bodyPart = currentBodyPart,
            patientName = patientName,
            results = resultsMap,
            activePointIndex = activePointIndex.intValue,
            currentBleValue = currentBleValue.intValue,
            isConnected = isBleConnected.value,
            isSaving = isSaving.value,
            onPointSelect = { index ->
                if(!isSaving.value) {
                    activePointIndex.intValue = index
                    currentBleValue.intValue = 0
                    lastValidValue = 0
                }
            },
            onCloseMeasurement = { activePointIndex.intValue = -1 },
            onBackClick = { if(!isSaving.value) finish() },
            onSaveToCache = { saveResultsToCache() }
        )

        if (isSaving.value) {
            Dialog(onDismissRequest = {}) {
                Box(modifier = Modifier.size(100.dp).background(colors.surface, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
            }
        }
    }

    private fun saveResultsToCache() {
        if (resultsMap.isEmpty()) {
            Toast.makeText(this, "Realize ao menos um ponto.", Toast.LENGTH_SHORT).show()
            return
        }
        SessionCache.results[currentBodyPart] = resultsMap.toMap()
        Toast.makeText(this, "Salvo. Volte para continuar.", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDataReceived(data: String) {
        val cleanData = data.trim()
        if (cleanData.equals("Enviado", ignoreCase = true)) {
            val finalValue = lastValidValue
            val currentIndex = activePointIndex.intValue
            if (currentIndex != -1 && finalValue > 0) {
                runOnUiThread {
                    resultsMap[currentIndex] = finalValue
                    activePointIndex.intValue = -1
                    Toast.makeText(this, "Ponto registrado!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val value = cleanData.toIntOrNull()
            if (value != null) {
                if (value > 0) lastValidValue = value
                runOnUiThread { currentBleValue.intValue = value }
            }
        }
    }

    override fun onConnected() { runOnUiThread { isBleConnected.value = true } }
    override fun onDisconnected() { runOnUiThread { isBleConnected.value = false } }
    override fun onError(message: String) {}

    override fun onDestroy() {
        super.onDestroy()
        BleManager.setListener(null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TesteScreen(
    bodyPart: String,
    patientName: String,
    results: Map<Int, Int>,
    activePointIndex: Int,
    currentBleValue: Int,
    isConnected: Boolean,
    isSaving: Boolean,
    onPointSelect: (Int) -> Unit,
    onCloseMeasurement: () -> Unit,
    onBackClick: () -> Unit,
    onSaveToCache: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isHand = bodyPart.contains("mao")
    val containerRatio = if (isHand) 0.8f else 0.65f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if(isHand) "Mão" else "Pé", color = colors.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(patientName, color = colors.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, enabled = !isSaving) { Icon(Icons.Default.ArrowBack, "Voltar", tint = colors.onSurface) }
                },
                actions = {
                    Icon(if(isConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled, null, tint = if(isConnected) Color.Green else colors.error, modifier = Modifier.padding(end = 8.dp).size(20.dp))
                    IconButton(onClick = onSaveToCache, enabled = !isSaving && results.isNotEmpty()) { Icon(Icons.Default.CheckCircle, "Salvar", tint = if(results.isNotEmpty()) colors.primary else Color.Gray) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(Brush.verticalGradient(colors = listOf(colors.background, colors.surface)))
        ) {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxWidth(0.95f).aspectRatio(containerRatio)) {
                    val imageRes = when(bodyPart) {
                        "mao_direita" -> R.drawable.right_hand
                        "mao_esquerda" -> R.drawable.left_hand
                        "pe_direito" -> R.drawable.right_foot
                        "pe_esquerdo" -> R.drawable.left_foot
                        else -> R.drawable.right_hand
                    }
                    Image(painter = painterResource(id = imageRes), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize().alpha(0.9f))
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val w = maxWidth
                        val h = maxHeight
                        // ... (Coordenadas dos pontos mantidas igual - lógica visual) ...
                        if (isHand) {
                            if(bodyPart == "mao_direita") {
                                MedicalPoint(0, 0.08f, 0.35f, results[0], w, h, onPointSelect)
                                MedicalPoint(1, 0.58f, 0.26f, results[1], w, h, onPointSelect)
                                MedicalPoint(2, 0.57f, 0.42f, results[2], w, h, onPointSelect)
                                MedicalPoint(3, 0.16f, 0.47f, results[3], w, h, onPointSelect)
                                MedicalPoint(4, 0.86f, 0.51f, results[4], w, h, onPointSelect)
                                MedicalPoint(5, 0.22f, 0.61f, results[5], w, h, onPointSelect)
                            } else {
                                MedicalPoint(0, 0.92f, 0.35f, results[0], w, h, onPointSelect)
                                MedicalPoint(1, 0.42f, 0.26f, results[1], w, h, onPointSelect)
                                MedicalPoint(2, 0.43f, 0.42f, results[2], w, h, onPointSelect)
                                MedicalPoint(3, 0.84f, 0.47f, results[3], w, h, onPointSelect)
                                MedicalPoint(4, 0.14f, 0.51f, results[4], w, h, onPointSelect)
                                MedicalPoint(5, 0.78f, 0.61f, results[5], w, h, onPointSelect)
                            }
                        } else {
                            if (bodyPart == "pe_direito") {
                                MedicalPoint(0, 0.37f, 0.26f, results[0], w, h, onPointSelect)
                                MedicalPoint(1, 0.26f, 0.48f, results[1], w, h, onPointSelect)
                                MedicalPoint(2, 0.69f, 0.21f, results[2], w, h, onPointSelect)
                                MedicalPoint(3, 0.13f, 0.34f, results[3], w, h, onPointSelect)
                                MedicalPoint(4, 0.45f, 0.35f, results[4], w, h, onPointSelect)
                                MedicalPoint(5, 0.77f, 0.37f, results[5], w, h, onPointSelect)
                                MedicalPoint(6, 0.39f, 0.66f, results[6], w, h, onPointSelect)
                                MedicalPoint(7, 0.74f, 0.61f, results[7], w, h, onPointSelect)
                                MedicalPoint(8, 0.69f, 0.80f, results[8], w, h, onPointSelect)
                            } else {
                                MedicalPoint(0, 0.63f, 0.26f, results[0], w, h, onPointSelect)
                                MedicalPoint(1, 0.74f, 0.48f, results[1], w, h, onPointSelect)
                                MedicalPoint(2, 0.31f, 0.21f, results[2], w, h, onPointSelect)
                                MedicalPoint(3, 0.87f, 0.34f, results[3], w, h, onPointSelect)
                                MedicalPoint(4, 0.55f, 0.35f, results[4], w, h, onPointSelect)
                                MedicalPoint(5, 0.23f, 0.37f, results[5], w, h, onPointSelect)
                                MedicalPoint(6, 0.61f, 0.66f, results[6], w, h, onPointSelect)
                                MedicalPoint(7, 0.26f, 0.61f, results[7], w, h, onPointSelect)
                                MedicalPoint(8, 0.31f, 0.80f, results[8], w, h, onPointSelect)
                            }
                        }
                    }
                }
            }
        }
        if (activePointIndex != -1 && !isSaving) {
            MeasurementDialog(currentLevel = currentBleValue, onDismiss = onCloseMeasurement)
        }
    }
}

@Composable
fun MedicalPoint(index: Int, xPercent: Float, yPercent: Float, resultLevel: Int?, parentWidth: Dp, parentHeight: Dp, onClick: (Int) -> Unit) {
    val isDone = resultLevel != null
    val resultData = ClinicalScale.getResult(resultLevel ?: 0)
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alphaAnim by infiniteTransition.animateFloat(initialValue = 0.3f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")

    // Cores específicas para os pontos (poderia usar tema também, mas estas são clínicas padrão)
    val baseColor = if (isDone) resultData.color else MaterialTheme.colorScheme.primary

    val offsetX = parentWidth * xPercent - 15.dp
    val offsetY = parentHeight * yPercent - 15.dp
    Box(modifier = Modifier.offset(x = offsetX, y = offsetY).size(30.dp).clip(CircleShape).border(2.dp, Color.White, CircleShape).background(baseColor.copy(alpha = if (isDone) 1f else 0.5f)).clickable { onClick(index) }, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().background(baseColor).alpha(if (isDone) 1f else alphaAnim))
        if (isDone) Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun MeasurementDialog(currentLevel: Int, onDismiss: () -> Unit) {
    val data = ClinicalScale.getResult(currentLevel)
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = colors.surface), border = BorderStroke(1.dp, data.color.copy(alpha=0.5f))) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Analisando", color = colors.primary, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(24.dp))
                Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(data.color.copy(alpha=0.2f)).border(4.dp, data.color, CircleShape), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = if(currentLevel == 0) "?" else "$currentLevel", color = data.color, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text(text = if(currentLevel == 0) "..." else data.force, color = colors.onSurface, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(data.description, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = colors.error), modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TesteScreenPreview() {
    EstesioTechTheme {
        TesteScreen(
            bodyPart = "mao_direita",
            patientName = "Paciente Teste",
            results = mapOf(0 to 2, 1 to 4),
            activePointIndex = -1,
            currentBleValue = 0,
            isConnected = true,
            isSaving = false,
            onPointSelect = {},
            onCloseMeasurement = {},
            onBackClick = {},
            onSaveToCache = {}
        )
    }
}