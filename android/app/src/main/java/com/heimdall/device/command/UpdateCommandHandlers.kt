package com.heimdall.device.command

import android.content.Context
import com.heimdall.device.update.UpdateManager
import com.heimdall.device.util.Logger
import kotlinx.coroutines.delay

/**
 * Handlers de comandos para instalação e atualização de apps
 */

/**
 * Handler para comando de instalar app
 */
fun installAppHandler(context: Context): suspend (Command) -> CommandResult {
    return { command ->
        try {
            val url = command.params["url"] as? String
            if (url == null) {
                CommandResult(
                    commandId = command.commandId,
                    command = command.command,
                    status = CommandResult.CommandStatus.REJECTED,
                    message = "Missing required parameter: url",
                    traceId = command.traceId
                )
            } else {
                val packageName = command.params["package_name"] as? String
                val force = (command.params["force"] as? Boolean) 
                    ?: (command.params["force"] as? String)?.toBoolean() 
                    ?: false
                
                Logger.info(
                    component = "heimdall.device.command.update",
                    message = "Installing app from URL",
                    metadata = mapOf(
                        "url" to url,
                        "package_name" to (packageName ?: "unknown"),
                        "force" to force
                    ),
                    traceId = command.traceId
                )
                
                val updateManager = UpdateManager.getInstance(context)
                val result = updateManager.installFromUrl(url, packageName, force)
                
                if (result.success) {
                    CommandResult(
                        commandId = command.commandId,
                        command = command.command,
                        status = CommandResult.CommandStatus.SUCCESS,
                        message = result.message,
                        data = mapOf(
                            "package_name" to result.packageName,
                            "version_name" to (result.versionName ?: ""),
                            "version_code" to (result.versionCode ?: -1),
                            "download_size" to (result.downloadSize ?: 0),
                            "download_time_ms" to (result.downloadTime ?: 0),
                            "install_time_ms" to (result.installTime ?: 0)
                        ),
                        traceId = command.traceId
                    )
                } else {
                    CommandResult(
                        commandId = command.commandId,
                        command = command.command,
                        status = CommandResult.CommandStatus.ERROR,
                        message = result.message,
                        error = result.error,
                        data = mapOf(
                            "package_name" to result.packageName,
                            "error_code" to (result.errorCode ?: "")
                        ),
                        traceId = command.traceId
                    )
                }
            }
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.command.update",
                message = "Error installing app",
                metadata = mapOf("command_id" to command.commandId),
                throwable = e,
                traceId = command.traceId
            )
            
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.ERROR,
                message = "Installation failed: ${e.message}",
                error = e.message ?: "Unknown error",
                traceId = command.traceId
            )
        }
    }
}

/**
 * Handler para comando de atualizar app (mesmo que install_app, apenas alias)
 */
fun updateAppHandler(context: Context): suspend (Command) -> CommandResult {
    // Reutiliza o mesmo handler de instalação
    return installAppHandler(context)
}

/**
 * Handler para comando de desinstalar app
 */
fun uninstallAppHandler(context: Context): suspend (Command) -> CommandResult {
    return { command ->
        try {
            val packageName = command.params["package_name"] as? String
            if (packageName == null) {
                CommandResult(
                    commandId = command.commandId,
                    command = command.command,
                    status = CommandResult.CommandStatus.REJECTED,
                    message = "Missing required parameter: package_name",
                    traceId = command.traceId
                )
            } else {
                Logger.info(
                    component = "heimdall.device.command.update",
                    message = "Uninstalling app",
                    metadata = mapOf("package_name" to packageName),
                    traceId = command.traceId
                )
                
                val updateManager = UpdateManager.getInstance(context)
                val result = updateManager.uninstallApp(packageName)
                
                if (result.success) {
                    CommandResult(
                        commandId = command.commandId,
                        command = command.command,
                        status = CommandResult.CommandStatus.SUCCESS,
                        message = result.message,
                        data = mapOf("package_name" to result.packageName),
                        traceId = command.traceId
                    )
                } else {
                    CommandResult(
                        commandId = command.commandId,
                        command = command.command,
                        status = CommandResult.CommandStatus.ERROR,
                        message = result.message,
                        error = result.error,
                        data = mapOf(
                            "package_name" to result.packageName,
                            "error_code" to (result.errorCode ?: "")
                        ),
                        traceId = command.traceId
                    )
                }
            }
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.command.update",
                message = "Error uninstalling app",
                metadata = mapOf("command_id" to command.commandId),
                throwable = e,
                traceId = command.traceId
            )
            
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.ERROR,
                message = "Uninstall failed: ${e.message}",
                error = e.message ?: "Unknown error",
                traceId = command.traceId
            )
        }
    }
}

/**
 * Handler para comando de verificar versão instalada
 */
fun checkUpdatesHandler(context: Context): suspend (Command) -> CommandResult {
    return { command ->
        try {
            val packageName = command.params["package_name"] as? String
            
            if (packageName == null) {
                CommandResult(
                    commandId = command.commandId,
                    command = command.command,
                    status = CommandResult.CommandStatus.REJECTED,
                    message = "Missing required parameter: package_name",
                    traceId = command.traceId
                )
            } else {
                Logger.info(
                    component = "heimdall.device.command.update",
                    message = "Checking installed version",
                    metadata = mapOf("package_name" to packageName),
                    traceId = command.traceId
                )
                
                val updateManager = UpdateManager.getInstance(context)
                val installedInfo = updateManager.getInstalledVersion(packageName)
                
                if (installedInfo != null) {
                    CommandResult(
                        commandId = command.commandId,
                        command = command.command,
                        status = CommandResult.CommandStatus.SUCCESS,
                        message = "Version check completed",
                        data = mapOf(
                            "package_name" to packageName,
                            "installed_version_name" to installedInfo.versionName,
                            "installed_version_code" to installedInfo.versionCode,
                            "installed_at" to installedInfo.installedAt
                        ),
                        traceId = command.traceId
                    )
                } else {
                    CommandResult(
                        commandId = command.commandId,
                        command = command.command,
                        status = CommandResult.CommandStatus.SUCCESS,
                        message = "Package not installed",
                        data = mapOf(
                            "package_name" to packageName,
                            "installed" to false
                        ),
                        traceId = command.traceId
                    )
                }
            }
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.command.update",
                message = "Error checking version",
                metadata = mapOf("command_id" to command.commandId),
                throwable = e,
                traceId = command.traceId
            )
            
            CommandResult(
                commandId = command.commandId,
                command = command.command,
                status = CommandResult.CommandStatus.ERROR,
                message = "Version check failed: ${e.message}",
                error = e.message ?: "Unknown error",
                traceId = command.traceId
            )
        }
    }
}

