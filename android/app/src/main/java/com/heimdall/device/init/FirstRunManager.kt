package com.heimdall.device.init

import android.content.Context
import android.content.SharedPreferences
import com.heimdall.device.audit.AuditManager
import com.heimdall.device.cleanup.AppCleanupManager
import com.heimdall.device.util.Logger
import kotlinx.coroutines.*

/**
 * Gerencia a primeira execução do Heimdall
 */
class FirstRunManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: FirstRunManager? = null
        
        private const val PREFS_NAME = "heimdall_first_run"
        private const val KEY_FIRST_RUN = "is_first_run"
        private const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"
        
        fun getInstance(context: Context): FirstRunManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirstRunManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val cleanupManager = AppCleanupManager.getInstance(context)
    private val auditManager = AuditManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Verifica se é a primeira execução
     */
    fun isFirstRun(): Boolean {
        return prefs.getBoolean(KEY_FIRST_RUN, true)
    }
    
    /**
     * Verifica se a primeira execução já foi completada
     */
    fun isFirstRunCompleted(): Boolean {
        return prefs.getBoolean(KEY_FIRST_RUN_COMPLETED, false)
    }
    
    /**
     * Executa o processo de primeira execução
     */
    fun executeFirstRun(onComplete: (Boolean) -> Unit = {}) {
        if (!isFirstRun() || isFirstRunCompleted()) {
            Logger.info(
                component = "heimdall.device.init",
                message = "First run already completed or not first run"
            )
            onComplete(false)
            return
        }
        
        scope.launch {
            try {
                Logger.info(
                    component = "heimdall.device.init",
                    message = "Starting first run process"
                )
                
                auditManager.publishAuditEvent(
                    eventType = "first_run_started",
                    message = "Heimdall first run process started"
                )
                
                // Marcar que primeira execução está em andamento
                prefs.edit().putBoolean(KEY_FIRST_RUN, false).apply()
                
                // Executar limpeza de apps
                val cleanupResult = cleanupManager.cleanupUnnecessaryApps()
                
                if (cleanupResult.success) {
                    Logger.info(
                        component = "heimdall.device.init",
                        message = "First run cleanup completed",
                        metadata = mapOf(
                            "removed_count" to cleanupResult.removedCount,
                            "failed_count" to cleanupResult.failedCount
                        )
                    )
                    
                    // Marcar primeira execução como completada
                    prefs.edit().putBoolean(KEY_FIRST_RUN_COMPLETED, true).apply()
                    
                    auditManager.publishAuditEvent(
                        eventType = "first_run_completed",
                        message = "Heimdall first run process completed successfully",
                        metadata = mapOf(
                            "removed_apps_count" to cleanupResult.removedCount,
                            "failed_removals_count" to cleanupResult.failedCount,
                            "removed_packages" to cleanupResult.removedPackages,
                            "failed_packages" to cleanupResult.failedPackages
                        )
                    )
                    
                    withContext(Dispatchers.Main) {
                        onComplete(true)
                    }
                } else {
                    Logger.error(
                        component = "heimdall.device.init",
                        message = "First run cleanup failed",
                        metadata = mapOf("error" to cleanupResult.message)
                    )
                    
                    auditManager.publishAuditEvent(
                        eventType = "first_run_failed",
                        message = "Heimdall first run process failed",
                        metadata = mapOf("error" to cleanupResult.message)
                    )
                    
                    withContext(Dispatchers.Main) {
                        onComplete(false)
                    }
                }
                
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.init",
                    message = "First run process error",
                    throwable = e
                )
                
                auditManager.publishAuditEvent(
                    eventType = "first_run_error",
                    message = "Heimdall first run process error: ${e.message}",
                    metadata = mapOf("error" to (e.message ?: "Unknown error"))
                )
                
                withContext(Dispatchers.Main) {
                    onComplete(false)
                }
            }
        }
    }
    
    /**
     * Reseta o estado de primeira execução (útil para testes)
     */
    fun resetFirstRun() {
        prefs.edit()
            .putBoolean(KEY_FIRST_RUN, true)
            .putBoolean(KEY_FIRST_RUN_COMPLETED, false)
            .apply()
    }
}

