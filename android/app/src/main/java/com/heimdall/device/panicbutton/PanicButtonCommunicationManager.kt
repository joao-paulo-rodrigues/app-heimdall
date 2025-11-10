package com.heimdall.device.panicbutton

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import com.heimdall.device.util.Logger
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Gerenciador de comunicação robusta entre Heimdall e PanicButton
 * Suporta múltiplos métodos de comunicação:
 * - BroadcastReceiver (eventos em tempo real)
 * - SharedPreferences (dados compartilhados)
 * - ContentProvider (dados estruturados)
 * - Intents (comandos diretos)
 */
class PanicButtonCommunicationManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "PanicButtonComm"
        const val PANIC_BUTTON_PACKAGE = "com.uebrasil.panicbuttonapp"
        const val SHARED_PREFS_NAME = "heimdall_panicbutton_shared"
        
        // Ações de Broadcast
        const val ACTION_PANIC_TRIGGERED = "com.uebrasil.panicbuttonapp.PANIC_TRIGGERED"
        const val ACTION_STATUS_UPDATE = "com.uebrasil.panicbuttonapp.STATUS_UPDATE"
        const val ACTION_SENSOR_DATA = "com.uebrasil.panicbuttonapp.SENSOR_DATA"
        const val ACTION_HEIMDALL_COMMAND = "com.heimdall.device.COMMAND"
        const val ACTION_HEIMDALL_CONFIG = "com.heimdall.device.CONFIG"
        
        // Chaves de SharedPreferences
        const val KEY_IMEI = "device_imei"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_TENANT_ID = "tenant_id"
        const val KEY_MQTT_CONFIG = "mqtt_config"
        const val KEY_KIOSK_MODE = "kiosk_mode_enabled"
        const val KEY_LAST_UPDATE = "last_update_timestamp"
        
        @Volatile
        private var INSTANCE: PanicButtonCommunicationManager? = null
        
        fun getInstance(context: Context): PanicButtonCommunicationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PanicButtonCommunicationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
    private val eventListeners = ConcurrentHashMap<String, MutableList<(JSONObject) -> Unit>>()
    private var broadcastReceiver: BroadcastReceiver? = null
    
    /**
     * Verifica se o PanicButton está instalado
     */
    fun isPanicButtonInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(PANIC_BUTTON_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            Logger.warning(
                component = "heimdall.device.panicbutton",
                message = "Error checking PanicButton installation",
                metadata = mapOf("error" to (e.message ?: "Unknown"))
            )
            false
        }
    }
    
    /**
     * Inicializa o sistema de comunicação
     */
    fun initialize() {
        Logger.info(
            component = "heimdall.device.panicbutton",
            message = "Initializing PanicButton communication system"
        )
        
        registerBroadcastReceiver()
        syncSharedData()
    }
    
    /**
     * Registra BroadcastReceiver para receber eventos do PanicButton
     */
    private fun registerBroadcastReceiver() {
        if (broadcastReceiver != null) {
            return
        }
        
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                handleBroadcast(intent)
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(ACTION_PANIC_TRIGGERED)
            addAction(ACTION_STATUS_UPDATE)
            addAction(ACTION_SENSOR_DATA)
        }
        
        try {
            context.registerReceiver(broadcastReceiver, filter)
            Logger.info(
                component = "heimdall.device.panicbutton",
                message = "BroadcastReceiver registered"
            )
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.panicbutton",
                message = "Failed to register BroadcastReceiver",
                throwable = e
            )
        }
    }
    
    /**
     * Processa broadcasts recebidos do PanicButton
     */
    private fun handleBroadcast(intent: Intent) {
        try {
            val action = intent.action ?: return
            val data = JSONObject()
            
            // Extrair dados do intent
            intent.extras?.keySet()?.forEach { key ->
                val value = intent.extras?.get(key)
                when (value) {
                    is String -> data.put(key, value)
                    is Int -> data.put(key, value)
                    is Long -> data.put(key, value)
                    is Boolean -> data.put(key, value)
                    is Double -> data.put(key, value)
                    else -> data.put(key, value?.toString() ?: "")
                }
            }
            
            data.put("action", action)
            data.put("timestamp", System.currentTimeMillis())
            
            Logger.info(
                component = "heimdall.device.panicbutton",
                message = "Broadcast received from PanicButton",
                metadata = mapOf("action" to action)
            )
            
            // Notificar listeners
            notifyListeners(action, data)
            
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.panicbutton",
                message = "Error handling broadcast",
                throwable = e
            )
        }
    }
    
    /**
     * Sincroniza dados compartilhados com o PanicButton
     */
    fun syncSharedData() {
        scope.launch {
            try {
                val deviceId = com.heimdall.device.util.Logger.getDeviceId()
                val tenantId = "UEBRASIL"
                
                // Compartilhar IMEI/Device ID (o deviceId já é o IMEI)
                shareData(KEY_DEVICE_ID, deviceId)
                shareData(KEY_IMEI, deviceId) // IMEI explícito
                shareData(KEY_TENANT_ID, tenantId)
                
                // Compartilhar configuração MQTT
                val mqttConfig = JSONObject().apply {
                    put("host", "177.87.122.5")
                    put("port", 1883)
                    put("username", "mosquitto_broker_user_ue")
                    put("tenant_id", tenantId)
                }
                shareData(KEY_MQTT_CONFIG, mqttConfig.toString())
                
                Logger.info(
                    component = "heimdall.device.panicbutton",
                    message = "Shared data synchronized",
                    metadata = mapOf(
                        "device_id" to deviceId,
                        "tenant_id" to tenantId
                    )
                )
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.panicbutton",
                    message = "Error syncing shared data",
                    throwable = e
                )
            }
        }
    }
    
    /**
     * Compartilha um dado com o PanicButton via SharedPreferences
     */
    fun shareData(key: String, value: String) {
        try {
            sharedPrefs.edit()
                .putString(key, value)
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply()
            
            Logger.debug(
                component = "heimdall.device.panicbutton",
                message = "Data shared with PanicButton",
                metadata = mapOf("key" to key)
            )
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.panicbutton",
                message = "Error sharing data",
                metadata = mapOf("key" to key),
                throwable = e
            )
        }
    }
    
    /**
     * Obtém um dado compartilhado
     */
    fun getSharedData(key: String, defaultValue: String = ""): String {
        return sharedPrefs.getString(key, defaultValue) ?: defaultValue
    }
    
    /**
     * Envia comando direto ao PanicButton via Intent
     */
    fun sendCommand(action: String, data: Map<String, Any> = emptyMap()): Boolean {
        if (!isPanicButtonInstalled()) {
            Logger.warning(
                component = "heimdall.device.panicbutton",
                message = "Cannot send command: PanicButton not installed",
                metadata = mapOf("action" to action)
            )
            return false
        }
        
        return try {
            val intent = Intent(action).apply {
                setPackage(PANIC_BUTTON_PACKAGE)
                data.forEach { (key, value) ->
                    when (value) {
                        is String -> putExtra(key, value)
                        is Int -> putExtra(key, value)
                        is Long -> putExtra(key, value)
                        is Boolean -> putExtra(key, value)
                        is Double -> putExtra(key, value)
                        else -> putExtra(key, value.toString())
                    }
                }
            }
            
            context.sendBroadcast(intent)
            
            Logger.info(
                component = "heimdall.device.panicbutton",
                message = "Command sent to PanicButton",
                metadata = mapOf("action" to action, "data_keys" to data.keys.toList())
            )
            
            true
        } catch (e: Exception) {
            Logger.error(
                component = "heimdall.device.panicbutton",
                message = "Error sending command to PanicButton",
                metadata = mapOf("action" to action),
                throwable = e
            )
            false
        }
    }
    
    /**
     * Registra listener para eventos do PanicButton
     */
    fun registerEventListener(action: String, listener: (JSONObject) -> Unit) {
        eventListeners.getOrPut(action) { mutableListOf() }.add(listener)
        
        Logger.debug(
            component = "heimdall.device.panicbutton",
            message = "Event listener registered",
            metadata = mapOf("action" to action)
        )
    }
    
    /**
     * Remove listener de eventos
     */
    fun unregisterEventListener(action: String, listener: (JSONObject) -> Unit) {
        eventListeners[action]?.remove(listener)
    }
    
    /**
     * Notifica todos os listeners de um evento
     */
    private fun notifyListeners(action: String, data: JSONObject) {
        eventListeners[action]?.forEach { listener ->
            try {
                listener(data)
            } catch (e: Exception) {
                Logger.error(
                    component = "heimdall.device.panicbutton",
                    message = "Error in event listener",
                    metadata = mapOf("action" to action),
                    throwable = e
                )
            }
        }
    }
    
    /**
     * Configura modo kiosk do PanicButton
     */
    fun configureKioskMode(enabled: Boolean) {
        shareData(KEY_KIOSK_MODE, enabled.toString())
        sendCommand(ACTION_HEIMDALL_CONFIG, mapOf("kiosk_mode" to enabled))
        
        Logger.info(
            component = "heimdall.device.panicbutton",
            message = "Kiosk mode configured",
            metadata = mapOf("enabled" to enabled)
        )
    }
    
    /**
     * Limpa recursos
     */
    fun cleanup() {
        broadcastReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Receiver já foi desregistrado
            }
        }
        broadcastReceiver = null
        eventListeners.clear()
    }
}


