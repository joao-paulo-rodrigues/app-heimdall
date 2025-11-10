package com.heimdall.device.cleanup

import android.content.Context
import android.content.pm.PackageManager
import com.heimdall.device.audit.AuditManager
import com.heimdall.device.update.InstallManager
import com.heimdall.device.util.Logger
import kotlinx.coroutines.*

/**
 * Gerencia limpeza de apps não necessários do sistema
 */
class AppCleanupManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: AppCleanupManager? = null
        
        fun getInstance(context: Context): AppCleanupManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppCleanupManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Apps essenciais que NÃO devem ser removidos
        private val ESSENTIAL_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.providers.settings",
            "com.android.providers.contacts",
            "com.android.providers.telephony",
            "com.android.providers.downloads",
            "com.android.providers.media",
            "com.android.providers.calendar",
            "com.android.keychain",
            "com.android.proxyhandler",
            "com.android.sharedstoragebackup",
            "com.android.externalstorage",
            "com.android.htmlviewer",
            "com.android.documentsui",
            "com.android.packageinstaller",
            "com.android.certinstaller",
            "com.android.inputmethod.latin",
            "com.android.inputmethod.voice",
            "com.android.managedprovisioning",
            "com.android.vending", // Google Play Store (pode ser necessário)
            "com.google.android.gms", // Google Play Services
            "com.google.android.gsf", // Google Services Framework
            "com.heimdall.device", // O próprio Heimdall
            "com.uebrasil.panicbuttonapp" // PanicButton (se existir)
        )
        
        // Prefixos de apps do sistema que devem ser mantidos
        private val SYSTEM_PACKAGE_PREFIXES = setOf(
            "com.android.",
            "android.",
            "com.heimdall.",
            "com.uebrasil.panicbuttonapp"
        )
    }
    
    private val installManager = InstallManager(context)
    private val auditManager = AuditManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Executa limpeza de apps não necessários
     */
    suspend fun cleanupUnnecessaryApps(): CleanupResult = withContext(Dispatchers.IO) {
        if (!installManager.isDeviceOwner()) {
            Logger.error(
                component = "heimdall.device.cleanup",
                message = "Device Owner privileges required for app cleanup"
            )
            auditManager.publishAuditEvent(
                eventType = "cleanup_failed",
                message = "Device Owner privileges required",
                metadata = mapOf("reason" to "no_device_owner")
            )
            return@withContext CleanupResult(
                success = false,
                removedCount = 0,
                failedCount = 0,
                removedPackages = emptyList(),
                failedPackages = emptyList(),
                message = "Device Owner privileges required"
            )
        }
        
        try {
            Logger.info(
                component = "heimdall.device.cleanup",
                message = "Starting app cleanup process"
            )
            
            auditManager.publishAuditEvent(
                eventType = "cleanup_started",
                message = "App cleanup process started"
            )
            
            val installedPackages = getInstalledPackages()
            val packagesToRemove = identifyPackagesToRemove(installedPackages)
            
            Logger.info(
                component = "heimdall.device.cleanup",
                message = "Identified packages for removal",
                metadata = mapOf(
                    "total_installed" to installedPackages.size,
                    "to_remove" to packagesToRemove.size
                )
            )
            
            val removedPackages = mutableListOf<String>()
            val failedPackages = mutableListOf<String>()
            
            packagesToRemove.forEach { packageName ->
                try {
                    Logger.info(
                        component = "heimdall.device.cleanup",
                        message = "Removing package",
                        metadata = mapOf("package_name" to packageName)
                    )
                    
                    val success = installManager.uninstallApp(packageName)
                    
                    if (success) {
                        removedPackages.add(packageName)
                        auditManager.publishAuditEvent(
                            eventType = "app_removed",
                            message = "App removed successfully",
                            metadata = mapOf("package_name" to packageName)
                        )
                    } else {
                        failedPackages.add(packageName)
                        Logger.warning(
                            component = "heimdall.device.cleanup",
                            message = "Failed to remove package",
                            metadata = mapOf("package_name" to packageName)
                        )
                        auditManager.publishAuditEvent(
                            eventType = "app_removal_failed",
                            message = "Failed to remove app",
                            metadata = mapOf("package_name" to packageName)
                        )
                    }
                    
                    // Pequeno delay entre remoções para não sobrecarregar o sistema
                    delay(500)
                    
                } catch (e: Exception) {
                    failedPackages.add(packageName)
                    Logger.error(
                        component = "heimdall.device.cleanup",
                        message = "Error removing package",
                        metadata = mapOf("package_name" to packageName),
                        throwable = e
                    )
                    auditManager.publishAuditEvent(
                        eventType = "app_removal_error",
                        message = "Error removing app: ${e.message}",
                        metadata = mapOf(
                            "package_name" to packageName,
                            "error" to (e.message ?: "Unknown error")
                        )
                    )
                }
            }
            
            val result = CleanupResult(
                success = true,
                removedCount = removedPackages.size,
                failedCount = failedPackages.size,
                removedPackages = removedPackages,
                failedPackages = failedPackages,
                message = "Cleanup completed: ${removedPackages.size} removed, ${failedPackages.size} failed"
            )
            
            Logger.info(
                component = "heimdall.device.cleanup",
                message = "App cleanup completed",
                metadata = mapOf(
                    "removed_count" to removedPackages.size,
                    "failed_count" to failedPackages.size
                )
            )
            
            auditManager.publishAuditEvent(
                eventType = "cleanup_completed",
                message = "App cleanup process completed",
                metadata = mapOf(
                    "removed_count" to removedPackages.size,
                    "failed_count" to failedPackages.size,
                    "removed_packages" to removedPackages,
                    "failed_packages" to failedPackages
                )
            )
            
            result
            
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.cleanup",
                message = "App cleanup failed",
                throwable = e
            )
            
            auditManager.publishAuditEvent(
                eventType = "cleanup_failed",
                message = "App cleanup process failed: ${e.message}",
                metadata = mapOf("error" to (e.message ?: "Unknown error"))
            )
            
            CleanupResult(
                success = false,
                removedCount = 0,
                failedCount = 0,
                removedPackages = emptyList(),
                failedPackages = emptyList(),
                message = "Cleanup failed: ${e.message}"
            )
        }
    }
    
    /**
     * Obtém lista de pacotes instalados
     */
    private fun getInstalledPackages(): List<String> {
        return try {
            val packageManager = context.packageManager
            val packages = packageManager.getInstalledPackages(0)
            packages.map { it.packageName }
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.cleanup",
                message = "Error getting installed packages",
                throwable = e
            )
            emptyList()
        }
    }
    
    /**
     * Identifica pacotes que devem ser removidos
     */
    private fun identifyPackagesToRemove(installedPackages: List<String>): List<String> {
        return installedPackages.filter { packageName ->
            // Não remover apps essenciais
            if (ESSENTIAL_PACKAGES.contains(packageName)) {
                return@filter false
            }
            
            // Não remover apps do sistema (com prefixos específicos)
            if (SYSTEM_PACKAGE_PREFIXES.any { packageName.startsWith(it) }) {
                return@filter false
            }
            
            // Remover apps de terceiros (não essenciais)
            true
        }
    }
    
    data class CleanupResult(
        val success: Boolean,
        val removedCount: Int,
        val failedCount: Int,
        val removedPackages: List<String>,
        val failedPackages: List<String>,
        val message: String
    )
}

