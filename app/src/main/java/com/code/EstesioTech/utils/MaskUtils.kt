package com.code.EstesioTech.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Aplica mascara de CPF (XXX.XXX.XXX-XX) visualmente no TextField.
 * O valor interno permanece apenas com digitos (LGPD: sem formatacao interna).
 *
 * Uso:
 *   OutlinedTextField(
 *     value = cpf,  // "12345678901" (apenas digitos)
 *     visualTransformation = CpfVisualTransformation()
 *   )
 */
class CpfVisualTransformation : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        // Limita a 11 digitos (tamanho maximo do CPF sem formatacao)
        val trimmed = if (text.text.length >= 11) text.text.substring(0..10) else text.text
        val out = buildString {
            for (i in trimmed.indices) {
                append(trimmed[i])
                if (i == 2 || i == 5) append(".")
                if (i == 8) append("-")
            }
        }

        val offsetTranslator = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2  -> offset
                offset <= 5  -> offset + 1
                offset <= 8  -> offset + 2
                offset <= 11 -> offset + 3
                else         -> 14
            }
            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 3  -> offset
                offset <= 7  -> offset - 1
                offset <= 11 -> offset - 2
                offset <= 14 -> offset - 3
                else         -> 11
            }
        }

        return TransformedText(AnnotatedString(out), offsetTranslator)
    }
}
