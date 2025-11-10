package com.heimdall.device.update

import android.content.Context
import com.heimdall.device.util.Logger
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Gerencia download de APKs do CloudFront
 */
class DownloadManager(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadDir = File(context.filesDir, UpdateConfig.DOWNLOAD_DIR)
    
    init {
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
    }
    
    /**
     * Baixa APK do CloudFront
     */
    suspend fun downloadApk(
        url: String,
        packageName: String,
        expectedChecksum: String? = null,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: java.io.InputStream? = null
        var outputStream: FileOutputStream? = null
        
        try {
            Logger.info(
                component = "heimdall.device.update.download",
                message = "Starting APK download",
                metadata = mapOf(
                    "url" to url,
                    "package_name" to packageName
                )
            )
            
            val downloadUrl = URL(url)
            connection = downloadUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = UpdateConfig.CONNECTION_TIMEOUT_MS.toInt()
            connection.readTimeout = UpdateConfig.DOWNLOAD_TIMEOUT_MS.toInt()
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Heimdall-MDM/1.0")
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Logger.error(
                    component = "heimdall.device.update.download",
                    message = "Download failed: HTTP error",
                    metadata = mapOf(
                        "url" to url,
                        "response_code" to responseCode
                    )
                )
                return@withContext null
            }
            
            val contentLength = connection.contentLengthLong
            inputStream = connection.inputStream
            
            val tempFile = File(downloadDir, "${UpdateConfig.APK_FILE_PREFIX}${packageName}_${System.currentTimeMillis()}${UpdateConfig.APK_FILE_SUFFIX}")
            outputStream = FileOutputStream(tempFile)
            
            val buffer = ByteArray(8192)
            var bytesDownloaded = 0L
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                bytesDownloaded += bytesRead
                
                onProgress?.invoke(bytesDownloaded, contentLength)
                
                // Log progress a cada 10%
                if (contentLength > 0 && bytesDownloaded % (contentLength / 10) == 0L) {
                    val progress = (bytesDownloaded * 100 / contentLength).toInt()
                    Logger.debug(
                        component = "heimdall.device.update.download",
                        message = "Download progress",
                        metadata = mapOf(
                            "package_name" to packageName,
                            "progress_percent" to progress,
                            "bytes_downloaded" to bytesDownloaded,
                            "total_bytes" to contentLength
                        )
                    )
                }
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            Logger.info(
                component = "heimdall.device.update.download",
                message = "APK downloaded successfully",
                metadata = mapOf(
                    "package_name" to packageName,
                    "file_size" to tempFile.length(),
                    "file_path" to tempFile.absolutePath
                )
            )
            
            // Verificar checksum se fornecido
            if (expectedChecksum != null) {
                val actualChecksum = calculateChecksum(tempFile)
                if (actualChecksum != expectedChecksum) {
                    Logger.error(
                        component = "heimdall.device.update.download",
                        message = "Checksum mismatch",
                        metadata = mapOf(
                            "package_name" to packageName,
                            "expected" to expectedChecksum,
                            "actual" to actualChecksum
                        )
                    )
                    tempFile.delete()
                    return@withContext null
                }
                
                Logger.info(
                    component = "heimdall.device.update.download",
                    message = "Checksum verified",
                    metadata = mapOf("package_name" to packageName)
                )
            }
            
            tempFile
            
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.update.download",
                message = "Download failed",
                metadata = mapOf(
                    "url" to url,
                    "package_name" to packageName
                ),
                throwable = e
            )
            null
        } finally {
            try {
                outputStream?.close()
                inputStream?.close()
                connection?.disconnect()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Calcula checksum SHA-256 do arquivo
     */
    private fun calculateChecksum(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Limpa arquivos de download antigos
     */
    fun cleanupOldDownloads() {
        scope.launch {
            try {
                val files = downloadDir.listFiles() ?: return@launch
                val now = System.currentTimeMillis()
                val maxAge = 24 * 60 * 60 * 1000L // 24 horas
                
                files.forEach { file ->
                    if (now - file.lastModified() > maxAge) {
                        file.delete()
                        Logger.debug(
                            component = "heimdall.device.update.download",
                            message = "Deleted old download file",
                            metadata = mapOf("file" to file.name)
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.update.download",
                    message = "Error cleaning up old downloads",
                    throwable = e
                )
            }
        }
    }
}

