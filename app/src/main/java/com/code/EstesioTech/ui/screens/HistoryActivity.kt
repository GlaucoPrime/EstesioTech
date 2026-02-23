package com.code.EstesioTech

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.filled.History
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.code.EstesioTech.data.cloud.EstesioCloud
import com.code.EstesioTech.data.cloud.PatientHistoryData
import com.code.EstesioTech.data.cloud.SessionData
import com.code.EstesioTech.ui.theme.EstesioTechTheme
import com.code.EstesioTech.utils.LocaleUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocaleUtils.setLocale(this)

        val prefs = getSharedPreferences("EstesioPrefs", Context.MODE_PRIVATE)
        val isDarkTheme = prefs.getBoolean("dark_theme", true)
        val colorBlindMode = prefs.getInt("color_blind_mode", 0)
        val fontScale = prefs.getFloat("font_scale", 1.0f)

        setContent {
            EstesioTechTheme(darkTheme = isDarkTheme, colorBlindMode = colorBlindMode, fontScale = fontScale) {
                var loadingMessage by remember { mutableStateOf<String?>(null) }

                Box(modifier = Modifier.fillMaxSize()) {
                    HistoryScreen(
                        onBack = { finish() },
                        onLoadingChange = { message -> loadingMessage = message },
                        onGeneratePDF = { sessionId, name, cpf, action ->
                            loadingMessage = "Gerando PDF..."
                            generateAndSharePDF(sessionId, name, cpf, action) { file ->
                                loadingMessage = null
                                // Se for download, abre automaticamente
                                if (action == "download" && file != null) {
                                    openPdf(file)
                                }
                            }
                        }
                    )

                    loadingMessage?.let { msg ->
                        LoadingOverlay(message = msg)
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

    private fun generateAndSharePDF(sessionId: String, name: String, cpf: String, action: String, onComplete: (File?) -> Unit) {
        EstesioCloud.getFullSessionData(sessionId, onSuccess = { data ->
            try {
                // --- DESIGN DO PDF ---
                val pdfDocument = PdfDocument()
                val pageWidth = 595
                val pageHeight = 842
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                val paint = Paint()

                // 1. Cabeçalho (Azul Escuro)
                val headerPaint = Paint().apply { color = android.graphics.Color.rgb(26, 38, 52) }
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 100f, headerPaint)

                // Título Branco
                val titlePaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 26f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("RELATÓRIO ESTESIOTECH", pageWidth / 2f, 60f, titlePaint)

                // 2. Configuração de Texto do Corpo
                val labelPaint = Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 12f
                    isFakeBoldText = true
                }
                val valuePaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 14f
                    isFakeBoldText = false
                }
                val linePaint = Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    strokeWidth = 1f
                }

                var yPos = 140f
                val leftMargin = 40f

                // 3. Dados do Paciente
                canvas.drawText("DADOS DO PACIENTE", leftMargin, yPos, labelPaint.apply { textSize = 16f; color = android.graphics.Color.rgb(0, 172, 193) }) // Cyan
                yPos += 30f

                canvas.drawText("NOME:", leftMargin, yPos, labelPaint.apply { textSize = 12f; color = android.graphics.Color.DKGRAY })
                canvas.drawText(name, leftMargin + 100f, yPos, valuePaint)
                yPos += 25f

                canvas.drawText("CPF:", leftMargin, yPos, labelPaint)
                canvas.drawText(cpf, leftMargin + 100f, yPos, valuePaint)
                yPos += 25f

                // Data e Hora em Português
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

                // 4. Resultado Geral
                val maxGif = data.maxOfOrNull { (it["gif"] as? Long)?.toInt() ?: 0 } ?: 0
                val riskColor = if(maxGif > 0) android.graphics.Color.RED else android.graphics.Color.rgb(34, 139, 34) // Verde Floresta
                val riskText = if(maxGif > 0) "RISCO IDENTIFICADO (GRAU $maxGif)" else "SENSIBILIDADE PRESERVADA (GRAU 0)"

                // Caixa de Risco
                val boxPaint = Paint().apply { color = riskColor; style = Paint.Style.FILL }
                canvas.drawRect(leftMargin, yPos, pageWidth - leftMargin, yPos + 50f, boxPaint)

                val riskTextPaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 18f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(riskText, pageWidth / 2f, yPos + 32f, riskTextPaint)
                yPos += 90f

                // 5. Detalhamento
                canvas.drawText("DETALHAMENTO POR MEMBRO", leftMargin, yPos, labelPaint.apply { textSize = 16f; color = android.graphics.Color.rgb(0, 172, 193) })
                yPos += 30f

                data.forEach { test ->
                    // Nova página se necessário
                    if (yPos > pageHeight - 100) {
                        pdfDocument.finishPage(page)
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPos = 50f
                    }

                    val rawPart = test["bodyPart"].toString()
                    val partName = rawPart.replace("_", " ").uppercase()

                    // Fundo cinza claro para o título do membro
                    val partBgPaint = Paint().apply { color = android.graphics.Color.rgb(240, 240, 240) }
                    canvas.drawRect(leftMargin, yPos - 15f, pageWidth - leftMargin, yPos + 10f, partBgPaint)

                    canvas.drawText(partName, leftMargin + 10f, yPos, labelPaint.apply { color = android.graphics.Color.BLACK })
                    yPos += 25f

                    val points = test["pointsData"] as? Map<String, Any>
                    points?.toSortedMap()?.forEach { (k, v) ->
                        val pLevel = v.toString().toIntOrNull() ?: 0
                        val pDesc = ClinicalScale.getResult(pLevel).description

                        // Cor do texto baseada na gravidade
                        val pointColor = if (pLevel >= 3) android.graphics.Color.RED else android.graphics.Color.BLACK

                        canvas.drawText("Ponto $k:", leftMargin + 20f, yPos, labelPaint)
                        val descPaint = Paint().apply { color = pointColor; textSize = 12f }
                        canvas.drawText("Nível $v - $pDesc", leftMargin + 100f, yPos, descPaint)
                        yPos += 20f
                    }
                    yPos += 20f
                }

                pdfDocument.finishPage(page)

                // Salvar
                val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fileName = "EstesioTech_${name.take(5)}_${System.currentTimeMillis()}.pdf"
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
                    startActivity(Intent.createChooser(shareIntent, "Compartilhar Relatório"))
                } else {
                    Toast.makeText(this, "Salvo em Downloads", Toast.LENGTH_LONG).show()
                }

                onComplete(file)
            } catch (e: Exception) {
                Toast.makeText(this, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
                onComplete(null)
            }
        }, onError = {
            Toast.makeText(this, "Erro ao baixar dados: $it", Toast.LENGTH_SHORT).show()
            onComplete(null)
        })
    }
}

