package com.heimdall.device.audit

import android.content.Context
import com.heimdall.device.config.MqttConfig
import com.heimdall.device.service.MqttServiceManager
import com.heimdall.device.util.Logger
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * Gerencia publicação de eventos de auditoria no tópico MQTT
 */
class AuditManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AuditManager? = null
        
        fun getInstance(context: Context): AuditManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuditManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Publica evento de auditoria
     */
    fun publishAuditEvent(
        eventType: String,
        message: String,
        metadata: Map<String, Any>? = null
    ) {
        scope.launch {
            try {
                val auditEvent = JSONObject().apply {
                    put("timestamp", System.currentTimeMillis())
                    put("event_type", eventType)
                    put("message", message)
                    put("device_id", Logger.getDeviceId())
                    put("tenant_id", "UEBRASIL")
                    
                    if (metadata != null && metadata.isNotEmpty()) {
                        val metadataObj = JSONObject()
                        metadata.forEach { (key, value) ->
                            when (value) {
                                is String -> metadataObj.put(key, value)
                                is Number -> metadataObj.put(key, value)
                                is Boolean -> metadataObj.put(key, value)
                                is List<*> -> metadataObj.put(key, value.toString())
                                else -> metadataObj.put(key, value.toString())
                            }
                        }
                        put("metadata", metadataObj)
                    }
                }
                
                val topic = MqttConfig.getAuditTopic("UEBRASIL", Logger.getDeviceId())
                
                Logger.info(
                    component = "heimdall.device.audit",
                    message = "Publishing audit event",
                    metadata = mapOf(
                        "event_type" to eventType,
                        "topic" to topic
                    )
                )
                
                MqttServiceManager.getInstance(context).publish(
                    topic = topic,
                    payload = auditEvent.toString(),
                    qos = 1
                )
                
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.audit",
                    message = "Failed to publish audit event",
                    metadata = mapOf("event_type" to eventType),
                    throwable = e
                )
            }
        }
    }
}

