package com.heimdall.device.update

/**
 * Resultado de uma operação de atualização/instalação
 */
data class UpdateResult(
    val success: Boolean,
    val packageName: String,
    val message: String,
    val versionName: String? = null,
    val versionCode: Int? = null,
    val error: String? = null,
    val errorCode: String? = null,
    val downloadSize: Long? = null,
    val downloadTime: Long? = null,
    val installTime: Long? = null
) {
    companion object {
        fun success(
            packageName: String,
            message: String,
            versionName: String? = null,
            versionCode: Int? = null,
            downloadSize: Long? = null,
            downloadTime: Long? = null,
            installTime: Long? = null
        ): UpdateResult {
            return UpdateResult(
                success = true,
                packageName = packageName,
                message = message,
                versionName = versionName,
                versionCode = versionCode,
                downloadSize = downloadSize,
                downloadTime = downloadTime,
                installTime = installTime
            )
        }
        
        fun error(packageName: String, message: String, errorCode: String? = null, error: String? = null): UpdateResult {
            return UpdateResult(
                success = false,
                packageName = packageName,
                message = message,
                error = error,
                errorCode = errorCode
            )
        }
    }
}

