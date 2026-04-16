package com.code.EstesioTech

/**
 * Cache temporário de resultados de uma sessão de exame.
 * Limpo no início de cada SelectionActivity para evitar contaminação
 * entre sessões diferentes.
 */
object SessionCache {
    var results: MutableMap<String, Map<Int, Int>> = mutableMapOf()
    var hasDeformities: Boolean = false
    fun clear() {
        results.clear()
        hasDeformities = false
    }
}