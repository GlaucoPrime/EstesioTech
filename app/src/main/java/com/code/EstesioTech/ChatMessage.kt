package com.code.EstesioTech

/**
 * Modelo de mensagem para o terminal BLE (DeviceControlActivity).
 * Cada mensagem tem um tipo que define sua aparencia visual no chat.
 *
 * TYPE_SENT     -> Mensagem enviada pelo medico (direita, cor primaria)
 * TYPE_RECEIVED -> Dado recebido do ESP32 (esquerda, cor secundaria)
 * TYPE_SYSTEM   -> Status do sistema: conectando, erro etc. (centro, opaco)
 */
data class ChatMessage(
    val message: String,
    val type: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_SENT     = 0
        const val TYPE_RECEIVED = 1
        const val TYPE_SYSTEM   = 2
    }
}
