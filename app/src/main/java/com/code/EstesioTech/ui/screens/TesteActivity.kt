package com.code.EstesioTech.ui.screens

import android.os.Bundle
import com.code.EstesioTech.SessionCache
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.code.EstesioTech.data.bluetooth.BleManager
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.LocaleUtils

/**
 * Tela de execucao do teste de sensibilidade.
 *
 * NOVIDADE: Indicador de Resultado em Tempo Real
 * Uma "bolinha" animada no rodape mostra a classificacao atual da sessao
 * conforme os pontos vao sendo registrados. A cor muda progressivamente:
 *   Cinza (sem dados) -> Verde (normal) -> Amarelo (atencao) -> Vermelho (risco)
 *
 * Fluxo de dados BLE:
 *   ESP32 envia numeros "1"-"6" em tempo real conforme o filamento e acionado
 *   ESP32 envia "Enviado" quando o medico confirma o ponto (botao fisico)
 *   Ao receber "Enviado", o ultimo nivel valido e salvo no resultsMap
 *
 * Correcao do bug de RESET:
 *   Um dialog informativo exige que o medico pressione RESET no aparelho antes
 *   de iniciar, garantindo que o contador do ESP32 comece do zero.
 */
class TesteActivity : ComponentActivity(), BleManager.ConnectionListener {

    private val activePointIndex  = mutableIntStateOf(-1)
    private val currentBleValue   = mutableIntStateOf(0)
    private var lastValidValue    = 0
    private val resultsMap        = mutableStateMapOf<Int, Int>()
    private val isBleConnected    = mutableStateOf(false)
    private val showResetReminder = mutableStateOf(true)
    private val isSaving          = mutableStateOf(false)

    private var bodyPart    = ""
    private var patientCpf  = ""
    private var patientName = ""
    private var sessionId   = ""

