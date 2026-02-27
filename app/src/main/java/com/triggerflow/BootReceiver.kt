package com.triggerflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed received")
            
            val prefs = PreferencesManager(context)
            if (prefs.isEnabled) {
                Log.d("BootReceiver", "Automation enabled, starting service...")
                startMonitorService(context)
            } else {
                Log.d("BootReceiver", "Automation disabled, ignoring boot.")
            }
        }
    }

    private fun startMonitorService(context: Context) {
        val serviceIntent = Intent(context, WifiMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
