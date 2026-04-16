package com.code.EstesioTech

import androidx.compose.ui.graphics.Color

/**
 * Modelo de resultado clinico para um nivel de sensibilidade.
 *
 * @param level          Nivel numerico (0-6)
 * @param color          Cor de representacao visual do nivel
 * @param force          Forca aplicada pelo filamento (ex: "0,05 g")
 * @param description    Descricao tecnica curta do nivel
 * @param interpretation Orientacao clinica sobre o resultado
 * @param riskIcon       Emoji de icone de risco para exibicao rapida
 */
data class ClinicalResult(
    val level: Int,
    val color: Color,
    val force: String,
    val description: String,
    val interpretation: String,
    val riskIcon: String = ""
)

/**
 * Escala clinica de sensibilidade baseada no protocolo de Semmes-Weinstein
 * adaptado para o Estesiometro Digital EstesioTech.
 *
 * Niveis:
 *  0 -> Aguardando (nenhum dado recebido ainda)
 *  1 -> Verde  : 0,05 g  - Sensibilidade normal
 *  2 -> Azul   : 0,20 g  - Sensibilidade diminuida leve
 *  3 -> Violeta: 2,00 g  - Perda da sensibilidade protetora
 *  4 -> Vermelho: 4,00 g - Perda severa / risco de lesao
 *  5 -> Laranja: 10,00 g - Perda profunda / alto risco de ulcera
 *  6 -> Magenta: 300,0 g - Perda total / apenas dor profunda
 */
object ClinicalScale {

    fun getResult(level: Int): ClinicalResult = when (level) {
        1    -> ClinicalResult(1, Color(0xFF4CAF50), "0,05 g",  "Sensibilidade Normal",             "Sem perda sensitiva detectada.",              "")
        2    -> ClinicalResult(2, Color(0xFF2196F3), "0,2 g",   "Sensibilidade Diminuida",           "Perda leve. Ainda possui sensibilidade protetora.", "")
        3    -> ClinicalResult(3, Color(0xFF9C27B0), "2,0 g",   "Perda Protetora Leve",              "Atencao: inicio de perda protetora.", "")
        4    -> ClinicalResult(4, Color(0xFFF44336), "4,0 g",   "Perda da Sensibilidade Protetora",  "RISCO: Propenso a lesoes sem percepcao.",     "")
        5    -> ClinicalResult(5, Color(0xFFFF9800), "10,0 g",  "Perda Severa / Alto Risco",         "ALTO RISCO: Forte probabilidade de ulceras.",  "")
        6, 7 -> ClinicalResult(6, Color(0xFFE91E63), "300,0 g", "Perda Total / Apenas Dor Profunda", "CRITICO: Apenas dor profunda percebida.",       "")
        else -> ClinicalResult(0, Color(0xFF9E9E9E), "-",       "Aguardando Leitura",                "Toque o ponto para iniciar a medicao.",         "")
    }

    /**
     * Calcula o nivel de risco geral de uma sessao a partir de todos os resultados.
     * Usado para o indicador visual em tempo real na TesteActivity.
     *
     * @param results Map de ponto -> nivel de sensibilidade
     * @return Par (nivel_max, ClinicalResult) representando o pior resultado da sessao
     */
    fun getSessionRisk(results: Map<Int, Int>): ClinicalResult {
        if (results.isEmpty()) return getResult(0)
        val maxLevel = results.values.maxOrNull() ?: 0
        return getResult(maxLevel)
    }

    /**
     * Calcula a porcentagem de conclusao do exame.
     * @param completed Numero de pontos avaliados
     * @param total     Total de pontos da regiao (6 para maos, 9 para pes)
     */
    fun getProgress(completed: Int, total: Int): Float =
        if (total == 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