    override fun attachBaseContext(newBase: android.content.Context) =
        super.attachBaseContext(LocaleUtils.wrapContext(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleUtils.setLocale(this)

        bodyPart    = intent.getStringExtra("BODY_PART")     ?: "mao_direita"
        patientCpf  = intent.getStringExtra("PATIENT_CPF")   ?: ""
        patientName = intent.getStringExtra("PATIENT_NAME")  ?: "Paciente"
        sessionId   = intent.getStringExtra("SESSION_ID")    ?: ""

        BleManager.setListener(this)
        isBleConnected.value = BleManager.isConnected()

        val prefs     = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        val isDark    = prefs.getBoolean("dark_theme", true)
        val colorMode = prefs.getInt("color_blind_mode", 0)
        val fontScale = prefs.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDark, colorBlindMode = colorMode, fontScale = fontScale) {
                MainContent()
            }
        }
    }

    @Composable
    fun MainContent() {
        val colors = MaterialTheme.colorScheme

        // Dialog de RESET obrigatorio antes de comecar
        if (showResetReminder.value) {
            AlertDialog(
                onDismissRequest = {},
                icon    = { Icon(Icons.Default.RestartAlt, null, tint = colors.primary, modifier = Modifier.size(36.dp)) },
                title   = { Text("Preparar Aparelho", fontWeight = FontWeight.Bold) },
                text    = {
                    Text(
                        "Antes de iniciar, pressione o botao RESET no Estesiometro.\n\nIsso zera o contador interno e garante leituras precisas.",
                        textAlign = TextAlign.Center
                    )
                },
                containerColor  = colors.surface,
                confirmButton   = {
                    Button(
                        onClick = { showResetReminder.value = false },
                        colors  = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        modifier= Modifier.fillMaxWidth()
                    ) {
                        Text("RESET FEITO — INICIAR")
                    }
                }
            )
        }

        TesteScreen(
            bodyPart         = bodyPart,
            patientName      = patientName,
            results          = resultsMap,
            activePointIndex = activePointIndex.intValue,
            currentBleValue  = currentBleValue.intValue,
            isConnected      = isBleConnected.value,
            isSaving         = isSaving.value,
            onPointSelect    = { idx ->
                if (!isSaving.value) {
                    activePointIndex.intValue = idx
                    currentBleValue.intValue  = 0
                    lastValidValue            = 0
                }
            },
            onCloseMeasurement = { activePointIndex.intValue = -1 },
            onBackClick        = { if (!isSaving.value) finish() },
            onSaveToCache      = { saveResultsToCache() }
        )

        if (isSaving.value) {
            Dialog(onDismissRequest = {}) {
                Box(
                    modifier         = Modifier.size(90.dp).background(colors.surface, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            }
        }
    }

    private fun saveResultsToCache() {
        if (resultsMap.isEmpty()) {
            Toast.makeText(this, "Realize ao menos um ponto antes de salvar.", Toast.LENGTH_SHORT).show()
            return
        }
        SessionCache.results[bodyPart] = resultsMap.toMap()
        Toast.makeText(this, "Salvo com sucesso!", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onDataReceived(data: String) {
        val clean = data.trim()
        if (clean.equals("Enviado", ignoreCase = true)) {
            // Confirmacao do ESP32: salva o ultimo valor valido no mapa
            val final   = lastValidValue
            val pointIdx = activePointIndex.intValue
            if (pointIdx != -1 && final > 0) {
                resultsMap[pointIdx]          = final
                activePointIndex.intValue     = -1
                runOnUiThread {
                    Toast.makeText(this, "Ponto ${pointIdx + 1} registrado!", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            val v = clean.toIntOrNull()
            if (v != null) {
                if (v > 0) lastValidValue = v
                currentBleValue.intValue = v
            }
        }
    }

    override fun onConnected()              { isBleConnected.value = true  }
    override fun onDisconnected()           { isBleConnected.value = false }
    override fun onError(message: String)   { /* registra silenciosamente */ }

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
    val colors  = MaterialTheme.colorScheme
    val isHand  = bodyPart.contains("mao")
    val total   = if (isHand) 6 else 9

    // Calcula o risco atual da sessao para o indicador em tempo real
    val sessionRisk    = ClinicalScale.getSessionRisk(results)
    val progressFrac   = ClinicalScale.getProgress(results.size, total)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isHand) "Mao" else "Pe",
                            fontWeight = FontWeight.ExtraBold,
                            color      = colors.onSurface,
                            fontSize   = 20.sp
                        )
                        Text(patientName, color = colors.primary, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, enabled = !isSaving) {
                        Icon(Icons.Default.ArrowBack, null, tint = colors.onSurface)
                    }
                },
                actions = {
                    // Indicador de conexao BLE
                    Icon(
                        if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.BluetoothDisabled,
                        null,
                        tint     = if (isConnected) Color(0xFF4CAF50) else colors.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    // Botao salvar (habilitado apenas se tiver resultados)
                    IconButton(onClick = onSaveToCache, enabled = !isSaving && results.isNotEmpty()) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = if (results.isNotEmpty()) colors.primary else colors.onSurface.copy(0.3f),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .background(Brush.verticalGradient(listOf(colors.background, colors.surface.copy(0.5f))))
        ) {
            // ---- Mapa anatomico com pontos ----
            BoxWithConstraints(
                modifier         = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val aspectRatio = if (isHand) 0.80f else 0.65f
                Box(modifier = Modifier.fillMaxWidth(0.95f).aspectRatio(aspectRatio)) {
                    val imgRes = when (bodyPart) {
                        "mao_direita"  -> R.drawable.right_hand
                        "mao_esquerda" -> R.drawable.left_hand
                        "pe_direito"   -> R.drawable.right_foot
                        else           -> R.drawable.left_foot
                    }
                    Image(
                        painter     = painterResource(imgRes),
                        contentDescription = null,
                        contentScale= ContentScale.FillBounds,
                        modifier    = Modifier.fillMaxSize().alpha(0.88f)
                    )

                    // Pontos anatomicos
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val w = maxWidth; val h = maxHeight
                        val pointCoords = getPointCoordinates(bodyPart)
                        pointCoords.forEachIndexed { idx, (xPct, yPct) ->
                            MedicalPoint(
                                index        = idx,
                                xPercent     = xPct,
                                yPercent     = yPct,
                                resultLevel  = results[idx],
                                isActive     = activePointIndex == idx,
                                parentWidth  = w,
                                parentHeight = h,
                                onClick      = onPointSelect
                            )
                        }
                    }
                }
            }

            // ---- Indicador de Resultado em Tempo Real (NOVIDADE) ----
            RealtimeRiskIndicator(
                completedCount = results.size,
                totalPoints    = total,
                progressFrac   = progressFrac,
                sessionRisk    = sessionRisk,
                colors         = colors
            )

            Spacer(Modifier.height(8.dp))
        }
    }

    // Dialog de medicao ativa
    if (activePointIndex != -1 && !isSaving) {
        MeasurementDialog(currentLevel = currentBleValue, onDismiss = onCloseMeasurement)
    }
}

/**
 * Indicador de resultado em tempo real.
 *
 * Exibido na parte inferior da tela durante o exame.
 * Mostra:
 *  - Barra de progresso com os pontos concluidos
 *  - Bolinha colorida com o nivel de risco atual
 *  - Descricao textual do resultado parcial
 *
 * A bolinha pulsa quando ha risco detectado, chamando atencao do medico.
 */
@Composable
private fun RealtimeRiskIndicator(
    completedCount: Int,
    totalPoints: Int,
    progressFrac: Float,
    sessionRisk: com.code.EstesioTech.ClinicalResult,
    colors: ColorScheme
) {
    val animatedProgress by animateFloatAsState(
        targetValue   = progressFrac,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "progress"
    )
    val riskColor by animateColorAsState(
        targetValue   = sessionRisk.color,
        animationSpec = tween(400),
        label         = "riskColor"
    )

    // Pulsacao para nivel de risco >= 3
    val infinitePulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infinitePulse.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val shouldPulse = sessionRisk.level >= 3 && completedCount > 0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape  = RoundedCornerShape(16.dp),
        color  = colors.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier  = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bolinha de risco animada
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(if (shouldPulse) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(riskColor.copy(alpha = 0.15f))
                    .border(2.dp, riskColor.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (completedCount == 0) {
                    Text("?", color = colors.onSurface.copy(0.4f), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                } else {
                    Text(
                        text       = "${sessionRisk.level}",
                        color      = riskColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 22.sp
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                // Descricao do resultado parcial
                Text(
                    text       = if (completedCount == 0) "Aguardando pontos..."
                    else sessionRisk.description,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = if (completedCount == 0) colors.onSurface.copy(0.5f) else riskColor
                )
                Text(
                    text  = "$completedCount de $totalPoints pontos avaliados",
                    color = colors.onSurface.copy(0.55f),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(6.dp))
                // Barra de progresso
                LinearProgressIndicator(
                    progress          = animatedProgress,
                    modifier          = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color             = riskColor,
                    trackColor        = colors.outline.copy(0.2f)
                )
            }
        }
    }
}

// Retorna as coordenadas (x%, y%) de cada ponto para a parte do corpo
private fun getPointCoordinates(bodyPart: String): List<Pair<Float, Float>> = when (bodyPart) {
    "mao_direita"  -> listOf(0.08f to 0.35f, 0.58f to 0.26f, 0.57f to 0.42f, 0.16f to 0.47f, 0.86f to 0.51f, 0.22f to 0.61f)
    "mao_esquerda" -> listOf(0.92f to 0.35f, 0.42f to 0.26f, 0.43f to 0.42f, 0.84f to 0.47f, 0.14f to 0.51f, 0.78f to 0.61f)
    "pe_direito"   -> listOf(0.37f to 0.26f, 0.26f to 0.48f, 0.69f to 0.21f, 0.13f to 0.34f, 0.45f to 0.35f, 0.77f to 0.37f, 0.39f to 0.66f, 0.74f to 0.61f, 0.69f to 0.80f)
    else           -> listOf(0.63f to 0.26f, 0.74f to 0.48f, 0.31f to 0.21f, 0.87f to 0.34f, 0.55f to 0.35f, 0.23f to 0.37f, 0.61f to 0.66f, 0.26f to 0.61f, 0.31f to 0.80f)
}

@Composable
fun MedicalPoint(
    index: Int,
    xPercent: Float,
    yPercent: Float,
    resultLevel: Int?,
    isActive: Boolean,
    parentWidth: Dp,
    parentHeight: Dp,
    onClick: (Int) -> Unit
) {
    val isDone     = resultLevel != null
    val resultData = ClinicalScale.getResult(resultLevel ?: 0)
    val colors     = MaterialTheme.colorScheme

    val infiniteAnim = rememberInfiniteTransition(label = "blink_$index")
    val blinkAlpha by infiniteAnim.animateFloat(
        0.2f, 1f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "blink"
    )
    val activeScale by animateFloatAsState(
        targetValue   = if (isActive) 1.35f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "active"
    )

    val offsetX = parentWidth  * xPercent - 15.dp
    val offsetY = parentHeight * yPercent - 15.dp
    val dotColor = if (isDone) resultData.color else colors.primary

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .size(30.dp)
            .scale(activeScale)
            .clip(CircleShape)
            .border(2.dp, Color.White.copy(0.9f), CircleShape)
            .background(dotColor.copy(alpha = if (isDone) 1f else 0.5f))
            .alpha(if (!isDone && !isActive) blinkAlpha else 1f)
            .clickable { onClick(index) },
        contentAlignment = Alignment.Center
    ) {
        if (isDone) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        } else if (isActive) {
            // Anel pulsante quando este ponto esta ativo
            CircularProgressIndicator(
                modifier     = Modifier.fillMaxSize(),
                color        = Color.White,
                strokeWidth  = 2.dp
            )
        } else {
            Text(
                text     = "${index + 1}",
                color    = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MeasurementDialog(currentLevel: Int, onDismiss: () -> Unit) {
    val data   = ClinicalScale.getResult(currentLevel)
    val colors = MaterialTheme.colorScheme

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape  = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(colors.surface),
            border = BorderStroke(1.5.dp, data.color.copy(0.5f))
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Medindo Ponto", color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("Aguarde o ESP32 registrar a pressao...", color = colors.onSurface.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)

                Spacer(Modifier.height(24.dp))

                // Circulo de nivel atual
                Box(
                    modifier         = Modifier
                        .size(120.dp)
                        .scale(if (currentLevel > 0) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(data.color.copy(0.12f))
                        .border(3.dp, data.color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text       = if (currentLevel == 0) "?" else "$currentLevel",
                            color      = data.color,
                            fontSize   = 40.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(data.force, color = colors.onSurface.copy(0.7f), fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(data.description, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center)
                Text(data.interpretation, color = colors.onSurface.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)

                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, colors.error.copy(0.5f))
                ) {
                    Text("Cancelar Ponto", color = colors.error)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TesteScreenPreview() {
    EstesioTechTheme {
        TesteScreen(
            bodyPart = "mao_direita", patientName = "Paciente Teste",
            results = mapOf(0 to 2, 1 to 4, 2 to 1),
            activePointIndex = -1, currentBleValue = 0,
            isConnected = true, isSaving = false,
            onPointSelect = {}, onCloseMeasurement = {},
            onBackClick = {}, onSaveToCache = {}
        )
    }
}