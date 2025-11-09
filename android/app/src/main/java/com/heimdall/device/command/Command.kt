package com.heimdall.device.command

import org.json.JSONObject

/**
 * Modelo de comando recebido via MQTT
 */
data class Command(
    val commandId: String,
    val command: String,
    val params: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val traceId: String? = null,
    val tenantId: String? = null
) {
    companion object {
        fun fromJson(json: String): Command? {
            return try {
                val obj = JSONObject(json)
                val paramsObj = obj.optJSONObject("params")
                val params = mutableMapOf<String, Any>()
                
                paramsObj?.keys()?.forEach { key ->
                    params[key] = paramsObj.get(key)
                }
                
                Command(
                    commandId = obj.getString("command_id"),
                    command = obj.getString("command"),
                    params = params,
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    traceId = obj.optString("trace_id", null),
                    tenantId = obj.optString("tenant_id", null)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun toJson(): String {
        return JSONObject().apply {
            put("command_id", commandId)
            put("command", command)
            put("timestamp", timestamp)
            traceId?.let { put("trace_id", it) }
            tenantId?.let { put("tenant_id", it) }
            if (params.isNotEmpty()) {
                val paramsObj = JSONObject()
                params.forEach { (key, value) ->
                    when (value) {
                        is String -> paramsObj.put(key, value)
                        is Number -> paramsObj.put(key, value)
                        is Boolean -> paramsObj.put(key, value)
                        else -> paramsObj.put(key, value.toString())
                    }
                }
                put("params", paramsObj)
            }
        }.toString()
    }
}


