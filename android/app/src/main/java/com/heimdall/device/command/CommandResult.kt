package com.heimdall.device.command

import org.json.JSONObject

/**
 * Resultado do processamento de um comando
 */
data class CommandResult(
    val commandId: String,
    val command: String,
    val status: CommandStatus,
    val message: String,
    val data: Map<String, Any> = emptyMap(),
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val traceId: String? = null
) {
    enum class CommandStatus {
        SUCCESS,
        ERROR,
        IN_PROGRESS,
        REJECTED
    }
    
    fun toJson(): String {
        return JSONObject().apply {
            put("command_id", commandId)
            put("command", command)
            put("status", status.name.lowercase())
            put("message", message)
            put("timestamp", timestamp)
            traceId?.let { put("trace_id", it) }
            error?.let { put("error", it) }
            if (data.isNotEmpty()) {
                val dataObj = JSONObject()
                data.forEach { (key, value) ->
                    when (value) {
                        is String -> dataObj.put(key, value)
                        is Number -> dataObj.put(key, value)
                        is Boolean -> dataObj.put(key, value)
                        else -> dataObj.put(key, value.toString())
                    }
                }
                put("data", dataObj)
            }
        }.toString()
    }
}


