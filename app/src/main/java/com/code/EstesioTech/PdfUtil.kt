package com.code.EstesioTech

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.code.EstesioTech.data.cloud.EstesioCloud
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilitario para geracao e compartilhamento de PDF de laudos clinicos.
 *
 * Centraliza a logica de PDF que antes estava duplicada em HistoryActivity
 * e SelectionActivity. Agora ambas chamam este objeto.
 *
 * Estrutura do PDF gerado:
 *  1. Cabecalho azul-escuro com titulo
 *  2. Dados do paciente (nome, CPF, data, hora)
 *  3. Caixa de resultado geral (verde = sem risco, vermelho = com risco)
 *  4. Detalhamento por membro com nivel de cada ponto
 *
 * Uso:
 *   PdfUtil.generateAndShare(context, sessionId, name, cpf, "share") { file ->
 *       // file != null se gerou com sucesso
 *   }
 */
object PdfUtil {

    private const val PAGE_WIDTH  = 595  // A4 portrait em points (72 dpi)
    private const val PAGE_HEIGHT = 842
    private const val LEFT_MARGIN = 40f

    /**
     * Gera o PDF do laudo e executa a acao solicitada (share ou download).
     *
     * @param context   Contexto da Activity (necessario para Toast e FileProvider)
     * @param sessionId ID da sessao para buscar os dados no Firestore
     * @param name      Nome do paciente para o arquivo e o laudo
     * @param cpf       CPF do paciente para exibicao no laudo
     * @param action    "share" -> abre seletor de apps | "download" -> salva em Downloads
     * @param onComplete Callback com o File gerado (ou null em caso de erro)
     */
    fun generateAndShare(
        context: Context,
        sessionId: String,
        name: String,
        cpf: String,
        action: String,
        onComplete: (File?) -> Unit
    ) {
        EstesioCloud.getFullSessionData(
            sessionId,
            onSuccess = { data ->
                try {
                    val file = buildPdf(name, cpf, data)
                    when (action) {
                        "share" -> shareFile(context, file)
                        else    -> Toast.makeText(context, "Salvo em Downloads", Toast.LENGTH_LONG).show()
                    }
                    onComplete(file)
                } catch (e: Exception) {
                    Toast.makeText(context, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                    onComplete(null)
                }
            },
            onError = { err ->
                Toast.makeText(context, "Erro ao baixar dados: $err", Toast.LENGTH_SHORT).show()
                onComplete(null)
            }
        )
    }

    // Compartilha o arquivo via seletor de apps
    private fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.applicationContext.packageName}.provider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar Relatorio"))
    }

    // Constroi o PDF e retorna o File salvo em Downloads
    private fun buildPdf(
        name: String,
        cpf: String,
        data: List<Map<String, Any>>
    ): File {
        val doc      = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page     = doc.startPage(pageInfo)
        var canvas   = page.canvas

        val ptBR = Locale("pt", "BR")
        val paint = Paint()

        // -- Cabecalho --
        paint.color = android.graphics.Color.rgb(26, 38, 52)
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 100f, paint)

        paint.apply {
            color        = android.graphics.Color.WHITE
            textSize     = 26f
            isFakeBoldText = true
            textAlign    = Paint.Align.CENTER
        }
        canvas.drawText("RELATORIO ESTESIOTECH", PAGE_WIDTH / 2f, 60f, paint)

        // -- Dados do paciente --
        val labelPaint = Paint().apply {
            color = android.graphics.Color.DKGRAY; textSize = 12f; isFakeBoldText = true
        }
        val valuePaint = Paint().apply { color = android.graphics.Color.BLACK; textSize = 14f }
        val linePaint  = Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 1f }
        var yPos       = 140f

        fun sectionTitle(text: String) {
            labelPaint.textSize = 16f
            labelPaint.color    = android.graphics.Color.rgb(0, 172, 193)
            canvas.drawText(text, LEFT_MARGIN, yPos, labelPaint)
            yPos += 30f
            labelPaint.textSize = 12f
            labelPaint.color    = android.graphics.Color.DKGRAY
        }

        fun labelValue(label: String, value: String) {
            canvas.drawText(label, LEFT_MARGIN, yPos, labelPaint)
            canvas.drawText(value, LEFT_MARGIN + 100f, yPos, valuePaint)
            yPos += 25f
        }

        sectionTitle("DADOS DO PACIENTE")
        labelValue("NOME:",     name)
        labelValue("CPF:",      cpf)
        labelValue("DATA:",     SimpleDateFormat("dd 'de' MMMM 'de' yyyy", ptBR).format(Date()))
        labelValue("HORARIO:", SimpleDateFormat("HH:mm", ptBR).format(Date()))

        yPos += 15f
        canvas.drawLine(LEFT_MARGIN, yPos, PAGE_WIDTH - LEFT_MARGIN, yPos, linePaint)
        yPos += 30f

        // -- Resultado geral --
        val maxGif   = data.maxOfOrNull { (it["gif"] as? Long)?.toInt() ?: 0 } ?: 0
        val riskColor = if (maxGif > 0) android.graphics.Color.rgb(183, 28, 28)
        else android.graphics.Color.rgb(27, 94, 32)
        val riskText  = if (maxGif > 0) "RISCO IDENTIFICADO (GRAU $maxGif)"
        else "SENSIBILIDADE PRESERVADA (GRAU 0)"

        paint.apply { color = riskColor; style = Paint.Style.FILL }
        canvas.drawRect(LEFT_MARGIN, yPos, PAGE_WIDTH - LEFT_MARGIN, yPos + 50f, paint)
        paint.apply {
            color = android.graphics.Color.WHITE; textSize = 17f; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(riskText, PAGE_WIDTH / 2f, yPos + 32f, paint)
        yPos += 80f

        // -- Detalhamento por membro --
        sectionTitle("DETALHAMENTO POR MEMBRO")

        data.forEach { test ->
            if (yPos > PAGE_HEIGHT - 120) {
                doc.finishPage(page)
                page   = doc.startPage(pageInfo)
                canvas = page.canvas
                yPos   = 50f
            }

            val partName = test["bodyPart"].toString().replace("_", " ").uppercase(ptBR)
            paint.apply { color = android.graphics.Color.rgb(240, 240, 240); style = Paint.Style.FILL }
            canvas.drawRect(LEFT_MARGIN, yPos - 15f, PAGE_WIDTH - LEFT_MARGIN, yPos + 10f, paint)

            labelPaint.apply { color = android.graphics.Color.BLACK; textSize = 13f; isFakeBoldText = true }
            canvas.drawText(partName, LEFT_MARGIN + 10f, yPos, labelPaint)
            yPos += 25f

            val points = test["pointsData"] as? Map<*, *>
            points?.entries
                ?.sortedBy { it.key.toString().toIntOrNull() ?: 0 }
                ?.forEach { (k, v) ->
                    val level = v.toString().toIntOrNull() ?: 0
                    val result = ClinicalScale.getResult(level)
                    val pColor = if (level >= 3) android.graphics.Color.rgb(183, 28, 28)
                    else android.graphics.Color.rgb(27, 94, 32)
                    labelPaint.apply { color = android.graphics.Color.DKGRAY; textSize = 11f; isFakeBoldText = false }
                    canvas.drawText("Ponto $k:", LEFT_MARGIN + 20f, yPos, labelPaint)
                    val descPaint = Paint().apply { color = pColor; textSize = 11f }
                    canvas.drawText("Nivel $level - ${result.description}", LEFT_MARGIN + 100f, yPos, descPaint)
                    yPos += 20f
                }
            yPos += 15f
        }

        doc.finishPage(page)

        val dir      = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val safeName = name.take(8).replace(" ", "_")
        val file     = File(dir, "EstesioTech_${safeName}_${System.currentTimeMillis()}.pdf")
        doc.writeTo(FileOutputStream(file))
        doc.close()
        return file
    }
}