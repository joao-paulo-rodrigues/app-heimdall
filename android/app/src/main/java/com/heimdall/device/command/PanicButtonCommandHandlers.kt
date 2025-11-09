package com.heimdall.device.command

import android.content.Context
import com.heimdall.device.panicbutton.PanicButtonCommunicationManager
import com.heimdall.device.util.Logger
import kotlinx.coroutines.delay

/**
 * Handlers de comandos relacionados ao PanicButton
 */

/**
 * Handler para comando de configurar modo kiosk do PanicButton
 */
fun configurePanicButtonKioskHandler(context: Context): suspend (Command) -> CommandResult {
    return { command ->
        try {
            val enabled = command.params["enabled"] as? Boolean 
                ?: (command.params["enabled"] as? String)?.toBoolean() 
                ?: true
            
            val commManager = PanicButtonCommunicationManager.getInstance(context)
            commManager.configureKioskMode(enabled)
            
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.SUCCESS,
                message = "PanicButton kiosk mode configured",
                data = mapOf("enabled" to enabled),
                traceId = command.traceId
            )
        } catch (e: Exception) {
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.ERROR,
                message = "Failed to configure PanicButton kiosk mode",
                error = e.message ?: "Unknown error",
                traceId = command.traceId
            )
        }
    }
}

/**
 * Handler para comando de sincronizar dados com PanicButton
 */
fun syncPanicButtonDataHandler(context: Context): suspend (Command) -> CommandResult {
    return { command ->
        try {
            val commManager = PanicButtonCommunicationManager.getInstance(context)
            commManager.syncSharedData()
            
            delay(500) // Aguardar sincronização
            
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.SUCCESS,
                message = "Data synchronized with PanicButton",
                traceId = command.traceId
            )
        } catch (e: Exception) {
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.ERROR,
                message = "Failed to sync data with PanicButton",
                error = e.message ?: "Unknown error",
                traceId = command.traceId
            )
        }
    }
}

/**
 * Handler para comando de verificar status do PanicButton
 */
fun getPanicButtonStatusHandler(context: Context): suspend (Command) -> CommandResult {
    return { command ->
        try {
            val commManager = PanicButtonCommunicationManager.getInstance(context)
            val isInstalled = commManager.isPanicButtonInstalled()
            val kioskMode = commManager.getSharedData(PanicButtonCommunicationManager.KEY_KIOSK_MODE, "false").toBoolean()
            val lastUpdate = commManager.getSharedData(PanicButtonCommunicationManager.KEY_LAST_UPDATE, "0")
            
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.SUCCESS,
                message = "PanicButton status retrieved",
                data = mapOf(
                    "installed" to isInstalled,
                    "kiosk_mode" to kioskMode,
                    "last_update" to lastUpdate
                ),
                traceId = command.traceId
            )
        } catch (e: Exception) {
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.ERROR,
                message = "Failed to get PanicButton status",
                error = e.message ?: "Unknown error",
                traceId = command.traceId
            )
        }
    }
}

/**
 * Handler para comando de enviar comando ao PanicButton
 */
fun sendPanicButtonCommandHandler(context: Context): suspend (Command) -> CommandResult {
    return { command ->
        try {
            val action = command.params["action"] as? String
            if (action == null) {
                CommandResult(
                    commandId = command.commandId,
                    command = command.command,
                    status = CommandResult.CommandStatus.REJECTED,
                    message = "Missing required parameter: action",
                    traceId = command.traceId
                )
            } else {
                val data = command.params.filterKeys { it != "action" }
                
                val commManager = PanicButtonCommunicationManager.getInstance(context)
                val success = commManager.sendCommand(action, data)
                
                CommandResult(
                    commandId = command.commandId,
                    command = command.command,
                    status = if (success) CommandResult.CommandStatus.SUCCESS else CommandResult.CommandStatus.ERROR,
                    message = if (success) "Command sent to PanicButton" else "Failed to send command to PanicButton",
                    data = mapOf("action" to action, "success" to success),
                    traceId = command.traceId
                )
            }
        } catch (e: Exception) {
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.ERROR,
                message = "Error sending command to PanicButton",
                error = e.message ?: "Unknown error",
                traceId = command.traceId
            )
        }
    }
}

