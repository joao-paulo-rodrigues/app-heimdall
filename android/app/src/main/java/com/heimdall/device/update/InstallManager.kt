package com.heimdall.device.update

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.heimdall.device.receiver.HeimdallDeviceAdminReceiver
import com.heimdall.device.util.Logger
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream

/**
 * Gerencia instalação de APKs usando Device Owner
 */
class InstallManager(private val context: Context) {
    
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, HeimdallDeviceAdminReceiver::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Verifica se o app tem privilégios de Device Owner
     */
    fun isDeviceOwner(): Boolean {
        return try {
            devicePolicyManager.isDeviceOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Instala APK usando Device Owner (instalação silenciosa)
     */
    suspend fun installApk(apkFile: File, packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isDeviceOwner()) {
            Logger.error(
                component = "heimdall.device.update.install",
                message = "Device Owner privileges required for silent installation",
                metadata = mapOf("package_name" to packageName)
            )
            return@withContext false
        }
        
        if (!apkFile.exists()) {
            Logger.error(
                component = "heimdall.device.update.install",
                message = "APK file not found",
                metadata = mapOf(
                    "package_name" to packageName,
                    "file_path" to apkFile.absolutePath
                )
            )
            return@withContext false
        }
        
        try {
            Logger.info(
                component = "heimdall.device.update.install",
                message = "Starting silent installation",
                metadata = mapOf(
                    "package_name" to packageName,
                    "file_size" to apkFile.length()
                )
            )
            
            val packageInstaller = context.packageManager.packageInstaller
            val sessionParams = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            sessionParams.setAppPackageName(packageName)
            
            // Garantir instalação silenciosa (sem interação do usuário)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Android 8.0+: Definir razão da instalação como device setup
                sessionParams.setInstallReason(PackageManager.INSTALL_REASON_DEVICE_SETUP)
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // Android 12+: Não requer ação do usuário
                sessionParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            
            val sessionId = packageInstaller.createSession(sessionParams)
            val session = packageInstaller.openSession(sessionId)
            
            // Copiar APK para a sessão
            FileInputStream(apkFile).use { input ->
                session.openWrite("app.apk", 0, -1).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Criar Intent para receber resultado
            val intent = android.content.Intent(context, InstallResultReceiver::class.java).apply {
                putExtra("package_name", packageName)
                putExtra("session_id", sessionId)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            // Commit da instalação
            session.commit(pendingIntent.intentSender)
            session.close()
            
            Logger.info(
                component = "heimdall.device.update.install",
                message = "Installation session created",
                metadata = mapOf(
                    "package_name" to packageName,
                    "session_id" to sessionId
                )
            )
            
            // Aguardar resultado (timeout de 60 segundos)
            delay(UpdateConfig.INSTALL_TIMEOUT_MS)
            
            // Verificar se foi instalado
            val installed = isPackageInstalled(packageName)
            
            if (installed) {
                Logger.info(
                    component = "heimdall.device.update.install",
                    message = "Package installed successfully",
                    metadata = mapOf("package_name" to packageName)
                )
            } else {
                Logger.warning(
                    component = "heimdall.device.update.install",
                    message = "Installation may have failed or is still in progress",
                    metadata = mapOf("package_name" to packageName)
                )
            }
            
            installed
            
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.update.install",
                message = "Installation failed",
                metadata = mapOf("package_name" to packageName),
                throwable = e
            )
            false
        }
    }
    
    /**
     * Desinstala app usando Device Owner
     */
    suspend fun uninstallApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isDeviceOwner()) {
            Logger.error(
                component = "heimdall.device.update.install",
                message = "Device Owner privileges required for uninstallation",
                metadata = mapOf("package_name" to packageName)
            )
            return@withContext false
        }
        
        try {
            Logger.info(
                component = "heimdall.device.update.install",
                message = "Uninstalling package",
                metadata = mapOf("package_name" to packageName)
            )
            
            val packageInstaller = context.packageManager.packageInstaller
            
            // Criar Intent para receber resultado
            val intent = android.content.Intent(context, UninstallResultReceiver::class.java).apply {
                putExtra("package_name", packageName)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            // Desinstalar
            packageInstaller.uninstall(packageName, pendingIntent.intentSender)
            
            // Aguardar desinstalação
            delay(5000)
            
            val uninstalled = !isPackageInstalled(packageName)
            
            if (uninstalled) {
                Logger.info(
                    component = "heimdall.device.update.install",
                    message = "Package uninstalled successfully",
                    metadata = mapOf("package_name" to packageName)
                )
            }
            
            uninstalled
            
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.update.install",
                message = "Uninstallation failed",
                metadata = mapOf("package_name" to packageName),
                throwable = e
            )
            false
        }
    }
    
