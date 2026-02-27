package com.triggerflow

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class TriggerTileService : TileService() {

    override fun onClick() {
        val prefs = PreferencesManager(this)
        val newState = !prefs.isEnabled
        prefs.isEnabled = newState

        // Update UI immediately
        updateTile(newState)

        // Trigger Service Action
        if (newState) {
            val serviceIntent = Intent(this, WifiMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            val serviceIntent = Intent(this, WifiMonitorService::class.java)
            stopService(serviceIntent)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val prefs = PreferencesManager(this)
        updateTile(prefs.isEnabled)
    }

    private fun updateTile(isEnabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        // Label is set in Manifest, but can be updated here if needed
        // tile.label = "TriggerFlow" 
        tile.updateTile()
    }
}
