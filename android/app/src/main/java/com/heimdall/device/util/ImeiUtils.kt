package com.heimdall.device.util

import android.content.Context
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Utilitários para obtenção e gerenciamento do IMEI do dispositivo
 * - 15 dígitos numéricos
 * - Luhn-válido
 * - Dual-source: tenta IMEI real, senão usa IMEI determinístico
 * - Persistência criptografada para estabilidade
 */
object ImeiUtils {
    private const val TAG = "ImeiUtils"
    private const val PREFS_NAME = "heimdall_prefs_secure"
    private const val KEY_IMEI = "device_imei"
    private const val KEY_IMEI_SOURCE = "device_imei_source"
    private const val KEY_IMEI_SECONDARY = "device_imei_secondary"

    data class ImeiInfo(
        val imei: String,
        val source: String, // "real" | "synthetic"
        val validLuhn: Boolean,
        val secondary: String? = null
    )

    /**
     * Mantém compatibilidade com chamadas existentes.
     */
    fun getOrGenerateImei(context: Context): String = getBestImei(context).imei

    /**
     * Retorna IMEI com metadados. Garante 15 dígitos e Luhn.
     */
    fun getBestImei(context: Context): ImeiInfo {
        // 1) Ler de storage seguro se existente e válido
        loadFromSecureStorage(context)?.let { saved ->
            if (isValidImei(saved.imei)) {
                Logger.debug(
                    component = "heimdall.device.util.imei",
                    message = "IMEI loaded from secure storage",
                    metadata = mapOf(
                        "imei" to saved.imei,
                        "source" to saved.source
                    )
                )
                return saved
            }
        }

        // 2) Tentar IMEI real (quando permitido/viável)
        val real = tryGetRealImei(context)
        if (real != null && isValidImei(real.first)) {
            val info = ImeiInfo(real.first, source = "real", validLuhn = true, secondary = real.second)
            persistSecure(context, info)
            Logger.info(
                component = "heimdall.device.util.imei",
                message = "Real IMEI obtained and persisted",
                metadata = mapOf("imei" to real.first)
            )
            return info
        }

        // 3) Gerar determinístico com base no ANDROID_ID (sintético), 15 dígitos Luhn
        val synthetic = generateDeterministicImei(context)
        
        // Verificar e corrigir se necessário
        val validImei = if (isValidImei(synthetic)) {
            synthetic
        } else {
            Logger.warning(
                component = "heimdall.device.util.imei",
                message = "Generated IMEI is invalid, correcting check digit"
            )
            val base14 = synthetic.substring(0, 14)
            val sum = calculateLuhnSum(base14)
            val validCheckDigit = (10 - (sum % 10)) % 10
            val corrected = base14 + validCheckDigit.toString()
            Logger.debug(
                component = "heimdall.device.util.imei",
                message = "IMEI corrected",
                metadata = mapOf(
                    "original" to synthetic,
                    "corrected" to corrected
                )
            )
            corrected
        }
        
        val info = ImeiInfo(validImei, source = "synthetic", validLuhn = true, secondary = null)
        persistSecure(context, info)
        Logger.info(
            component = "heimdall.device.util.imei",
            message = "Synthetic IMEI generated and persisted",
            metadata = mapOf("imei" to validImei)
        )
        return info
    }

