package com.heimdall.device.command

import android.content.Context
import com.heimdall.device.util.Logger
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * Handler principal para processamento de comandos
 * Coordena ACK, retry e store-and-forward
 */
class CommandHandler(
    private val context: Context,
    private val ackManager: CommandAckManager,
    private val retryManager: CommandRetryManager,
    private val storeAndForward: StoreAndForwardManager
) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commandProcessors = mutableMapOf<String, suspend (Command) -> CommandResult>()
    
    /**
     * Registra um processador para um tipo de comando
     */
    fun registerProcessor(commandType: String, processor: suspend (Command) -> CommandResult) {
        commandProcessors[commandType] = processor
        
        Logger.info(
            component = "heimdall.device.command_handler",
            message = "Command processor registered",
            metadata = mapOf("command_type" to commandType)
        )
    }
    
    /**
     * Processa um comando recebido
     */
    fun processCommand(commandJson: String) {
        scope.launch {
            val command = Command.fromJson(commandJson)
            
            if (command == null) {
                Logger.error(
                    component = "heimdall.device.command_handler",
                    message = "Invalid command format",
                    metadata = mapOf("payload" to commandJson)
                )
                return@launch
            }
            
            Logger.info(
                component = "heimdall.device.command_handler",
                message = "Command received",
                metadata = mapOf(
                    "command_id" to command.commandId,
                    "command" to command.command
                ),
                traceId = command.traceId
            )
            
            // Enviar ACK imediato de recebimento
            ackManager.sendReceivedAck(command)
            
            // Verificar se comando já foi processado (idempotência)
            if (isCommandProcessed(command.commandId)) {
                Logger.warning(
                    component = "heimdall.device.command_handler",
                    message = "Command already processed, ignoring",
                    metadata = mapOf("command_id" to command.commandId),
                    traceId = command.traceId
                )
                return@launch
            }
            
            // Enviar ACK de processamento
            ackManager.sendProcessingAck(command)
            
            // Processar comando
            try {
                val processor = commandProcessors[command.command]
                
                if (processor == null) {
                    val result = CommandResult(
                        commandId = command.commandId,
                        command = command.command,
                        status = CommandResult.CommandStatus.REJECTED,
                        message = "Unknown command type",
                        traceId = command.traceId
                    )
                    
                    ackManager.sendResult(result)
                    markCommandProcessed(command.commandId)
                    return@launch
                }
                
                // Executar processador
                val result = processor(command)
                
                // Marcar como processado
                markCommandProcessed(command.commandId)
                
                // Limpar retry
                retryManager.clearRetry(command.commandId)
                
                // Enviar resultado
                ackManager.sendResult(result)
                
                Logger.info(
                    component = "heimdall.device.command_handler",
                    message = "Command processed successfully",
                    metadata = mapOf(
                        "command_id" to command.commandId,
                        "status" to result.status.name
                    ),
                    traceId = command.traceId
                )
                
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.command_handler",
                    message = "Command processing failed",
                    metadata = mapOf("command_id" to command.commandId),
                    throwable = e,
                    traceId = command.traceId
                )
                
                // Tentar reprocessar se possível
                if (retryManager.canRetry(command.commandId)) {
                    retryManager.scheduleRetry(command) { cmd ->
                        processCommand(cmd.toJson())
                    }
                } else {
                    // Enviar resultado de erro
                    val result = CommandResult(
                        commandId = command.commandId,
                        command = command.command,
                        status = CommandResult.CommandStatus.ERROR,
                        message = "Command processing failed after retries",
                        error = e.message ?: "Unknown error",
                        traceId = command.traceId
                    )
                    
                    ackManager.sendResult(result)
                    markCommandProcessed(command.commandId)
                }
            }
        }
    }
    
    /**
     * Verifica se comando já foi processado (usando SharedPreferences)
     */
    private fun isCommandProcessed(commandId: String): Boolean {
        val prefs = context.getSharedPreferences("heimdall_commands", Context.MODE_PRIVATE)
        return prefs.getBoolean("processed_$commandId", false)
    }
    
    /**
     * Marca comando como processado
     */
    private fun markCommandProcessed(commandId: String) {
        val prefs = context.getSharedPreferences("heimdall_commands", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("processed_$commandId", true).apply()
    }
    
    /**
     * Limpa histórico de comandos processados (manutenção)
     */
    fun clearProcessedHistory() {
        val prefs = context.getSharedPreferences("heimdall_commands", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        Logger.info(
            component = "heimdall.device.command_handler",
            message = "Command history cleared"
        )
    }
}


