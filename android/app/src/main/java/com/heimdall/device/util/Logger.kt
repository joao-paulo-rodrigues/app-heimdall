package com.heimdall.device.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object Logger {
    private const val TAG = "Heimdall"
    private const val LOG_DIR = "heimdall_logs"
    private const val LOG_FILE = "heimdall.log"
    
    private var context: Context? = null
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private var deviceId: String = "UNKNOWN"
    private var tenantId: String = "UNKNOWN"

    fun initialize(context: Context) {
        this.context = context
        // Usar IMEI como device ID (gerado ou real)
        this.deviceId = ImeiUtils.getOrGenerateImei(context)
    }

    fun setTenantId(tenantId: String) {
        this.tenantId = tenantId
    }

    private fun log(
        level: String,
        component: String,
        message: String,
        metadata: Map<String, Any>? = null,
        traceId: String? = null
    ) {
        val logEntry = JSONObject().apply {
            put("timestamp", dateFormatter.format(Date()))
            put("level", level)
            put("component", component)
            put("device_id", deviceId)
            put("tenant_id", tenantId)
            put("trace_id", traceId ?: UUID.randomUUID().toString())
            put("message", message)
            
            if (metadata != null && metadata.isNotEmpty()) {
                val metadataObj = JSONObject()
                metadata.forEach { (key, value) ->
                    when (value) {
                        is String -> metadataObj.put(key, value)
                        is Number -> metadataObj.put(key, value)
                        is Boolean -> metadataObj.put(key, value)
                        else -> metadataObj.put(key, value.toString())
                    }
                }
                put("metadata", metadataObj)
            }
        }

        val logLine = logEntry.toString()
        
        when (level) {
            "DEBUG" -> Log.d(TAG, logLine)
            "INFO" -> Log.i(TAG, logLine)
            "WARNING" -> Log.w(TAG, logLine)
            "ERROR" -> Log.e(TAG, logLine)
            "CRITICAL" -> Log.wtf(TAG, logLine)
        }

        writeToFile(logLine)
    }

    private fun writeToFile(logLine: String) {
        try {
            val context = this.context ?: return
            val logDir = File(context.filesDir, LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            val logFile = File(logDir, LOG_FILE)
            logFile.appendText("$logLine\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    fun debug(component: String, message: String, metadata: Map<String, Any>? = null, traceId: String? = null) {
        log("DEBUG", component, message, metadata, traceId)
    }

    fun info(component: String, message: String, metadata: Map<String, Any>? = null, traceId: String? = null) {
        log("INFO", component, message, metadata, traceId)
    }

    fun warning(component: String, message: String, metadata: Map<String, Any>? = null, traceId: String? = null) {
        log("WARNING", component, message, metadata, traceId)
    }

    fun error(component: String, message: String, metadata: Map<String, Any>? = null, traceId: String? = null, throwable: Throwable? = null) {
        val errorMetadata = metadata?.toMutableMap() ?: mutableMapOf()
        throwable?.let {
            errorMetadata["error"] = it.message ?: "Unknown error"
            errorMetadata["stack_trace"] = it.stackTraceToString()
        }
        log("ERROR", component, message, errorMetadata, traceId)
    }

    fun critical(component: String, message: String, metadata: Map<String, Any>? = null, traceId: String? = null, throwable: Throwable? = null) {
        val errorMetadata = metadata?.toMutableMap() ?: mutableMapOf()
        throwable?.let {
            errorMetadata["error"] = it.message ?: "Unknown error"
            errorMetadata["stack_trace"] = it.stackTraceToString()
        }
        log("CRITICAL", component, message, errorMetadata, traceId)
    }

    fun getDeviceId(): String = deviceId
}

