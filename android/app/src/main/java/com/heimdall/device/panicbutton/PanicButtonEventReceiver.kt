package com.heimdall.device.panicbutton

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.heimdall.device.service.MqttServiceManager
import com.heimdall.device.util.Logger
import org.json.JSONObject

/**
 * BroadcastReceiver para receber eventos do PanicButton
 * Registrado dinamicamente pelo PanicButtonCommunicationManager
 */
class PanicButtonEventReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        Logger.info(
            component = "heimdall.device.panicbutton.receiver",
            message = "Event received from PanicButton",
            metadata = mapOf("action" to action)
        )
        
        when (action) {
            PanicButtonCommunicationManager.ACTION_PANIC_TRIGGERED -> {
                handlePanicTriggered(context, intent)
            }
            PanicButtonCommunicationManager.ACTION_STATUS_UPDATE -> {
                handleStatusUpdate(context, intent)
            }
            PanicButtonCommunicationManager.ACTION_SENSOR_DATA -> {
                handleSensorData(context, intent)
            }
        }
    }
    
    private fun handlePanicTriggered(context: Context, intent: Intent) {
        try {
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            val location = intent.getStringExtra("location")
            val sensorData = intent.getStringExtra("sensor_data")
            
            val eventData = JSONObject().apply {
                put("event", "panic_triggered")
                put("timestamp", timestamp)
                location?.let { put("location", it) }
                sensorData?.let { put("sensor_data", it) }
            }
            
            // Publicar evento via MQTT
            val mqttManager = MqttServiceManager.getInstance(context)
            mqttManager.publishPanicEvent(eventData.toString())
            
            Logger.critical(
                component = "heimdall.device.panicbutton.receiver",
                message = "Panic button triggered",
                metadata = mapOf(
                    "timestamp" to timestamp,
                    "location" to (location ?: "unknown")
                )
            )
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.panicbutton.receiver",
                message = "Error handling panic event",
                throwable = e
            )
        }
    }
    
    private fun handleStatusUpdate(context: Context, intent: Intent) {
        try {
            val status = intent.getStringExtra("status") ?: return
            val data = intent.getStringExtra("data")
            
            Logger.info(
                component = "heimdall.device.panicbutton.receiver",
                message = "PanicButton status update",
                metadata = mapOf("status" to status)
            )
            
            // Publicar status via MQTT se necessário
            if (status == "error" || status == "critical") {
                val mqttManager = MqttServiceManager.getInstance(context)
                val statusData = JSONObject().apply {
                    put("status", status)
                    data?.let { put("data", it) }
                }
                mqttManager.publishStatus(statusData.toString())
            }
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.panicbutton.receiver",
                message = "Error handling status update",
                throwable = e
            )
        }
    }
    
    private fun handleSensorData(context: Context, intent: Intent) {
        try {
            val sensorData = intent.getStringExtra("sensor_data") ?: return
            
            Logger.debug(
                component = "heimdall.device.panicbutton.receiver",
                message = "Sensor data received from PanicButton"
            )
            
            // Publicar dados de sensores via MQTT (telemetria)
            val mqttManager = MqttServiceManager.getInstance(context)
            mqttManager.publishTelemetry(sensorData)
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.panicbutton.receiver",
                message = "Error handling sensor data",
                throwable = e
            )
        }
    }
}


