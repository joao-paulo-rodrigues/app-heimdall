package com.heimdall.device.command

import android.content.Context
import com.heimdall.device.util.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Store-and-Forward Manager
 * Armazena comandos e resultados pendentes quando MQTT está offline
 * Usa arquivo JSON (sem Room por enquanto)
 */
class StoreAndForwardManager private constructor(private val context: Context) {
    
    companion object {
        private const val STORE_DIR = "heimdall_store"
        private const val PENDING_COMMANDS_FILE = "pending_commands.json"
        private const val PENDING_RESULTS_FILE = "pending_results.json"
        private const val MAX_STORED_ITEMS = 1000
        
        @Volatile
        private var INSTANCE: StoreAndForwardManager? = null
        
        fun getInstance(context: Context): StoreAndForwardManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StoreAndForwardManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val storeDir = File(context.filesDir, STORE_DIR)
    private val pendingCommandsFile = File(storeDir, PENDING_COMMANDS_FILE)
    private val pendingResultsFile = File(storeDir, PENDING_RESULTS_FILE)
    private val lock = ReentrantReadWriteLock()
    
    init {
        if (!storeDir.exists()) {
            storeDir.mkdirs()
        }
    }
    
    /**
     * Armazena um comando pendente
     */
    fun storeCommand(command: Command) {
        lock.write {
            try {
                var commands = loadCommands()
                commands.put(command.toJson())
                
                // Limitar tamanho
                if (commands.length() > MAX_STORED_ITEMS) {
                    val newArray = JSONArray()
                    for (i in commands.length() - MAX_STORED_ITEMS until commands.length()) {
                        newArray.put(commands.get(i))
                    }
                    commands = newArray
                }
                
                saveCommands(commands)
                
                Logger.debug(
                    component = "heimdall.device.store_forward",
                    message = "Command stored",
                    metadata = mapOf(
                        "command_id" to command.commandId,
                        "command" to command.command,
                        "total_stored" to commands.length()
                    )
                )
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.store_forward",
                    message = "Failed to store command",
                    metadata = mapOf("command_id" to command.commandId),
                    throwable = e
                )
            }
        }
    }
    
    /**
     * Armazena um resultado pendente
     */
    fun storeResult(result: CommandResult) {
        lock.write {
            try {
                var results = loadResults()
                results.put(result.toJson())
                
                // Limitar tamanho
                if (results.length() > MAX_STORED_ITEMS) {
                    val newArray = JSONArray()
                    for (i in results.length() - MAX_STORED_ITEMS until results.length()) {
                        newArray.put(results.get(i))
                    }
                    results = newArray
                }
                
                saveResults(results)
                
                Logger.debug(
                    component = "heimdall.device.store_forward",
                    message = "Result stored",
                    metadata = mapOf(
                        "command_id" to result.commandId,
                        "status" to result.status.name
                    )
                )
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.store_forward",
                    message = "Failed to store result",
                    metadata = mapOf("command_id" to result.commandId),
                    throwable = e
                )
            }
        }
    }
    
    /**
     * Retorna todos os comandos pendentes e limpa o arquivo
     */
    fun getAndClearPendingCommands(): List<String> {
        return lock.write {
            try {
                val commands = loadCommands()
                val list = mutableListOf<String>()
                
                for (i in 0 until commands.length()) {
                    list.add(commands.getString(i))
                }
                
                // Limpar arquivo
                saveCommands(JSONArray())
                
                Logger.info(
                    component = "heimdall.device.store_forward",
                    message = "Retrieved pending commands",
                    metadata = mapOf("count" to list.size)
                )
                
                list
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.store_forward",
                    message = "Failed to get pending commands",
                    throwable = e
                )
                emptyList()
            }
        }
    }
    
    /**
     * Retorna todos os resultados pendentes e limpa o arquivo
     */
    fun getAndClearPendingResults(): List<String> {
        return lock.write {
            try {
                val results = loadResults()
                val list = mutableListOf<String>()
                
                for (i in 0 until results.length()) {
                    list.add(results.getString(i))
                }
                
                // Limpar arquivo
                saveResults(JSONArray())
                
                Logger.info(
                    component = "heimdall.device.store_forward",
                    message = "Retrieved pending results",
                    metadata = mapOf("count" to list.size)
                )
                
                list
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.store_forward",
                    message = "Failed to get pending results",
                    throwable = e
                )
                emptyList()
            }
        }
    }
    
    private fun loadCommands(): JSONArray {
        return if (pendingCommandsFile.exists()) {
            try {
                JSONArray(pendingCommandsFile.readText())
            } catch (e: Exception) {
                JSONArray()
            }
        } else {
            JSONArray()
        }
    }
    
    private fun saveCommands(commands: JSONArray) {
        pendingCommandsFile.writeText(commands.toString())
    }
    
    private fun loadResults(): JSONArray {
        return if (pendingResultsFile.exists()) {
            try {
                JSONArray(pendingResultsFile.readText())
            } catch (e: Exception) {
                JSONArray()
            }
        } else {
            JSONArray()
        }
    }
    
    private fun saveResults(results: JSONArray) {
        pendingResultsFile.writeText(results.toString())
    }
}

