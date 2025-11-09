package com.heimdall.device.command

import org.json.JSONObject

/**
 * ACK (Acknowledgment) de comando
 * Enviado imediatamente após receber um comando para confirmar recebimento
 */
data class CommandAck(
    val commandId: String,
    val command: String,
    val ackType: AckType,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val traceId: String? = null
) {
    enum class AckType {
        RECEIVED,      // Comando recebido e será processado
        PROCESSING,    // Comando em processamento
        REJECTED       // Comando rejeitado (formato inválido, etc)
    }
    
    fun toJson(): String {
        return JSONObject().apply {
            put("command_id", commandId)
            put("command", command)
            put("ack_type", ackType.name.lowercase())
            put("timestamp", timestamp)
            traceId?.let { put("trace_id", it) }
            message?.let { put("message", it) }
        }.toString()
    }
}


