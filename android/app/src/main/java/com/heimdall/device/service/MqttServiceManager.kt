package com.heimdall.device.service

import android.content.Context
import com.heimdall.device.command.*
import com.heimdall.device.config.MqttConfig
import com.heimdall.device.panicbutton.PanicButtonCommunicationManager
import com.heimdall.device.util.Logger
import com.heimdall.device.command.configurePanicButtonKioskHandler
import com.heimdall.device.command.syncPanicButtonDataHandler
import com.heimdall.device.command.getPanicButtonStatusHandler
import com.heimdall.device.command.sendPanicButtonCommandHandler
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import kotlinx.coroutines.*
import java.util.UUID

class MqttServiceManager private constructor(private val context: Context) {
    
    private var mqttClient: Mqtt5AsyncClient? = null
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var deviceId: String = "UNKNOWN"
    private var tenantId: String = "UEBRASIL"
    
    // Sistema de comandos
    private val storeAndForward = StoreAndForwardManager.getInstance(context)
    private val ackManager = CommandAckManager(context, storeAndForward)
    private val retryManager = CommandRetryManager(context)
    private val commandHandler = CommandHandler(context, ackManager, retryManager, storeAndForward)
    
    private fun getDeviceId(): String {
        if (deviceId == "UNKNOWN") {
            deviceId = Logger.getDeviceId()
        }
        return deviceId
    }
    
    companion object {
        @Volatile
        private var INSTANCE: MqttServiceManager? = null
        
        fun getInstance(context: Context): MqttServiceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MqttServiceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun connect() {
        if (isConnected) {
            Logger.warning(
                component = "heimdall.device.mqtt",
                message = "MQTT client already connected"
            )
            return
        }

        scope.launch {
            try {
                val currentDeviceId = getDeviceId()
                val clientId = "${MqttConfig.CLIENT_ID_PREFIX}${currentDeviceId}_${UUID.randomUUID().toString().take(8)}"
                
                Logger.info(
                    component = "heimdall.device.mqtt",
                    message = "Attempting MQTT connection",
                    metadata = mapOf(
                        "server" to "${MqttConfig.BROKER_HOST}:${MqttConfig.BROKER_PORT}",
                        "client_id" to clientId,
                        "device_id" to currentDeviceId
                    )
                )

                mqttClient = MqttClient.builder()
                    .useMqttVersion5()
                    .identifier(clientId)
                    .serverHost(MqttConfig.BROKER_HOST)
                    .serverPort(MqttConfig.BROKER_PORT)
                    .simpleAuth()
                    .username(MqttConfig.USERNAME)
                    .password(MqttConfig.PASSWORD.toByteArray())
                    .applySimpleAuth()
                    .buildAsync()

                val connAck: Mqtt5ConnAck = mqttClient!!.connect()
                    .get() // Blocking call for simplicity

                isConnected = true
                Logger.info(
                    component = "heimdall.device.mqtt",
                    message = "MQTT connection established",
                    metadata = mapOf("client_id" to clientId)
                )
                
                // Inicializar sistema de comandos
                ackManager.initialize(mqttClient!!, tenantId, currentDeviceId)
                
                // Inicializar comunicação com PanicButton
                PanicButtonCommunicationManager.getInstance(context).initialize()
                
                // Registrar processadores de comandos
                registerCommandProcessors()
                
                // Reenviar resultados pendentes
                ackManager.retryPendingResults()
                
                subscribeToCommandTopic()
                publishStatus("online")
                
            } catch (e: Exception) {
                isConnected = false
                Logger.error(
                    component = "heimdall.device.mqtt",
                    message = "MQTT connection failed",
                    metadata = mapOf("error" to (e.message ?: "Unknown error")),
                    throwable = e
                )
                
                delay(5000)
                if (!isConnected) {
                    connect()
                }
            }
        }
    }

    private fun subscribeToCommandTopic() {
        scope.launch {
            try {
                val topic = MqttConfig.getCommandTopic(tenantId, getDeviceId())
                
                mqttClient?.subscribeWith()
                    ?.topicFilter(topic)
                    ?.qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    ?.callback { publish: Mqtt5Publish ->
                        handleCommand(publish.topic.toString(), String(publish.payloadAsBytes))
                    }
                    ?.send()
                    ?.get()
                
                Logger.info(
                    component = "heimdall.device.mqtt",
                    message = "Subscribed to command topic",
                    metadata = mapOf("topic" to topic)
                )
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.mqtt",
                    message = "Failed to subscribe to command topic",
                    throwable = e
                )
            }
        }
    }

    private fun handleCommand(topic: String, payload: String) {
        Logger.info(
            component = "heimdall.device.mqtt",
            message = "Command received via MQTT",
            metadata = mapOf("topic" to topic)
        )
        
        // Processar comando através do CommandHandler
        commandHandler.processCommand(payload)
    }
    
    /**
     * Registra processadores de comandos
     */
    private fun registerCommandProcessors() {
        // Comando de teste
        commandHandler.registerProcessor("ping") { command ->
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.SUCCESS,
                message = "Pong",
                data = mapOf("timestamp" to System.currentTimeMillis()),
                traceId = command.traceId
            )
        }
        
