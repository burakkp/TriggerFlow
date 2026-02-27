package com.triggerflow

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("TriggerFlowLogs", Context.MODE_PRIVATE)
    private val KEY_LOGS = "activity_logs"
    private val MAX_LOGS = 50

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val newEntry = "[$timestamp] $message"
        
        val currentLogs = getLogs().toMutableList()
        currentLogs.add(0, newEntry) // Add to top
        
        if (currentLogs.size > MAX_LOGS) {
            currentLogs.removeAt(currentLogs.lastIndex)
        }
        
        saveLogs(currentLogs)
    }

    fun getLogs(): List<String> {
        val logString = prefs.getString(KEY_LOGS, "") ?: ""
        if (logString.isBlank()) return emptyList()
        return logString.split("|||")
    }
    
    fun clearLogs() {
        prefs.edit().remove(KEY_LOGS).apply()
    }

    private fun saveLogs(logs: List<String>) {
        val joined = logs.joinToString("|||")
        prefs.edit().putString(KEY_LOGS, joined).apply()
    }
}
