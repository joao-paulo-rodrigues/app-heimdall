package com.heimdall.device.command

import android.content.Context
import com.heimdall.device.config.MqttConfig
import com.heimdall.device.util.Logger
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import kotlinx.coroutines.*

/**
 * Gerencia o envio de ACKs de comandos
 * Garante que ACKs sejam enviados mesmo se MQTT estiver temporariamente offline
 */
class CommandAckManager(
    private val context: Context,
    private val storeAndForward: StoreAndForwardManager
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mqttClient: Mqtt5AsyncClient? = null
    private var tenantId: String = "UEBRASIL"
    private var deviceId: String = "UNKNOWN"
    
    fun initialize(mqttClient: Mqtt5AsyncClient, tenantId: String, deviceId: String) {
        this.mqttClient = mqttClient
        this.tenantId = tenantId
        this.deviceId = deviceId
    }
    
    /**
     * Envia ACK imediato de recebimento
     */
    fun sendReceivedAck(command: Command) {
        val ack = CommandAck(
            commandId = command.commandId,
            command = command.command,
            ackType = CommandAck.AckType.RECEIVED,
            traceId = command.traceId
        )
        sendAck(ack)
    }
    
    /**
     * Envia ACK de processamento
     */
    fun sendProcessingAck(command: Command) {
        val ack = CommandAck(
            commandId = command.commandId,
            command = command.command,
            ackType = CommandAck.AckType.PROCESSING,
            message = "Command is being processed",
            traceId = command.traceId
        )
        sendAck(ack)
    }
    
    /**
     * Envia ACK de rejeição
     */
    fun sendRejectedAck(command: Command, reason: String) {
        val ack = CommandAck(
            commandId = command.commandId,
            command = command.command,
            ackType = CommandAck.AckType.REJECTED,
            message = reason,
            traceId = command.traceId
        )
        sendAck(ack)
    }
    
    private fun sendAck(ack: CommandAck) {
        scope.launch {
            try {
                val topic = MqttConfig.getAckTopic(tenantId, deviceId)
                val payload = ack.toJson()
                
                mqttClient?.publishWith()
                    ?.topic(topic)
                    ?.payload(payload.toByteArray())
                    ?.qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    ?.send()
                    ?.get()
                
                Logger.info(
                    component = "heimdall.device.command_ack",
                    message = "ACK sent",
                    metadata = mapOf(
                        "command_id" to ack.commandId,
                        "ack_type" to ack.ackType.name,
                        "topic" to topic
                    ),
                    traceId = ack.traceId
                )
            } catch (e: Exception) {
                // Se falhar, armazenar para envio posterior
                Logger.warning(
                    component = "heimdall.device.command_ack",
                    message = "Failed to send ACK, storing for retry",
                    metadata = mapOf(
                        "command_id" to ack.commandId,
                        "error" to (e.message ?: "Unknown error")
                    ),
                    traceId = ack.traceId
                )
                
                // Armazenar ACK como resultado pendente
                val result = CommandResult(
                    commandId = ack.commandId,
                    command = ack.command,
                    status = CommandResult.CommandStatus.IN_PROGRESS,
                    message = "ACK pending: ${ack.ackType.name}",
                    traceId = ack.traceId
                )
                storeAndForward.storeResult(result)
            }
        }
    }
    
    /**
     * Envia resultado final do comando
     */
    fun sendResult(result: CommandResult) {
        scope.launch {
            try {
                val topic = MqttConfig.getAckTopic(tenantId, deviceId)
                val payload = result.toJson()
                
                mqttClient?.publishWith()
                    ?.topic(topic)
                    ?.payload(payload.toByteArray())
                    ?.qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                    ?.send()
                    ?.get()
                
                Logger.info(
                    component = "heimdall.device.command_ack",
                    message = "Command result sent",
                    metadata = mapOf(
                        "command_id" to result.commandId,
                        "status" to result.status.name
                    ),
                    traceId = result.traceId
                )
            } catch (e: Exception) {
                // Armazenar para envio posterior
                Logger.warning(
                    component = "heimdall.device.command_ack",
                    message = "Failed to send result, storing for retry",
                    metadata = mapOf(
                        "command_id" to result.commandId,
                        "error" to (e.message ?: "Unknown error")
                    ),
                    traceId = result.traceId
                )
                
                storeAndForward.storeResult(result)
            }
        }
    }
    
    /**
     * Tenta reenviar resultados pendentes
     */
    fun retryPendingResults() {
        scope.launch {
            val pendingResults = storeAndForward.getAndClearPendingResults()
            
            if (pendingResults.isEmpty()) {
                return@launch
            }
            
            Logger.info(
                component = "heimdall.device.command_ack",
                message = "Retrying pending results",
                metadata = mapOf("count" to pendingResults.size)
            )
            
            pendingResults.forEach { resultJson ->
                try {
                    val obj = org.json.JSONObject(resultJson)
                    val result = CommandResult(
                        commandId = obj.getString("command_id"),
                        command = obj.getString("command"),
                        status = CommandResult.CommandStatus.valueOf(obj.getString("status").uppercase()),
                        message = obj.getString("message"),
                        error = obj.optString("error", null),
                        timestamp = obj.getLong("timestamp"),
                        traceId = obj.optString("trace_id", null)
                    )
                    sendResult(result)
                } catch (e: Exception) {
                    Logger.error(
                        component = "heimdall.device.command_ack",
                        message = "Failed to parse pending result",
                        throwable = e
                    )
                }
            }
        }
    }
}

