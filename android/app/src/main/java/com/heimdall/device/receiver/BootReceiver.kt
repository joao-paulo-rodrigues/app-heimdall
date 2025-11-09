package com.heimdall.device.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.heimdall.device.service.MqttServiceManager
import com.heimdall.device.util.Logger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Logger.info(
                component = "heimdall.device.boot",
                message = "Device boot completed, initializing Heimdall services"
            )
            
            val mqttManager = MqttServiceManager.getInstance(context)
            mqttManager.connect()
        }
    }
}


