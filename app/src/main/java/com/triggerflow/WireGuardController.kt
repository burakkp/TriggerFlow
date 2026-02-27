package com.triggerflow

import android.content.Context
import android.content.Intent
import android.util.Log

class WireGuardController(private val context: Context) {

    companion object {
        private const val TAG = "WireGuardController"
        private const val WG_PACKAGE = "com.wireguard.android"
        private const val ACTION_SET_TUNNEL_UP = "com.wireguard.android.action.SET_TUNNEL_UP"
        private const val ACTION_SET_TUNNEL_DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN"
        private const val EXTRA_TUNNEL = "tunnel"
    }

    fun setTunnelState(tunnelName: String, enabled: Boolean) {
        if (tunnelName.isBlank()) {
            Log.e(TAG, "Tunnel name is empty, cannot change state")
            return
        }

        val action = if (enabled) ACTION_SET_TUNNEL_UP else ACTION_SET_TUNNEL_DOWN
        
        try {
            val intent = Intent(action)
            intent.setPackage(WG_PACKAGE)
            intent.putExtra(EXTRA_TUNNEL, tunnelName)
            context.sendBroadcast(intent)
            Log.i(TAG, "Sent broadcast: $action for tunnel '$tunnelName'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send WireGuard broadcast", e)
        }
    }
}
