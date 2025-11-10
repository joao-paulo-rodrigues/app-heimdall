package com.heimdall.device.update

import android.content.Context
import com.heimdall.device.util.Logger
import kotlinx.coroutines.*
import java.io.File

/**
 * Gerenciador principal de atualizações e instalações
 * Baixa e instala APKs de uma URL simples
 */
class UpdateManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: UpdateManager? = null
        
        fun getInstance(context: Context): UpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UpdateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val downloadManager = DownloadManager(context)
    private val installManager = InstallManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Baixa e instala um APK de uma URL
     */
    suspend fun installFromUrl(
        url: String,
        packageName: String? = null,
        force: Boolean = false
    ): UpdateResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            Logger.info(
                component = "heimdall.device.update.manager",
                message = "Starting install from URL",
                metadata = mapOf(
                    "url" to url,
                    "package_name" to (packageName ?: "unknown"),
                    "force" to force
                )
            )
            
            // Verificar Device Owner
            if (!installManager.isDeviceOwner()) {
                return@withContext UpdateResult.error(
                    packageName ?: "unknown",
                    "Device Owner privileges required",
                    "device_owner_required"
                )
            }
            
            // Baixar APK
            val downloadStartTime = System.currentTimeMillis()
            val apkFile = downloadManager.downloadApk(
                url = url,
                packageName = packageName ?: "app",
                expectedChecksum = null,
                onProgress = { downloaded, total ->
                    val progress = if (total > 0) (downloaded * 100 / total).toInt() else 0
                    Logger.debug(
                        component = "heimdall.device.update.manager",
                        message = "Download progress",
                        metadata = mapOf(
                            "url" to url,
                            "progress_percent" to progress
                        )
                    )
                }
            )
            
            if (apkFile == null) {
                return@withContext UpdateResult.error(
                    packageName ?: "unknown",
                    "Failed to download APK",
                    "download_failed"
                )
            }
            
            val downloadTime = System.currentTimeMillis() - downloadStartTime
            val downloadSize = apkFile.length()
            
            Logger.info(
                component = "heimdall.device.update.manager",
                message = "APK downloaded, starting installation",
                metadata = mapOf(
                    "url" to url,
                    "file_size" to downloadSize,
                    "download_time_ms" to downloadTime
                )
            )
            
            // Instalar APK
            // Se packageName não foi fornecido, tentaremos obter do APK após instalação
            val installStartTime = System.currentTimeMillis()
            val installedPackageName = packageName ?: "unknown"
            val installed = installManager.installApk(apkFile, installedPackageName)
            val installTime = System.currentTimeMillis() - installStartTime
            
            // Limpar arquivo temporário
            try {
                apkFile.delete()
            } catch (e: Exception) {
                // Ignore
            }
            
            if (!installed) {
                return@withContext UpdateResult.error(
                    installedPackageName,
                    "Failed to install APK",
                    "install_failed"
                )
            }
            
            // Obter informações da versão instalada (se packageName foi fornecido)
            val installedInfo = if (packageName != null) {
                installManager.getInstalledPackageInfo(packageName)
            } else {
                null
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            
            Logger.info(
                component = "heimdall.device.update.manager",
                message = "App installed successfully",
                metadata = mapOf(
                    "url" to url,
                    "package_name" to (installedInfo?.packageName ?: installedPackageName),
                    "version_name" to (installedInfo?.versionName ?: "unknown"),
                    "version_code" to (installedInfo?.versionCode ?: -1),
                    "total_time_ms" to totalTime,
                    "download_time_ms" to downloadTime,
                    "install_time_ms" to installTime
                )
            )
            
            UpdateResult.success(
                packageName = installedInfo?.packageName ?: installedPackageName,
                message = "App installed successfully",
                versionName = installedInfo?.versionName,
                versionCode = installedInfo?.versionCode,
                downloadSize = downloadSize,
                downloadTime = downloadTime,
                installTime = installTime
            )
            
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.update.manager",
                message = "Install failed",
                metadata = mapOf("url" to url),
                throwable = e
            )
            
            UpdateResult.error(
                packageName ?: "unknown",
                "Install failed: ${e.message}",
                "install_exception",
                e.message
            )
        }
    }
    
    /**
     * Desinstala um app
     */
    suspend fun uninstallApp(packageName: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            Logger.info(
                component = "heimdall.device.update.manager",
                message = "Uninstalling app",
                metadata = mapOf("package_name" to packageName)
            )
            
            val success = installManager.uninstallApp(packageName)
            
            if (success) {
                UpdateResult.success(packageName, "App uninstalled successfully")
            } else {
                UpdateResult.error(packageName, "Failed to uninstall app", "uninstall_failed")
            }
            
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.update.manager",
                message = "Uninstall failed",
                metadata = mapOf("package_name" to packageName),
                throwable = e
            )
            
            UpdateResult.error(
                packageName,
                "Uninstall failed: ${e.message}",
                "uninstall_exception",
                e.message
            )
        }
    }
    
    /**
     * Obtém informações de versão instalada
     */
    fun getInstalledVersion(packageName: String): InstallManager.PackageInfo? {
        return installManager.getInstalledPackageInfo(packageName)
    }
    
    /**
     * Limpa downloads antigos
     */
    fun cleanup() {
        downloadManager.cleanupOldDownloads()
    }
}