        // Comando para obter status do dispositivo
        commandHandler.registerProcessor("get_device_status") { command ->
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.SUCCESS,
                message = "Device status retrieved",
                data = mapOf(
                    "device_id" to getDeviceId(),
                    "tenant_id" to tenantId,
                    "mqtt_connected" to isConnected,
                    "timestamp" to System.currentTimeMillis()
                ),
                traceId = command.traceId
            )
        }
        
        // Comandos do PanicButton
        commandHandler.registerProcessor("configure_panicbutton_kiosk", configurePanicButtonKioskHandler(context))
        commandHandler.registerProcessor("sync_panicbutton_data", syncPanicButtonDataHandler(context))
        commandHandler.registerProcessor("get_panicbutton_status", getPanicButtonStatusHandler(context))
        commandHandler.registerProcessor("send_panicbutton_command", sendPanicButtonCommandHandler(context))
        
        Logger.info(
            component = "heimdall.device.mqtt",
            message = "Command processors registered"
        )
    }

    fun publishStatus(status: String) {
        if (!isConnected || mqttClient == null) {
            Logger.warning(
                component = "heimdall.device.mqtt",
                message = "Cannot publish status: not connected"
            )
            return
        }

        scope.launch {
            try {
                val currentDeviceId = getDeviceId()
                val topic = MqttConfig.getStatusTopic(tenantId, currentDeviceId)
                val payload = """{"status":"$status","device_id":"$currentDeviceId","timestamp":"${System.currentTimeMillis()}"}"""
                
                mqttClient?.publishWith()
                    ?.topic(topic)
                    ?.payload(payload.toByteArray())
                    ?.qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    ?.send()
                    ?.get()
                
                Logger.debug(
                    component = "heimdall.device.mqtt",
                    message = "Status published successfully",
                    metadata = mapOf("topic" to topic, "status" to status)
                )
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.mqtt",
                    message = "Exception while publishing status",
                    throwable = e
                )
            }
        }
    }

    fun publishLog(logEntry: String) {
        if (!isConnected || mqttClient == null) {
            return
        }

        scope.launch {
            try {
                mqttClient?.publishWith()
                    ?.topic(MqttConfig.TOPIC_TELEMETRY_LOGS)
                    ?.payload(logEntry.toByteArray())
                    ?.qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    ?.send()
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.mqtt",
                    message = "Exception while publishing log",
                    throwable = e
                )
            }
        }
    }
    
    /**
     * Publica evento de pânico do PanicButton
     */
    fun publishPanicEvent(eventData: String) {
        if (!isConnected || mqttClient == null) {
            Logger.warning(
                component = "heimdall.device.mqtt",
                message = "Cannot publish panic event: not connected"
            )
            return
        }

        scope.launch {
            try {
                val topic = "${MqttConfig.TOPIC_PREFIX}/tenants/$tenantId/devices/${getDeviceId()}/panic"
                mqttClient?.publishWith()
                    ?.topic(topic)
                    ?.payload(eventData.toByteArray())
                    ?.qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    ?.send()
                    ?.get()
                
                Logger.critical(
                    component = "heimdall.device.mqtt",
                    message = "Panic event published",
                    metadata = mapOf("topic" to topic)
                )
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.mqtt",
                    message = "Exception while publishing panic event",
                    throwable = e
                )
            }
        }
    }
    
    /**
     * Publica telemetria de sensores do PanicButton
     */
    fun publishTelemetry(telemetryData: String) {
        if (!isConnected || mqttClient == null) {
            return
        }

        scope.launch {
            try {
                val topic = "${MqttConfig.TOPIC_PREFIX}/tenants/$tenantId/devices/${getDeviceId()}/telemetry"
                mqttClient?.publishWith()
                    ?.topic(topic)
                    ?.payload(telemetryData.toByteArray())
                    ?.qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    ?.send()
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.mqtt",
                    message = "Exception while publishing telemetry",
                    throwable = e
                )
            }
        }
    }

    fun disconnect() {
        try {
            scope.launch {
                mqttClient?.disconnect()
                isConnected = false
                Logger.info(
                    component = "heimdall.device.mqtt",
                    message = "MQTT client disconnected"
                )
            }
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.mqtt",
                message = "Exception while disconnecting",
                throwable = e
            )
        }
    }

    fun setTenantId(tenantId: String) {
        this.tenantId = tenantId
        mqttClient?.let { ackManager.initialize(it, tenantId, getDeviceId()) }
        Logger.info(
            component = "heimdall.device.mqtt",
            message = "Tenant ID updated",
            metadata = mapOf("tenant_id" to tenantId)
        )
    }
    
    /**
     * Registra um processador de comando externo
     */
    fun registerCommandProcessor(commandType: String, processor: suspend (Command) -> CommandResult) {
        commandHandler.registerProcessor(commandType, processor)
    }
    
    /**
     * Retorna o CommandHandler para uso externo
     */
    fun getCommandHandler(): CommandHandler = commandHandler
}
