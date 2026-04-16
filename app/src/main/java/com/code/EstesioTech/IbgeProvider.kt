package com.code.EstesioTech

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Modelo de dados para um estado brasileiro.
 *
 * @param id    ID IBGE do estado
 * @param sigla Sigla de 2 letras (ex: "PE", "SP")
 * @param nome  Nome completo (ex: "Pernambuco")
 */
data class IbgeState(
    val id: Int,
    val sigla: String,
    val nome: String
)

/**
 * Provedor de estados brasileiros via API publica do IBGE.
 *
 * Endpoint: https://servicodados.ibge.gov.br/api/v1/localidades/estados
 *
 * Cache simples em memoria: a lista e buscada apenas uma vez por sessao
 * para nao sobrecarregar a API publica.
 *
 * Fallback estatico: caso a API falhe (sem internet), retorna a lista
 * completa de UFs hardcoded para que o app nao quebre.
 */
object IbgeProvider {

    private const val TAG = "IbgeProvider"
    private const val IBGE_URL =
        "https://servicodados.ibge.gov.br/api/v1/localidades/estados?orderBy=nome"
    private const val TIMEOUT_MS = 6000

    /** Cache em memoria — null indica que ainda nao foi buscado */
    private var cachedStates: List<IbgeState>? = null

    /**
     * Retorna a lista de estados brasileiros, com cache e fallback.
     *
     * Executa em Dispatchers.IO (nao bloqueia a main thread).
     * Sempre retorna pelo menos os estados do fallback estatico.
     */
    suspend fun getBrazilianStates(): List<IbgeState> {
        cachedStates?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val connection = (URL(IBGE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod  = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout    = TIMEOUT_MS
                    setRequestProperty("Accept", "application/json")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json   = connection.inputStream.bufferedReader().use { it.readText() }
                    val array  = JSONArray(json)
                    val states = mutableListOf<IbgeState>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        states.add(
                            IbgeState(
                                id    = obj.getInt("id"),
                                sigla = obj.getString("sigla"),
                                nome  = obj.getString("nome")
                            )
                        )
                    }
                    // Ordena por sigla para facilitar busca visual
                    val sorted = states.sortedBy { it.sigla }
                    cachedStates = sorted
                    sorted
                } else {
                    Log.w(TAG, "IBGE respondeu com codigo: ${connection.responseCode}")
                    getFallbackStates()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao buscar estados IBGE: ${e.message}")
                getFallbackStates()
            }
        }
    }

    /** Limpa o cache (util se o usuario ficar offline e depois voltar) */
    fun clearCache() { cachedStates = null }

    /**
     * Lista estatica completa das 27 UFs brasileiras.
     * Usada como fallback quando a API do IBGE nao esta disponivel.
     */
    private fun getFallbackStates(): List<IbgeState> {
        val siglas = listOf(
            "AC" to "Acre",           "AL" to "Alagoas",         "AP" to "Amapa",
            "AM" to "Amazonas",       "BA" to "Bahia",           "CE" to "Ceara",
            "DF" to "Distrito Federal","ES" to "Espirito Santo",  "GO" to "Goias",
            "MA" to "Maranhao",       "MT" to "Mato Grosso",     "MS" to "Mato Grosso do Sul",
            "MG" to "Minas Gerais",   "PA" to "Para",            "PB" to "Paraiba",
            "PR" to "Parana",         "PE" to "Pernambuco",      "PI" to "Piaui",
            "RJ" to "Rio de Janeiro", "RN" to "Rio Grande do Norte","RS" to "Rio Grande do Sul",
            "RO" to "Rondonia",       "RR" to "Roraima",         "SC" to "Santa Catarina",
            "SP" to "Sao Paulo",      "SE" to "Sergipe",         "TO" to "Tocantins"
        )
        return siglas.mapIndexed { idx, (sigla, nome) ->
            IbgeState(id = idx + 1, sigla = sigla, nome = nome)
        }
    }
}
