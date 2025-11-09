package com.heimdall.device.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.heimdall.device.util.Logger

class HeimdallDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        try {
            Logger.initialize(context)
            Logger.info(
                component = "heimdall.device.deviceadmin",
                message = "Device Admin enabled - Heimdall is now Device Owner",
                metadata = mapOf("component" to getComponentName(context).flattenToString())
            )
        } catch (e: Exception) {
            android.util.Log.e("HeimdallDeviceAdmin", "Error in onEnabled", e)
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        try {
            Logger.initialize(context)
            Logger.warning(
                component = "heimdall.device.deviceadmin",
                message = "Device Admin disabled"
            )
        } catch (e: Exception) {
            android.util.Log.e("HeimdallDeviceAdmin", "Error in onDisabled", e)
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        try {
            Logger.initialize(context)
            Logger.warning(
                component = "heimdall.device.deviceadmin",
                message = "Device Admin disable requested"
            )
        } catch (e: Exception) {
            android.util.Log.e("HeimdallDeviceAdmin", "Error in onDisableRequested", e)
        }
        return "Disabling Device Admin will remove Heimdall MDM capabilities"
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, HeimdallDeviceAdminReceiver::class.java)
        }

        fun isDeviceOwner(context: Context): Boolean {
            val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return devicePolicyManager.isDeviceOwnerApp(context.packageName)
        }
    }
}