    private fun tryGetRealImei(context: Context): Pair<String, String?>? {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            // deviceId é legacy; tentar slots via reflexão suave quando disponível
            val primary = tm.deviceId ?: return null
            val secondary: String? = try {
                tm.javaClass.getMethod("getDeviceId", Int::class.java).invoke(tm, 1) as? String
            } catch (_: Exception) { null }
            val cleanedPrimary = primary.filter { it.isDigit() }.take(15)
            val cleanedSecondary = secondary?.filter { it.isDigit() }?.take(15)
            if (cleanedPrimary.length == 15) cleanedPrimary to cleanedSecondary else null
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun generateDeterministicImei(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown_android_id"
        
        Logger.debug(
            component = "heimdall.device.util.imei",
            message = "Generating deterministic IMEI",
            metadata = mapOf("android_id" to androidId)
        )
        
        // Gerar 14 dígitos base a partir do hash do ANDROID_ID e prefixo "86"
        val base14 = buildString {
            append("86")
            val hash = sha1(androidId)
            var idx = 0
            while (length < 14) {
                val ch = hash[idx % hash.length]
                val v = Character.digit(ch, 16)
                append(((if (v >= 0) v else 0) % 10))
                idx++
            }
        }
        
        val check = calculateLuhnCheckDigit(base14)
        val finalImei = base14 + check.toString()
        
        Logger.debug(
            component = "heimdall.device.util.imei",
            message = "IMEI generated",
            metadata = mapOf(
                "base14" to base14,
                "check_digit" to check,
                "final_imei" to finalImei,
                "valid" to isValidImei(finalImei)
            )
        )
        
        return finalImei
    }

    private fun sha1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(input.toByteArray())
        return bytes.joinToString("") { b -> "%02x".format(b) }
    }

    fun isValidImei(imei: String?): Boolean {
        if (imei.isNullOrBlank() || imei.length != 15 || imei.any { it !in '0'..'9' }) return false
        
        // Algoritmo de Luhn
        val sum = calculateLuhnSumForFullImei(imei)
        return sum % 10 == 0
    }

    private fun calculateLuhnCheckDigit(digits: String): Int {
        val sum = calculateLuhnSum(digits)
        return (10 - (sum % 10)) % 10
    }
    
    private fun calculateLuhnSum(digits: String): Int {
        var sum = 0
        var odd = digits.length % 2
        
        // Algoritmo de Luhn
        for (i in digits.indices) {
            val digit = digits[i].code - '0'.code
            if (odd == 1) {
                // Posição ímpar: usar dígito original
                sum += digit
            } else {
                // Posição par: dobrar e ajustar se > 9
                val doubled = digit * 2
                sum += if (doubled > 9) doubled - 9 else doubled
            }
            odd = if (odd == 1) 0 else 1
        }
        
        return sum
    }
    
    private fun calculateLuhnSumForFullImei(imei: String): Int {
        var sum = 0
        var odd = imei.length % 2
        
        // Algoritmo de Luhn para IMEI completo
        for (i in imei.indices) {
            val digit = imei[i].code - '0'.code
            if (odd == 1) {
                // Posição ímpar: usar dígito original
                sum += digit
            } else {
                // Posição par: dobrar e ajustar se > 9
                val doubled = digit * 2
                sum += if (doubled > 9) doubled - 9 else doubled
            }
            odd = if (odd == 1) 0 else 1
        }
        
        return sum
    }

    private fun persistSecure(context: Context, info: ImeiInfo) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs.edit()
                .putString(KEY_IMEI, info.imei)
                .putString(KEY_IMEI_SOURCE, info.source)
                .putString(KEY_IMEI_SECONDARY, info.secondary)
                .apply()
            
            Logger.debug(
                component = "heimdall.device.util.imei",
                message = "IMEI persisted to secure storage",
                metadata = mapOf(
                    "imei" to info.imei,
                    "source" to info.source
                )
            )
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.util.imei",
                message = "Failed to persist IMEI to secure storage",
                throwable = e
            )
        }
    }

    private fun loadFromSecureStorage(context: Context): ImeiInfo? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        val imei = prefs.getString(KEY_IMEI, null)
        val source = prefs.getString(KEY_IMEI_SOURCE, null)
        val secondary = prefs.getString(KEY_IMEI_SECONDARY, null)
        if (imei != null && source != null) {
            ImeiInfo(imei, source, isValidImei(imei), secondary)
        } else {
            null
        }
    } catch (e: Exception) {
        Logger.warning(
            component = "heimdall.device.util.imei",
            message = "Failed to load IMEI from secure storage",
            metadata = mapOf("error" to (e.message ?: "Unknown error"))
        )
        null
    }
}

