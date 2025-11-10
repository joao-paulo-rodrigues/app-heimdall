package com.heimdall.device.update

/**
 * Configurações para sistema de atualização e instalação
 */
object UpdateConfig {
    // Timeouts
    const val DOWNLOAD_TIMEOUT_MS = 120000L // 2 minutos
    const val INSTALL_TIMEOUT_MS = 60000L   // 1 minuto
    const val CONNECTION_TIMEOUT_MS = 30000L // 30 segundos
    
    // Retry configuration
    const val MAX_RETRY_ATTEMPTS = 3
    const val INITIAL_RETRY_DELAY_MS = 2000L
    const val MAX_RETRY_DELAY_MS = 30000L
    
    // Download directory
    const val DOWNLOAD_DIR = "heimdall_downloads"
    const val APK_FILE_PREFIX = "app_"
    const val APK_FILE_SUFFIX = ".apk"
}

