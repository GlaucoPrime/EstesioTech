package com.code.EstesioTech

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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// Cache temporário
object SessionCache {
    val results = mutableMapOf<String, Map<Int, Int>>()
    fun clear() = results.clear()
}

class SelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val builder = StrictMode.VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())

        val patientCpf = intent.getStringExtra("PATIENT_CPF") ?: ""
        val patientName = intent.getStringExtra("PATIENT_NAME") ?: "Paciente"
        val sessionId = intent.getStringExtra("SESSION_ID") ?: UUID.randomUUID().toString()

        if (savedInstanceState == null) {
            SessionCache.clear()
        }

        setContent {
            EstesioTechTheme {
                // Estado de Loading Global para esta tela
                var isLoading by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxSize()) {
                    SelectionScreen(
                        patientName = patientName,
                        patientCpf = patientCpf,
                        sessionId = sessionId,
                        onBack = { finish() },
                        onNavigateToTest = { bodyPart ->
                            // CORREÇÃO: Usando this@SelectionActivity para garantir o Contexto correto
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
                                    // CORREÇÃO: Contexto específico
                                    Toast.makeText(this@SelectionActivity, "Sessão salva com sucesso!", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    isLoading = false
                                    // CORREÇÃO: Contexto específico
                                    Toast.makeText(this@SelectionActivity, "Erro ao salvar: $it", Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        onGeneratePDF = { action ->
                            isLoading = true
                            generatePDF(sessionId, patientName, patientCpf, action) {
                                isLoading = false
                            }
                        }
                    )

                    // Overlay de Carregamento
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable(enabled = false) {},
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF00ACC1))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Processando...", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun generatePDF(sessionId: String, name: String, cpf: String, action: String, onComplete: () -> Unit) {
        EstesioCloud.getFullSessionData(sessionId, onSuccess = { data ->
            try {
                // --- PDF CONFIGURATION ---
                val pdfDocument = PdfDocument()
                val pageWidth = 595
                val pageHeight = 842
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas

                // --- PAINTS (Estilos) ---
                val titlePaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 24f
                    isFakeBoldText = true
                    textAlign = Paint.Align.LEFT
                }
                val headerBgPaint = Paint().apply { color = android.graphics.Color.rgb(26, 38, 52) } // Dark Blue do App
                val accentPaint = Paint().apply { color = android.graphics.Color.rgb(0, 172, 193) } // Cyan do App
                val textPaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 12f
                }
                val boldTextPaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 12f
                    isFakeBoldText = true
                }
                val riskPaint = Paint().apply { textSize = 14f; isFakeBoldText = true }

                // --- HEADER ---
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 80f, headerBgPaint)
                canvas.drawText("Relatório EstesioTech", 30f, 50f, titlePaint)

                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
                val datePaint = Paint().apply { color = android.graphics.Color.LTGRAY; textSize = 10f; textAlign = Paint.Align.RIGHT }
                canvas.drawText("Gerado em: $dateStr", pageWidth - 30f, 50f, datePaint)

                // --- DADOS DO PACIENTE ---
                var yPos = 120f
                canvas.drawText("DADOS DO PACIENTE", 30f, yPos, boldTextPaint)
                yPos += 20f
                canvas.drawText("Nome: $name", 30f, yPos, textPaint)
                yPos += 15f
                canvas.drawText("CPF: $cpf", 30f, yPos, textPaint)

                // --- RESULTADO GLOBAL (GIF) ---
                val maxGif = data.maxOfOrNull { (it["gif"] as? Long)?.toInt() ?: 0 } ?: 0
                val gifText = when(maxGif) {
                    0 -> "Grau 0: Sensibilidade Preservada"
                    1 -> "Grau 1: Perda de Sensibilidade Protetora"
                    else -> "Grau 2: Presença de Deformidades/Úlceras"
                }

                yPos += 30f
                // Desenha caixa de risco
                val riskColor = if(maxGif > 0) android.graphics.Color.rgb(244, 67, 54) else android.graphics.Color.rgb(76, 175, 80) // Red or Green
                val boxPaint = Paint().apply { color = riskColor; style = Paint.Style.FILL }
                canvas.drawRect(30f, yPos, pageWidth - 30f, yPos + 40f, boxPaint)

                riskPaint.color = android.graphics.Color.WHITE
                canvas.drawText(gifText.uppercase(), 45f, yPos + 25f, riskPaint)

                yPos += 70f
                canvas.drawText("DETALHAMENTO DA AVALIAÇÃO", 30f, yPos, boldTextPaint)
                canvas.drawLine(30f, yPos + 5f, pageWidth - 30f, yPos + 5f, accentPaint)
                yPos += 30f

                // --- ITERAR TESTES ---
                data.forEach { test ->
                    // Checa se precisa de nova página
                    if (yPos > pageHeight - 100) {
                        pdfDocument.finishPage(page)
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPos = 50f
                    }

                    val part = (test["bodyPart"] as? String)?.replace("_", " ")?.uppercase() ?: "MEMBRO"
                    val points = test["pointsData"] as? Map<String, Any>

                    // Cabeçalho do Membro
                    canvas.drawRect(30f, yPos, pageWidth - 30f, yPos + 20f, Paint().apply { color = android.graphics.Color.LTGRAY })
                    canvas.drawText(part, 40f, yPos + 14f, boldTextPaint)
                    yPos += 35f

                    points?.toSortedMap()?.forEach { (k, v) ->
                        val pLevel = v.toString().toIntOrNull() ?: 0
                        val pDesc = ClinicalScale.getResult(pLevel).description

                        // Destaque visual se houver perda de sensibilidade (> 2g/Violeta)
                        val pointColor = if (pLevel >= 3) android.graphics.Color.RED else android.graphics.Color.BLACK
                        textPaint.color = pointColor

                        canvas.drawText("Ponto $k:", 40f, yPos, boldTextPaint)
                        canvas.drawText("Filamento nível $v - $pDesc", 100f, yPos, textPaint)
                        yPos += 15f
                    }
                    yPos += 15f
                }

                pdfDocument.finishPage(page)

                // --- SALVAR E COMPARTILHAR ---
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fileName = "EstesioTech_${name.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
                val file = File(path, fileName)
                pdfDocument.writeTo(FileOutputStream(file))
                pdfDocument.close()

                if (action == "download") {
                    Toast.makeText(this, "Salvo em Downloads: $fileName", Toast.LENGTH_LONG).show()
                } else {
                    val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Enviar Relatório"))
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Erro PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            } finally {
                onComplete()
            }
        }, onError = {
            onComplete()
            Toast.makeText(this, "Erro ao baixar dados: $it", Toast.LENGTH_SHORT).show()
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(
    patientName: String,
    patientCpf: String,
    sessionId: String,
    onBack: () -> Unit,
    onNavigateToTest: (String) -> Unit,
    onFinalizeAndSave: (Boolean) -> Unit, // Recebe se tem deformidade
    onGeneratePDF: (String) -> Unit
) {
    var completedParts by remember { mutableStateOf(SessionCache.results.keys.toSet()) }
    var showRedoDialog by remember { mutableStateOf<String?>(null) }
    var showFinalDialog by remember { mutableStateOf(false) }

    // Novo Estado para o GIF 2
    var hasDeformities by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                completedParts = SessionCache.results.keys.toSet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = Color(0xFF101820),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Avaliação de Sensibilidade", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(patientName, color = Color.Gray, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Voltar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A2634))
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("Selecione a área", color = Color(0xFF00ACC1), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))

            // Cards de seleção
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectionCard("Mão Direita", completedParts.contains("mao_direita"), Modifier.weight(1f)) {
                    if (completedParts.contains("mao_direita")) showRedoDialog = "mao_direita" else onNavigateToTest("mao_direita")
                }
                SelectionCard("Mão Esquerda", completedParts.contains("mao_esquerda"), Modifier.weight(1f)) {
                    if (completedParts.contains("mao_esquerda")) showRedoDialog = "mao_esquerda" else onNavigateToTest("mao_esquerda")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SelectionCard("Pé Direito", completedParts.contains("pe_direito"), Modifier.weight(1f)) {
                    if (completedParts.contains("pe_direito")) showRedoDialog = "pe_direito" else onNavigateToTest("pe_direito")
                }
                SelectionCard("Pé Esquerdo", completedParts.contains("pe_esquerdo"), Modifier.weight(1f)) {
                    if (completedParts.contains("pe_esquerdo")) showRedoDialog = "pe_esquerdo" else onNavigateToTest("pe_esquerdo")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Checkbox para Grau 2 (NOVIDADE)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2634)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).clickable { hasDeformities = !hasDeformities }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hasDeformities,
                        onCheckedChange = { hasDeformities = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF44336), uncheckedColor = Color.Gray)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Presença de Deformidades/Úlceras?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Marque se observar garras, pé caído ou feridas. (Define Grau 2)", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }

            val allCompleted = completedParts.size >= 4

            Button(
                onClick = {
                    onFinalizeAndSave(hasDeformities)
                    showFinalDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).border(1.dp, if(allCompleted) Color(0xFF00ACC1) else Color.Gray, RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if(allCompleted) Color(0xFF00ACC1) else Color.Gray.copy(alpha=0.2f),
                    contentColor = if(allCompleted) Color.White else Color.Gray
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = allCompleted
            ) {
                if (allCompleted) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("FINALIZAR AVALIAÇÃO", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Complete os 4 testes", fontWeight = FontWeight.Normal)
                }
            }
        }
    }

    if (showRedoDialog != null) {
        val part = showRedoDialog!!
        val partName = part.replace("_", " ").uppercase()
        AlertDialog(
            onDismissRequest = { showRedoDialog = null },
            title = { Text("Refazer Teste?", color = Color.White) },
            text = { Text("Dados anteriores de $partName serão substituídos.", color = Color.Gray) },
            confirmButton = {
                Button(onClick = {
                    SessionCache.results.remove(part) // Remove do cache
                    showRedoDialog = null
                    completedParts = SessionCache.results.keys.toSet()
                    onNavigateToTest(part)
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679))) { Text("Refazer") }
            },
            dismissButton = { TextButton(onClick = { showRedoDialog = null }) { Text("Cancelar", color = Color(0xFF00ACC1)) } },
            containerColor = Color(0xFF1A2634)
        )
    }

    if (showFinalDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Dados Salvos", color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Sessão salva com sucesso em ${SimpleDateFormat("dd/MM HH:mm").format(Date())}.", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { onGeneratePDF("download") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Baixar PDF")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onGeneratePDF("share") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1)), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Compartilhar PDF")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showFinalDialog = false; onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Voltar para Home", color = Color.Gray) }
                }
            },
            confirmButton = {}, dismissButton = {}, containerColor = Color(0xFF1A2634)
        )
    }
}

@Composable
fun SelectionCard(title: String, isCompleted: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val borderColor = if (isCompleted) Color(0xFF00ACC1) else Color.Gray.copy(alpha = 0.3f)
    val containerColor = if (isCompleted) Color(0xFF00ACC1).copy(alpha = 0.1f) else Color(0xFF1A2634)

    Card(
        modifier = modifier.height(120.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isCompleted) Color(0xFF00ACC1) else Color.Gray.copy(alpha = 0.2f))) {
                if (isCompleted) Icon(Icons.Default.Lock, null, tint = Color.White) else Text((title.first()).toString(), color = Color.Gray, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = if (isCompleted) Color(0xFF00ACC1) else Color.Gray, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (isCompleted) Text("Concluído", color = Color(0xFF00ACC1), fontSize = 10.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SelectionScreenPreview() {
    EstesioTechTheme { SelectionScreen("Maria", "123", "preview", {}, {}, {_ ->}, {}) }
}