    /**
     * Verifica se um pacote está instalado
     */
    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Logger.warning(
                component = "heimdall.device.update.install",
                message = "Error checking package installation",
                metadata = mapOf("package_name" to packageName)
            )
            false
        }
    }
    
    /**
     * Obtém informações de um pacote instalado
     */
    fun getInstalledPackageInfo(packageName: String): PackageInfo? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            PackageInfo(
                packageName = packageName,
                versionName = packageInfo.versionName ?: "unknown",
                versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                },
                installedAt = packageInfo.firstInstallTime
            )
        } catch (e: Exception) {
            null
        }
    }
    
    data class PackageInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: Int,
        val installedAt: Long
    )
}

/**
 * BroadcastReceiver para receber resultado da desinstalação
 */
class UninstallResultReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        val packageName = intent.getStringExtra("package_name") ?: return
        val status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -1)
        
        when (status) {
            android.content.pm.PackageInstaller.STATUS_SUCCESS -> {
                Logger.info(
                    component = "heimdall.device.update.install.receiver",
                    message = "Uninstallation succeeded",
                    metadata = mapOf("package_name" to packageName)
                )
            }
            android.content.pm.PackageInstaller.STATUS_FAILURE -> {
                val statusMessage = intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Unknown error"
                Logger.error(
                    component = "heimdall.device.update.install.receiver",
                    message = "Uninstallation failed",
                    metadata = mapOf(
                        "package_name" to packageName,
                        "status_message" to statusMessage
                    )
                )
            }
        }
    }
}

/**
 * BroadcastReceiver para receber resultado da instalação
 */
class InstallResultReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        val packageName = intent.getStringExtra("package_name") ?: return
        val status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -1)
        
        when (status) {
            android.content.pm.PackageInstaller.STATUS_SUCCESS -> {
                Logger.info(
                    component = "heimdall.device.update.install.receiver",
                    message = "Installation succeeded",
                    metadata = mapOf("package_name" to packageName)
                )
            }
            android.content.pm.PackageInstaller.STATUS_FAILURE -> {
                val statusMessage = intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Unknown error"
                Logger.error(
                    component = "heimdall.device.update.install.receiver",
                    message = "Installation failed",
                    metadata = mapOf(
                        "package_name" to packageName,
                        "status_message" to statusMessage
                    )
                )
            }
            android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Logger.warning(
                    component = "heimdall.device.update.install.receiver",
                    message = "Installation aborted",
                    metadata = mapOf("package_name" to packageName)
                )
            }
            android.content.pm.PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                Logger.error(
                    component = "heimdall.device.update.install.receiver",
                    message = "Installation blocked",
                    metadata = mapOf("package_name" to packageName)
                )
            }
            android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                Logger.error(
                    component = "heimdall.device.update.install.receiver",
                    message = "Installation conflict",
                    metadata = mapOf("package_name" to packageName)
                )
            }
            android.content.pm.PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                Logger.error(
                    component = "heimdall.device.update.install.receiver",
                    message = "Package incompatible",
                    metadata = mapOf("package_name" to packageName)
                )
            }
            android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID -> {
                Logger.error(
                    component = "heimdall.device.update.install.receiver",
                    message = "Invalid package",
                    metadata = mapOf("package_name" to packageName)
                )
            }
            android.content.pm.PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Logger.error(
                    component = "heimdall.device.update.install.receiver",
                    message = "Insufficient storage",
                    metadata = mapOf("package_name" to packageName)
                )
            }
        }
    }
}

