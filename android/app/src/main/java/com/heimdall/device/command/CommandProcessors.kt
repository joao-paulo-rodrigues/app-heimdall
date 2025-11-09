package com.heimdall.device.command

import android.content.Context
import com.heimdall.device.util.Logger
import kotlinx.coroutines.delay

/**
 * Processadores de comandos específicos
 * Exemplos de implementação de processadores para diferentes tipos de comandos
 */

/**
 * Processador de comando de ping (exemplo simples)
 */
suspend fun pingProcessor(command: Command): CommandResult {
    return CommandResult(
        commandId = command.commandId,
        command = command.command,
        status = CommandResult.CommandStatus.SUCCESS,
        message = "Pong",
        data = mapOf(
            "timestamp" to System.currentTimeMillis(),
            "received_at" to command.timestamp
        ),
        traceId = command.traceId
    )
}

/**
 * Processador de comando para obter status do dispositivo
 */
suspend fun getDeviceStatusProcessor(context: Context, deviceId: String, tenantId: String, mqttConnected: Boolean): suspend (Command) -> CommandResult {
    return { command ->
        CommandResult(
            commandId = command.commandId,
            command = command.command,
            status = CommandResult.CommandStatus.SUCCESS,
            message = "Device status retrieved",
            data = mapOf(
                "device_id" to deviceId,
                "tenant_id" to tenantId,
                "mqtt_connected" to mqttConnected,
                "timestamp" to System.currentTimeMillis(),
                "android_version" to android.os.Build.VERSION.SDK_INT,
                "device_model" to android.os.Build.MODEL
            ),
            traceId = command.traceId
        )
    }
}

/**
 * Processador de comando com delay (exemplo de comando assíncrono)
 */
suspend fun delayedCommandProcessor(command: Command): CommandResult {
    val delayMs = (command.params["delay_ms"] as? Number)?.toLong() ?: 1000L
    
    Logger.info(
        component = "heimdall.device.command_processor",
        message = "Processing delayed command",
        metadata = mapOf(
            "command_id" to command.commandId,
            "delay_ms" to delayMs
        ),
        traceId = command.traceId
    )
    
    delay(delayMs)
    
    return CommandResult(
        commandId = command.commandId,
        command = command.command,
        status = CommandResult.CommandStatus.SUCCESS,
        message = "Delayed command completed",
        data = mapOf(
            "delay_ms" to delayMs,
            "completed_at" to System.currentTimeMillis()
        ),
        traceId = command.traceId
    )
}

/**
 * Processador de comando que valida parâmetros
 */
suspend fun validatedCommandProcessor(command: Command): CommandResult {
    val requiredParams = listOf("param1", "param2")
    val missingParams = requiredParams.filter { !command.params.containsKey(it) }
    
    if (missingParams.isNotEmpty()) {
        return CommandResult(
            commandId = command.commandId,
            command = command.command,
            status = CommandResult.CommandStatus.REJECTED,
            message = "Missing required parameters",
            error = "Missing: ${missingParams.joinToString(", ")}",
            traceId = command.traceId
        )
    }
    
    return CommandResult(
        commandId = command.commandId,
        command = command.command,
        status = CommandResult.CommandStatus.SUCCESS,
        message = "Command validated and processed",
        data = command.params,
        traceId = command.traceId
    )
}