@Composable
fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onLoadingChange: (String?) -> Unit, onGeneratePDF: (String, String, String, String) -> Unit) {
    var patientsList by remember { mutableStateOf<List<PatientHistoryData>>(emptyList()) }
    var isInitialLoading by remember { mutableStateOf(true) }
    val colors = MaterialTheme.colorScheme

    // Função para recarregar lista
    fun loadList() {
        EstesioCloud.getGroupedHistory(
            onSuccess = {
                patientsList = it
                isInitialLoading = false
            },
            onError = { isInitialLoading = false }
        )
    }

    LaunchedEffect(Unit) { loadList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Histórico de Pacientes", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Voltar") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                    titleContentColor = colors.onSurface,
                    navigationIconContentColor = colors.onSurface
                )
            )
        },
        containerColor = colors.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (isInitialLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.primary) }
            } else if (patientsList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, null, tint = colors.onSurface.copy(alpha=0.5f), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Nenhum histórico encontrado.", color = colors.onSurface.copy(alpha=0.7f))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(patientsList) { patient ->
                        PatientHistoryCard(
                            patient = patient,
                            onGeneratePDF = onGeneratePDF,
                            onDeleteSession = { sessionId ->
                                onLoadingChange("Excluindo...")
                                EstesioCloud.deleteSession(sessionId,
                                    onSuccess = {
                                        onLoadingChange(null)
                                        loadList()
                                    },
                                    onError = { onLoadingChange(null) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PatientHistoryCard(
    patient: PatientHistoryData,
    onGeneratePDF: (String, String, String, String) -> Unit,
    onDeleteSession: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.colorScheme

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Excluir Sessão?", color = colors.onSurface) },
            text = { Text("Tem certeza que deseja apagar este registro? Esta ação não pode ser desfeita.", color = colors.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    sessionToDelete?.let { onDeleteSession(it) }
                    showDeleteDialog = false
                }) { Text("Excluir", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar", color = colors.primary) }
            },
            containerColor = colors.surface
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Cabeçalho do Paciente
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (patient.name.isNotEmpty()) patient.name.first().toString().uppercase() else "?",
                            color = colors.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(patient.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                        Text("CPF: ${patient.cpf}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null,
                    tint = colors.onSurfaceVariant
                )
            }

            // Lista de Sessões (Expandível)
            if (expanded) {
                Spacer(Modifier.height(16.dp))
                Divider(color = colors.outlineVariant)
                Spacer(Modifier.height(8.dp))

                Text("Atendimentos Realizados:", style = MaterialTheme.typography.labelMedium, color = colors.primary)

                patient.sessions.forEach { session ->
                    val dateStr = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(session.date)

                    // Linha da Sessão
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(colors.surfaceVariant.copy(alpha=0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(dateStr, color = colors.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(if(session.maxGif > 0) "Risco Detectado" else "Sem Risco", fontSize = 12.sp, color = if(session.maxGif > 0) colors.error else Color(0xFF2E7D32))
                        }

                        // Botões de Ação
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Baixar
                            IconButton(
                                onClick = { onGeneratePDF(session.sessionId, patient.name, patient.cpf, "download") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Download, "Baixar", tint = colors.primary, modifier = Modifier.size(20.dp))
                            }

                            // Compartilhar (ADICIONADO)
                            IconButton(
                                onClick = { onGeneratePDF(session.sessionId, patient.name, patient.cpf, "share") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Share, "Compartilhar", tint = colors.secondary, modifier = Modifier.size(20.dp))
                            }

                            // Deletar
                            IconButton(
                                onClick = {
                                    sessionToDelete = session.sessionId
                                    showDeleteDialog = true
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Excluir", tint = colors.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- PREVIEW ---
@Preview(showBackground = true)
@Composable
fun PatientHistoryCardPreview() {
    val mockSession = SessionData("1", Date(), 0)
    val mockPatient = PatientHistoryData("Maria Silva", "123.456.789-00", listOf(mockSession))

    EstesioTechTheme(darkTheme = true) {
        PatientHistoryCard(
            patient = mockPatient,
            onGeneratePDF = { _, _, _, _ -> },
            onDeleteSession = {}
        )
    }
}