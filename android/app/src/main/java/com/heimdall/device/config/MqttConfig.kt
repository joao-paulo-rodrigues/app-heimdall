package com.heimdall.device.config

object MqttConfig {
    const val BROKER_HOST = "177.87.122.5"
    const val BROKER_PORT = 1883
    const val USERNAME = "mosquitto_broker_user_ue"
    const val PASSWORD = "tiue@Mosquitto2025#"
    const val CLIENT_ID_PREFIX = "heimdall_device_"
    const val KEEP_ALIVE_INTERVAL = 60
    const val CONNECTION_TIMEOUT = 30
    const val CLEAN_SESSION = false
    const val AUTO_RECONNECT = true
    const val MAX_INFLIGHT = 10
    
    // Topics
    const val TOPIC_PREFIX = "v1/heimdall"
    const val TOPIC_TELEMETRY_LOGS = "$TOPIC_PREFIX/telemetry/logs"
    const val TOPIC_TELEMETRY_STATUS = "$TOPIC_PREFIX/telemetry/status"
    
    fun getCommandTopic(tenantId: String, deviceId: String): String {
        return "$TOPIC_PREFIX/tenants/$tenantId/devices/$deviceId/cmd"
    }
    
    fun getAckTopic(tenantId: String, deviceId: String): String {
        return "$TOPIC_PREFIX/tenants/$tenantId/devices/$deviceId/ack"
    }
    
    fun getStatusTopic(tenantId: String, deviceId: String): String {
        return "$TOPIC_PREFIX/tenants/$tenantId/devices/$deviceId/status"
    }
    
    fun getAuditTopic(tenantId: String, deviceId: String): String {
        return "$TOPIC_PREFIX/tenants/$tenantId/devices/$deviceId/audit"
    }
}


