package com.heimdall.device

import android.app.Application
// Room temporariamente desabilitado devido a incompatibilidade kapt com Java 21
// import com.heimdall.device.database.HeimdallDatabase
import com.heimdall.device.service.MqttServiceManager
import com.heimdall.device.util.Logger
// Hilt temporariamente desabilitado
// import dagger.hilt.android.HiltAndroidApp

// @HiltAndroidApp
class HeimdallApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        Logger.initialize(this)
        
        // Room Database temporariamente desabilitado
        // try {
        //     val database = HeimdallDatabase.getDatabase(this)
        //     Logger.info(
        //         component = "heimdall.device.application",
        //         message = "Room Database initialized"
        //     )
        // } catch (e: Exception) {
        //     Logger.error(
        //         component = "heimdall.device.application",
        //         message = "Failed to initialize Room Database",
        //         throwable = e
        //     )
        // }
        
        val mqttManager = MqttServiceManager.getInstance(this)
        
        Logger.info(
            component = "heimdall.device.application",
            message = "Heimdall application initialized",
            metadata = mapOf("version" to "1.0.0")
        )
    }
}

