package com.code.EstesioTech

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Locale

object PdfUtil {
    fun sharePdf(context: Context, name: String, cpf: String, tests: List<Map<String, Any>>) {
        val sb = StringBuilder()
        sb.append("RELATÓRIO DE ESTESIOMETRIA - ESTESIOTECH (SUS)\n\n")
        sb.append("PACIENTE: $name\n")
        sb.append("CPF: $cpf\n")
        sb.append("Data de Emissão: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(java.util.Date())}\n\n")
        sb.append("--------------------------------------------------\n")
        sb.append("HISTÓRICO DE EXAMES REALIZADOS\n")
        sb.append("--------------------------------------------------\n\n")

        val sortedTests = tests.sortedByDescending { 
            (it["date"] as? com.google.firebase.Timestamp)?.toDate() ?: java.util.Date(0) 
        }

        sortedTests.forEach { test ->
            val partRaw = test["bodyPart"] as? String ?: "Membro"
            val part = partRaw.replace("_", " ").uppercase()
            val gif = test["gif"].toString()
            val dateRaw = test["date"]
            val date = (dateRaw as? com.google.firebase.Timestamp)?.toDate() ?: java.util.Date()
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
            
            sb.append("DATA: $dateStr\n")
            sb.append("MEMBRO: $part\n")
            sb.append("RESULTADO (GIF): Grau $gif\n")
            sb.append("--------------------------------------------------\n")
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822" // Tenta abrir cliente de email
            putExtra(Intent.EXTRA_SUBJECT, "Relatório Estesiometria - $name")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Enviar Relatório por Email"))
        } catch (e: Exception) {
            // Fallback genérico se não tiver email configurado
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Relatório - $name")
                putExtra(Intent.EXTRA_TEXT, sb.toString())
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Relatório"))
        }
    }
}