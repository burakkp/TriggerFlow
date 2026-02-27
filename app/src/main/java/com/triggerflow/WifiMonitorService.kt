package com.triggerflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class WifiMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "TriggerFlowChannel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "WifiMonitorService"
    }

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private lateinit var prefs: PreferencesManager
    private lateinit var wgController: WireGuardController
    private lateinit var wifiManager: WifiManager
    
    // Coroutine scope for debouncing
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var vpnJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        
        prefs = PreferencesManager(this)
        wgController = WireGuardController(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            val hasLocation = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val serviceType = if (hasLocation) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
            startForeground(NOTIFICATION_ID, createNotification("Monitoring WiFi..."), serviceType)
        } else {
            startForeground(NOTIFICATION_ID, createNotification("Monitoring WiFi..."))
        }
        prefs.isServiceRunning = true

        startNetworkMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If the service is killed, restart it
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
        serviceScope.cancel() // Cancel all pending jobs
        stopNetworkMonitoring()
        prefs.isServiceRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startNetworkMonitoring() {
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
            
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "WiFi Available")
                // Pass capabilities if possible, or wait for capabilities changed
                checkNetworkAndAct(network)
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "WiFi Lost")
                // When WiFi is genuinely lost, we want to enable VPN, but with a slight delay 
                // to avoid flickering if it comes back instantly or switches access points.
                checkNetworkAndAct(null) 
            }
            
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                // Determine SSID from capabilities if newer API allows
                checkNetworkAndAct(network, networkCapabilities)
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        // Initial check
        checkNetworkAndAct(null)
    }

    private fun stopNetworkMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering callback", e)
        }
    }

    private fun checkNetworkAndAct(network: Network? = null, capabilities: NetworkCapabilities? = null) {
        // Try to get SSID. 
        var currentSsid = getCurrentSsid(network, capabilities)
        
        // Save for UI Debugging
        prefs.lastKnownSsid = currentSsid

        val trustedSsids = prefs.trustedSsids
        val tunnelName = prefs.tunnelName
        
        Log.d(TAG, "Analysis: Current SSID: '$currentSsid', Trusted: $trustedSsids")

        if (tunnelName.isBlank()) {
            updateNotification("Configuration missing (Tunnel Name)")
            return
        }

        // 1. TRUSTED NETWORK FOUND
        if (trustedSsids.contains(currentSsid)) {
            // Cancel any pending Enable job immediately
            vpnJob?.cancel()
            updateNotification("Trusted WiFi ($currentSsid). VPN Disabled.")
            LogManager(this).addLog("Connected to Trusted: $currentSsid. VPN Disabled.")
            handleVpnState(enable = false)
            return
        }
        
        // 2. UNKNOWN SSID (Connected but system hasn't given us the name yet)
        if (network != null && (currentSsid.isEmpty() || currentSsid == "<unknown ssid>")) {
             Log.d(TAG, "Connected but SSID unknown. Possible Permission issue or Latency.")
             // We do NOT want to enable VPN immediately if we just connected to home.
             // But we can't wait forever.
             // Strategy: Trigger the "Enable VPN" flow, but the debounce will re-check the SSID.
             // If it resolves to Trusted inside the debounce, we are good.
        }

        // 3. UNTRUSTED or LOST
        // Action: Enable VPN (Debounced to allow for switching / SSID resolution)
        
        // If we are already scheduled to enable, let it run.
        if (vpnJob?.isActive == true) return
        
        vpnJob = serviceScope.launch {
            Log.d(TAG, "VPN Enable Sequence requested. Waiting 5s debounce...")
            delay(5000) // 5 Seconds Debounce
            
            // --- DOUBLE CHECK BEFORE FIRE ---
            // Re-read SSID. It might have populated by now.
            val reCheckSsid = getCurrentSsid(null, null) // Force re-read from WifiManager if needed
            Log.d(TAG, "Debounce finished. Re-checked SSID: '$reCheckSsid'")
            
            if (trustedSsids.contains(reCheckSsid)) {
                Log.d(TAG, "SSID resolved to Trusted during debounce. Aborting VPN Enable.")
                updateNotification("Trusted WiFi ($reCheckSsid). VPN Disabled.")
                LogManager(this@WifiMonitorService).addLog("Resolved Trusted ($reCheckSsid) during debounce. VPN Disabled.")
                handleVpnState(enable = false)
                return@launch
            }
            
            // If still unknown/untrusted, NOW we enable.
            Log.d(TAG, "Still Untrusted/Unknown. Enabling VPN.")
            updateNotification("External/No WiFi. VPN Enabled.")
            LogManager(this@WifiMonitorService).addLog("External/No WiFi. Enabling VPN.")
            handleVpnState(enable = true)
        }
    }

    private fun handleVpnState(enable: Boolean) {
        val tunnelName = if (prefs.isAdBlockEnabled) prefs.adBlockTunnelName else prefs.tunnelName
        if (tunnelName.isNotBlank()) {
            wgController.setTunnelState(tunnelName, enable)
        }
    }

    private fun getCurrentSsid(@Suppress("UNUSED_PARAMETER") network: Network?, capabilities: NetworkCapabilities?): String {
        var ssid: String? = null
        
        // 1. Try getting from NetworkCapabilities (Android Q+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && capabilities != null) {
             val info = capabilities.transportInfo
             if (info is WifiInfo) {
                 ssid = info.ssid
             }
        }
        
        // 2. Fallback to WifiManager (Legacy or if Capabilites didn't have it)
        if (ssid == null || ssid == WifiManager.UNKNOWN_SSID || ssid == "<unknown ssid>") {
             @Suppress("DEPRECATION")
             val info = wifiManager.connectionInfo
             ssid = info?.ssid
        }

        // Clean up quotes
        if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        
        return ssid ?: ""
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "TriggerFlow Monitoring Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TriggerFlow Monitor")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Will use default android icon if custom not present
            .setContentIntent(pendingIntent)
            .build()
    }
    
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.notify(NOTIFICATION_ID, notification)
    }
}
