package com.triggerflow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.TextView
import android.widget.ImageButton
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var etSsidInput: EditText
    private lateinit var chipGroupSsids: ChipGroup
    private lateinit var btnAddSsid: Button
    private lateinit var etTunnelName: EditText
    private lateinit var etAdBlockTunnelName: EditText
    private lateinit var btnVpnInfo: ImageButton
    private lateinit var switchEnable: SwitchMaterial
    private lateinit var switchAdBlock: SwitchMaterial
    private lateinit var tvStatus: TextView
    private lateinit var tvDetectedSsid: TextView
    private lateinit var btnViewLogs: Button
    private lateinit var btnSave: Button
    private lateinit var btnPermissions: Button
    private lateinit var btnGoPro: Button
    
    private lateinit var billingManager: BillingManager
    private lateinit var promoCodeManager: PromoCodeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesManager(this)
        promoCodeManager = PromoCodeManager(prefs)
        billingManager = BillingManager(this, prefs, promoCodeManager)

        initViews()
        loadSettings()
        setupListeners()
        updateStatus()
    }

    private fun initViews() {
        etSsidInput = findViewById(R.id.etSsidInput)
        chipGroupSsids = findViewById(R.id.chipGroupSsids)
        btnAddSsid = findViewById(R.id.btnAddSsid)
        etTunnelName = findViewById(R.id.etTunnelName)
        etAdBlockTunnelName = findViewById(R.id.etAdBlockTunnelName)
        btnVpnInfo = findViewById(R.id.btnVpnInfo)
        switchEnable = findViewById(R.id.switchEnable)
        switchAdBlock = findViewById(R.id.switchAdBlock)
        tvStatus = findViewById(R.id.tvStatus)
        tvDetectedSsid = findViewById(R.id.tvDetectedSsid)
        btnViewLogs = findViewById(R.id.btnViewLogs)
        btnSave = findViewById(R.id.btnSave)
        btnPermissions = findViewById(R.id.btnPermissions)
        btnGoPro = findViewById(R.id.btnGoPro)
    }

    private fun loadSettings() {
        refreshChipGroup()
        etTunnelName.setText(prefs.tunnelName)
        etAdBlockTunnelName.setText(prefs.adBlockTunnelName)
        switchAdBlock.isChecked = prefs.isAdBlockEnabled
        switchEnable.isChecked = prefs.isEnabled
    }

    private fun setupListeners() {
        btnAddSsid.setOnClickListener {
            val newSsid = etSsidInput.text.toString().trim()
            if (newSsid.isNotEmpty()) {
                // Feature Lock: Multi-SSID
                if (prefs.trustedSsids.size >= 1 && !billingManager.isPremium.value) {
                    showPremiumLockDialog("Unlimited Networks")
                } else {
                    addSsid(newSsid)
                    etSsidInput.text.clear()
                }
            }
        }
        
        btnVpnInfo.setOnClickListener {
            showVpnInfoDialog()
        }
        
        btnViewLogs.setOnClickListener {
            // Feature Lock: Logs
            if (!billingManager.isPremium.value) {
                showPremiumLockDialog("Activity Logs")
            } else {
                showLogSheet()
            }
        }
        
        btnGoPro.setOnClickListener {
             startActivity(Intent(this, PremiumActivity::class.java))
        }

        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show()
        }

        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            prefs.isEnabled = isChecked
            if (isChecked) {
                saveSettings()
                try {
                    startMonitorService()
                    tvStatus.text = "Running"
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_active))
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error starting service", e)
                    Toast.makeText(this, "Error starting service: ${e.message}", Toast.LENGTH_SHORT).show()
                    switchEnable.isChecked = false
                    prefs.isEnabled = false
                }
            } else {
                try {
                    stopMonitorService()
                    tvStatus.text = "Stopped"
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_inactive))
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error stopping service", e)
                    Toast.makeText(this, "Error stopping service: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPermissions.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun saveSettings() {
        prefs.tunnelName = etTunnelName.text.toString().trim()
        prefs.adBlockTunnelName = etAdBlockTunnelName.text.toString().trim()
        prefs.isAdBlockEnabled = switchAdBlock.isChecked
    }
    
    private fun showVpnInfoDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_vpn_info_title)
            .setMessage(R.string.dialog_vpn_info_desc)
            .setPositiveButton("Got it", null)
            .show()
    }
    
    private fun addSsid(ssid: String) {
        val currentSet = prefs.trustedSsids.toMutableSet()
        if (!currentSet.contains(ssid)) {
            currentSet.add(ssid)
            prefs.trustedSsids = currentSet
            refreshChipGroup()
        }
    }

    private fun removeSsid(ssid: String) {
        val currentSet = prefs.trustedSsids.toMutableSet()
        if (currentSet.contains(ssid)) {
            currentSet.remove(ssid)
            prefs.trustedSsids = currentSet
            refreshChipGroup()
        }
    }
    
    private fun refreshChipGroup() {
        chipGroupSsids.removeAllViews()
        val ssids = prefs.trustedSsids
        for (ssid in ssids) {
            val chip = Chip(this)
            chip.text = ssid
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener {
                removeSsid(ssid)
            }
            chipGroupSsids.addView(chip)
        }
    }

    private fun startMonitorService() {
        val serviceIntent = Intent(this, WifiMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopMonitorService() {
        val serviceIntent = Intent(this, WifiMonitorService::class.java)
        stopService(serviceIntent)
    }

    private fun updateStatus() {
        val isRunning = prefs.isServiceRunning
        val statusText = if (isRunning) "Running" else "Stopped"
        val statusColor = if (isRunning) R.color.status_active else R.color.status_inactive
        
        tvStatus.text = statusText
        tvStatus.setTextColor(ContextCompat.getColor(this, statusColor))
        
        val lastSsid = prefs.lastKnownSsid
        tvDetectedSsid.text = "Detected SSID: ${if(lastSsid.isEmpty()) "NONE/UNKNOWN" else lastSsid}"
    }

    private fun showLogSheet() {
        val dialog = BottomSheetDialog(this)
        
        val context = this
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_dark))
        }
        
        val title = TextView(context).apply {
            text = "Activity Logs"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_high_emphasis))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 16)
        }
        
        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 
                800
            )
        }
        
        val logText = TextView(context).apply {
            val logs = LogManager(context).getLogs()
            text = if (logs.isEmpty()) "No activity yet." else logs.joinToString("\n\n")
            setTextColor(ContextCompat.getColor(context, R.color.text_medium_emphasis))
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        
        scrollView.addView(logText)
        layout.addView(title)
        layout.addView(scrollView)
        
        dialog.setContentView(layout)
        dialog.show()
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val wgPermission = "com.wireguard.android.permission.CONTROL_TUNNELS"
        if (ContextCompat.checkSelfPermission(this, wgPermission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(wgPermission)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        } else {
            Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        
        billingManager.updatePremiumState()
        
        if (billingManager.isPremium.value) {
            btnGoPro.visibility = android.view.View.GONE
        } else {
            btnGoPro.visibility = android.view.View.VISIBLE
        }

        if (prefs.isEnabled && !prefs.isServiceRunning) {
            startMonitorService()
        }
        
        updateStatus()
    }
    
    private fun showPremiumLockDialog(feature: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("$feature is a Pro Feature")
            .setMessage("Upgrade to TriggerFlow Pro to unlock $feature, remove limits, and support development.")
            .setPositiveButton("Upgrade") { _, _ -> 
                startActivity(Intent(this, PremiumActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
