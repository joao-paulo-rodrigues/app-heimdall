package com.heimdall.device.command

import android.content.Context
import com.heimdall.device.util.Logger
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gerencia reprocessamento de comandos que falharam
 */
class CommandRetryManager(private val context: Context) {
    
    companion object {
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 5000L
        private const val MAX_RETRY_DELAY_MS = 60000L
    }
    
    private val retryCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Verifica se um comando pode ser reprocessado
     */
    fun canRetry(commandId: String): Boolean {
        val count = retryCounts.getOrPut(commandId) { AtomicInteger(0) }
        return count.get() < MAX_RETRIES
    }
    
    /**
     * Incrementa contador de retry e retorna o delay para próximo retry
     */
    fun incrementRetry(commandId: String): Long {
        val count = retryCounts.getOrPut(commandId) { AtomicInteger(0) }
        val currentRetry = count.incrementAndGet()
        
        // Backoff exponencial
        val delay = minOf(
            RETRY_DELAY_MS * (1 shl (currentRetry - 1)),
            MAX_RETRY_DELAY_MS
        )
        
        Logger.warning(
            component = "heimdall.device.command_retry",
            message = "Command retry scheduled",
            metadata = mapOf(
                "command_id" to commandId,
                "retry_count" to currentRetry,
                "max_retries" to MAX_RETRIES,
                "delay_ms" to delay
            )
        )
        
        return delay
    }
    
    /**
     * Remove contador de retry (comando processado com sucesso)
     */
    fun clearRetry(commandId: String) {
        retryCounts.remove(commandId)
    }
    
    /**
     * Agenda reprocessamento de um comando
     */
    fun scheduleRetry(
        command: Command,
        delayMs: Long = RETRY_DELAY_MS,
        onRetry: suspend (Command) -> Unit
    ) {
        if (!canRetry(command.commandId)) {
            Logger.error(
                component = "heimdall.device.command_retry",
                message = "Max retries reached for command",
                metadata = mapOf(
                    "command_id" to command.commandId,
                    "command" to command.command
                ),
                traceId = command.traceId
            )
            return
        }
        
        val actualDelay = incrementRetry(command.commandId)
        
        scope.launch {
            delay(actualDelay)
            
            if (canRetry(command.commandId)) {
                Logger.info(
                    component = "heimdall.device.command_retry",
                    message = "Retrying command",
                    metadata = mapOf(
                        "command_id" to command.commandId,
                        "command" to command.command
                    ),
                    traceId = command.traceId
                )
                
                try {
                    onRetry(command)
                } catch (e: Exception) {
                    Logger.error(
                        component = "heimdall.device.command_retry",
                        message = "Retry failed",
                        metadata = mapOf("command_id" to command.commandId),
                        throwable = e,
                        traceId = command.traceId
                    )
                }
            }
        }
    }
    
    /**
     * Limpa todos os contadores de retry
     */
    fun clearAll() {
        retryCounts.clear()
    }
}


