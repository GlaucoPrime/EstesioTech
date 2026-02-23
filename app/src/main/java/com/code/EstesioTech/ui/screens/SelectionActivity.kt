package com.code.EstesioTech.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.os.StrictMode
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// IMPORTS CORRIGIDOS DA NOVA ARQUITETURA DE PASTAS
import com.code.EstesioTech.R
import com.code.EstesioTech.data.cloud.EstesioCloud
import com.code.EstesioTech.ui.theme.EstesioTechTheme

object SessionCache {
    val results = mutableMapOf<String, Map<Int, Int>>()
    fun clear() = results.clear()
}

class SelectionActivity : ComponentActivity() {

    private var lastLocale: String? = null
    private var lastTheme: Boolean = true
    private var lastColorMode: Int = 0
    private var lastFontScale: Float = 1.0f

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val langCode = prefs.getString("language", "pt") ?: "pt"
        val locale = Locale(langCode)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        saveCurrentSettings()

        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())

        val patientCpf = intent.getStringExtra("PATIENT_CPF") ?: ""
        val patientName = intent.getStringExtra("PATIENT_NAME") ?: "Paciente"

        val cleanSessionId = SimpleDateFormat("ddMMyy_HHmm", Locale.getDefault()).format(Date())
        val sessionId = intent.getStringExtra("SESSION_ID") ?: cleanSessionId

        if (savedInstanceState == null) {
            SessionCache.clear()
        }

        loadUI(patientName, patientCpf, sessionId)
    }

    override fun onResume() {
        super.onResume()
        if (hasSettingsChanged()) {
            recreate()
        }
    }

    private fun saveCurrentSettings() {
        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        lastLocale = prefs.getString("language", "pt")
        lastTheme = prefs.getBoolean("dark_theme", true)
        lastColorMode = prefs.getInt("color_blind_mode", 0)
        lastFontScale = prefs.getFloat("font_scale", 1.0f)
    }

    private fun hasSettingsChanged(): Boolean {
        val prefs = getSharedPreferences("EstesioPrefs", MODE_PRIVATE)
        return lastLocale != prefs.getString("language", "pt") ||
                lastTheme != prefs.getBoolean("dark_theme", true) ||
                lastColorMode != prefs.getInt("color_blind_mode", 0) ||
                lastFontScale != prefs.getFloat("font_scale", 1.0f)
    }

    private fun loadUI(patientName: String, patientCpf: String, sessionId: String) {
        val prefs = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", true)
        val colorBlindMode = prefs.getInt("color_blind_mode", 0)
        val fontScale = prefs.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDarkTheme, colorBlindMode = colorBlindMode, fontScale = fontScale) {
                var isLoading by remember { mutableStateOf(false) }
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        SelectionScreen(
                            patientName = patientName,
                            patientCpf = patientCpf,
                            sessionId = sessionId,
                            onBack = { finish() },
                            onNavigateToTest = { bodyPart ->
                                val intent = Intent(this@SelectionActivity, TesteActivity::class.java).apply {
                                    putExtra("SESSION_ID", sessionId)
                                    putExtra("PATIENT_CPF", patientCpf)
                                    putExtra("PATIENT_NAME", patientName)
                                    putExtra("BODY_PART", bodyPart)
                                }
                                startActivity(intent)
                            },
                            onFinalizeAndSave = { hasDeformities ->
                                isLoading = true
                                EstesioCloud.saveCompleteSession(
                                    sessionId, patientCpf, patientName, SessionCache.results, hasDeformities,
                                    onSuccess = {
                                        isLoading = false
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Salvo com sucesso!") }
                                    },
                                    onError = { errorMsg ->
                                        isLoading = false
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Erro: $errorMsg") }
                                    }
                                )
                            },
                            onGeneratePDF = { action ->
                                isLoading = true
                                generatePDF(sessionId, patientName, patientCpf, action) { file ->
                                    isLoading = false
                                    if (action == "download" && file != null) {
                                        openPdf(file)
                                    }
                                }
                            }
                        )

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    // CORREÇÃO AQUI: Texto inserido de forma direta para evitar erro de unresolved reference.
                                    Text("Processando...", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openPdf(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Nenhum visualizador de PDF encontrado.", Toast.LENGTH_LONG).show()
        }
    }

    private fun generatePDF(sessionId: String, name: String, cpf: String, action: String, onComplete: (File?) -> Unit) {
        EstesioCloud.getFullSessionData(sessionId, onSuccess = { data ->
            try {
                val pdfDocument = PdfDocument()
                val pageWidth = 595
                val pageHeight = 842
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas

                val headerPaint = Paint().apply { color = android.graphics.Color.rgb(26, 38, 52) }
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 100f, headerPaint)

                val titlePaint = Paint().apply { color = android.graphics.Color.WHITE; textSize = 26f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
                canvas.drawText("RELATÓRIO ESTESIOTECH", pageWidth / 2f, 60f, titlePaint)

                val labelPaint = Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 12f; isFakeBoldText = true }
                val valuePaint = Paint().apply { color = android.graphics.Color.BLACK; textSize = 14f; isFakeBoldText = false }
                val linePaint = Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 1f }

                var yPos = 140f
                val leftMargin = 40f

                canvas.drawText("DADOS DO PACIENTE", leftMargin, yPos, labelPaint.apply { textSize = 16f; color = android.graphics.Color.rgb(0, 172, 193) })
                yPos += 30f

                canvas.drawText("NOME:", leftMargin, yPos, labelPaint.apply { textSize = 12f; color = android.graphics.Color.DKGRAY })
                canvas.drawText(name, leftMargin + 100f, yPos, valuePaint)
                yPos += 25f

                canvas.drawText("CPF:", leftMargin, yPos, labelPaint)
                canvas.drawText(cpf, leftMargin + 100f, yPos, valuePaint)
                yPos += 25f

                val ptLocale = Locale("pt", "BR")
                val dateStr = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", ptLocale).format(Date())
                val timeStr = SimpleDateFormat("HH:mm", ptLocale).format(Date())

                canvas.drawText("DATA:", leftMargin, yPos, labelPaint)
                canvas.drawText(dateStr, leftMargin + 100f, yPos, valuePaint)
                yPos += 25f

                canvas.drawText("HORÁRIO:", leftMargin, yPos, labelPaint)
                canvas.drawText(timeStr, leftMargin + 100f, yPos, valuePaint)

                yPos += 40f
                canvas.drawLine(leftMargin, yPos, pageWidth - leftMargin, yPos, linePaint)
                yPos += 40f

                val maxGif = data.maxOfOrNull { (it["gif"] as? Long)?.toInt() ?: 0 } ?: 0
                val riskColor = if(maxGif > 0) android.graphics.Color.RED else android.graphics.Color.rgb(34, 139, 34)
                val riskText = if(maxGif > 0) "RISCO IDENTIFICADO (GRAU $maxGif)" else "SENSIBILIDADE PRESERVADA (GRAU 0)"

                val boxPaint = Paint().apply { color = riskColor; style = Paint.Style.FILL }
                canvas.drawRect(leftMargin, yPos, pageWidth - leftMargin, yPos + 50f, boxPaint)

                val riskTextPaint = Paint().apply { color = android.graphics.Color.WHITE; textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
                canvas.drawText(riskText, pageWidth / 2f, yPos + 32f, riskTextPaint)
                yPos += 90f

                canvas.drawText("DETALHAMENTO POR MEMBRO", leftMargin, yPos, labelPaint.apply { textSize = 16f; color = android.graphics.Color.rgb(0, 172, 193) })
                yPos += 30f

                data.forEach { test ->
                    if (yPos > pageHeight - 100) {
                        pdfDocument.finishPage(page)
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPos = 50f
                    }

                    val rawPart = test["bodyPart"].toString()
                    val partName = rawPart.replace("_", " ").uppercase()

                    val partBgPaint = Paint().apply { color = android.graphics.Color.rgb(240, 240, 240) }
                    canvas.drawRect(leftMargin, yPos - 15f, pageWidth - leftMargin, yPos + 10f, partBgPaint)

                    canvas.drawText(partName, leftMargin + 10f, yPos, labelPaint.apply { color = android.graphics.Color.BLACK })
                    yPos += 25f

                    val points = test["pointsData"] as? Map<String, Any>
                    points?.toSortedMap()?.forEach { entry ->
                        val k = entry.key
                        val v = entry.value
                        val pLevel = v.toString().toIntOrNull() ?: 0

                        val pDesc = when(pLevel) {
                            1 -> "Verde (0,05g) - Normal"
                            2 -> "Azul (0,2g) - Diminuída"
                            3 -> "Violeta (2,0g) - Perda Protetora"
                            4 -> "Vermelho (4,0g) - Perda Severa"
                            5 -> "Laranja (10,0g) - Perda Profunda"
                            6 -> "Magenta (300g) - Perda Total"
                            else -> "Nível $pLevel"
                        }

                        val pointColor = if (pLevel >= 3) android.graphics.Color.RED else android.graphics.Color.BLACK
                        canvas.drawText("Ponto $k:", leftMargin + 20f, yPos, labelPaint)

                        val descPaint = Paint().apply { color = pointColor; textSize = 12f }
                        canvas.drawText(pDesc, leftMargin + 100f, yPos, descPaint)
                        yPos += 20f
                    }
                    yPos += 20f
                }

                pdfDocument.finishPage(page)

                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fileName = "EstesioTech_${name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
                val file = File(path, fileName)
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                if (action == "share") {
                    val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Enviar Relatório"))
                } else {
                    Toast.makeText(this, "Salvo em Downloads", Toast.LENGTH_LONG).show()
                }
                onComplete(file)
            } catch (e: Exception) {
                Toast.makeText(this, "Erro PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                onComplete(null)
            }
        }, onError = { errorMsg ->
            Toast.makeText(this, "Erro: $errorMsg", Toast.LENGTH_SHORT).show()
            onComplete(null)
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(
    patientName: String, patientCpf: String, sessionId: String,
    onBack: () -> Unit, onNavigateToTest: (String) -> Unit,
    onFinalizeAndSave: (Boolean) -> Unit, onGeneratePDF: (String) -> Unit
) {
    var completedParts by remember { mutableStateOf(SessionCache.results.keys.toSet()) }
    var showRedoDialog by remember { mutableStateOf<String?>(null) }
    var showFinalDialog by remember { mutableStateOf(false) }
    var hasDeformities by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) completedParts = SessionCache.results.keys.toSet()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.evaluation_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(patientName, style = MaterialTheme.typography.bodySmall, color = colors.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.cancel), tint = colors.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surface)
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.select_area), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.padding(bottom = 24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectionCard(stringResource(R.string.right_hand), completedParts.contains("mao_direita"), colors, Modifier.weight(1f)) {
                    if (completedParts.contains("mao_direita")) showRedoDialog = "mao_direita" else onNavigateToTest("mao_direita")
                }
                SelectionCard(stringResource(R.string.left_hand), completedParts.contains("mao_esquerda"), colors, Modifier.weight(1f)) {
                    if (completedParts.contains("mao_esquerda")) showRedoDialog = "mao_esquerda" else onNavigateToTest("mao_esquerda")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectionCard(stringResource(R.string.right_foot), completedParts.contains("pe_direito"), colors, Modifier.weight(1f)) {
                    if (completedParts.contains("pe_direito")) showRedoDialog = "pe_direito" else onNavigateToTest("pe_direito")
                }
                SelectionCard(stringResource(R.string.left_foot), completedParts.contains("pe_esquerdo"), colors, Modifier.weight(1f)) {
                    if (completedParts.contains("pe_esquerdo")) showRedoDialog = "pe_esquerdo" else onNavigateToTest("pe_esquerdo")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).clickable { hasDeformities = !hasDeformities }
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = hasDeformities, onCheckedChange = { hasDeformities = it }, colors = CheckboxDefaults.colors(checkedColor = colors.error))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(stringResource(R.string.deformities_question), color = colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(stringResource(R.string.deformities_desc), color = colors.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }

            val allCompleted = completedParts.size >= 4

            Button(
                onClick = { onFinalizeAndSave(hasDeformities); showFinalDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if(allCompleted) colors.primary else colors.surfaceVariant, contentColor = if(allCompleted) colors.onPrimary else colors.onSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                enabled = allCompleted
            ) {
                if (allCompleted) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.finish_evaluation), fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.complete_all_tests), fontWeight = FontWeight.Normal)
                }
            }
        }
    }

    if (showRedoDialog != null) {
        val part = showRedoDialog!!
        val partName = when(part) {
            "mao_direita" -> stringResource(R.string.right_hand)
            "mao_esquerda" -> stringResource(R.string.left_hand)
            "pe_direito" -> stringResource(R.string.right_foot)
            "pe_esquerdo" -> stringResource(R.string.left_foot)
            else -> part.replace("_", " ").uppercase()
        }

        AlertDialog(
            onDismissRequest = { showRedoDialog = null },
            title = { Text(stringResource(R.string.redo_test), color = colors.onSurface) },
            text = { Text(stringResource(R.string.redo_desc), color = colors.onSurfaceVariant) },
            confirmButton = {
                Button(onClick = {
                    SessionCache.results.remove(part)
                    showRedoDialog = null
                    completedParts = SessionCache.results.keys.toSet()
                    onNavigateToTest(part)
                }, colors = ButtonDefaults.buttonColors(containerColor = colors.error)) { Text(stringResource(R.string.redo_btn)) }
            },
            dismissButton = { TextButton(onClick = { showRedoDialog = null }) { Text(stringResource(R.string.cancel), color = colors.primary) } },
            containerColor = colors.surface
        )
    }

    if (showFinalDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.data_saved), color = colors.onSurface) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onGeneratePDF("download") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = colors.primary)) {
                        Icon(Icons.Default.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.download_pdf))
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onGeneratePDF("share") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = colors.secondary)) {
                        Icon(Icons.Default.Share, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.share_pdf))
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showFinalDialog = false; onBack() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.back_home), color = colors.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {}, dismissButton = {}, containerColor = colors.surface
        )
    }
}

@Composable
fun SelectionCard(title: String, isCompleted: Boolean, colors: ColorScheme, modifier: Modifier, onClick: () -> Unit) {
    val containerColor = if (isCompleted) colors.primaryContainer else colors.surface

    Card(modifier = modifier.height(120.dp).clickable { onClick() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = containerColor), border = BorderStroke(1.dp, if(isCompleted) colors.primary else colors.outline.copy(alpha=0.2f))) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isCompleted) colors.primary else colors.secondaryContainer)) {
                if (isCompleted) Icon(Icons.Default.Lock, null, tint = colors.onPrimary) else Text((title.first()).toString(), color = colors.onSecondaryContainer, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = if (isCompleted) colors.primary else colors.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SelectionScreenPreview() {
    EstesioTechTheme { SelectionScreen("Maria", "123", "preview", {}, {}, {_ ->}, {}) }
}