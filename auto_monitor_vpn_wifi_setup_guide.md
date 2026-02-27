# Auto WireGuard - Complete Setup Guide

## 📋 Prerequisites

1. **Android Studio** (latest version)
2. **Android Device** running Android 8.0+ (API 26+)
3. **WireGuard App** installed from Play Store
4. **WireGuard configured** with your Raspberry Pi

---

## 🚀 Step 1: Create the Project

1. Open Android Studio
2. Click **File → New → New Project**
3. Select **Empty Activity**
4. Configure:
   - Name: `TriggerFlow`
   - Package: `com.triggerflow`
   - Language: **Kotlin**
   - Minimum SDK: **API 26 (Android 8.0)**

---

## 📁 Step 2: Add All Files

### 1. Update `build.gradle` (Module: app)
- Replace the entire file with the provided `build.gradle` content
- Click **Sync Now** when prompted

### 2. Update `AndroidManifest.xml`
- Replace in `app/src/main/AndroidManifest.xml`

### 3. Create Kotlin Files
Create these files in `app/src/main/java/com/triggerflow/`:

- `MainActivity.kt`
- `WifiMonitorService.kt`
- `NetworkChangeReceiver.kt`
- `WireGuardController.kt`
- `PreferencesManager.kt`

### 4. Create Layout File
- Replace `app/src/main/res/layout/activity_main.xml`

### 5. Update strings.xml
- Replace `app/src/main/res/values/strings.xml`

---

## 🔧 Step 3: Configure WireGuard

### On Your Raspberry Pi (PiHole + WireGuard):
1. Make sure WireGuard is properly configured
2. Note your tunnel name (usually `wg0`)

### On Your Android Phone:
1. Install **WireGuard** from Play Store
2. Add your tunnel configuration
3. **IMPORTANT**: In WireGuard app:
   - Go to Settings (three dots menu)
   - Enable **"Allow remote control"** or **"Control from apps"**
   - This allows our app to control WireGuard

---

## 📱 Step 4: Build & Install the App

1. Connect your Android device via USB
2. Enable **Developer Options** and **USB Debugging**
3. In Android Studio, click the **Run** button (green triangle)
4. Select your device

---

## ⚙️ Step 5: Configure the App

1. **Open the app**
2. **Enter Settings**:
   - **Home WiFi SSID**: Your home WiFi name (exactly as it appears, case-sensitive)
   - **WireGuard Tunnel Name**: Your tunnel name from WireGuard app (e.g., `wg0`)
   - **Enable Auto-Connect**: Toggle ON
3. **Click "Save Settings"**

---

## 🔋 Step 6: Disable Battery Optimization

1. Click **"Disable Battery Optimization"** button in the app
2. Select **"Allow"** or **"Don't restrict"**
3. This ensures the app works in background

---

## ▶️ Step 7: Start Monitoring

1. Click **"Start Monitoring"** button
2. Grant any permissions requested (WiFi, notifications)
3. You should see: **"Service Status: RUNNING ✓"**
4. A persistent notification will appear

---

## 🧪 Step 8: Test It!

### Test Case 1: Leave Home WiFi
1. Make sure you're connected to your home WiFi
2. VPN should be **disconnected**
3. Turn off WiFi or move away from home
4. **Expected**: VPN automatically connects

### Test Case 2: Return Home
1. Connect to your home WiFi
2. **Expected**: VPN automatically disconnects

### Test Case 3: Connect to Other WiFi
1. Connect to a different WiFi (coffee shop, work, etc.)
2. **Expected**: VPN automatically connects

---

## 🐛 Troubleshooting

### Issue: VPN not connecting automatically
**Solutions**:
1. Check WireGuard has "Allow remote control" enabled
2. Verify tunnel name matches exactly
3. Check app has all permissions granted
4. Look at Android logs: `adb logcat | grep WireGuard`

### Issue: Service stops after some time
**Solutions**:
1. Disable battery optimization (Step 6)
2. Go to phone Settings → Apps → Auto WireGuard → Battery → Unrestricted
3. Some manufacturers (Xiaomi, Huawei) need additional settings

### Issue: WiFi SSID not detected
**Solutions**:
1. Make sure SSID is entered exactly (case-sensitive)
2. Check WiFi permission is granted
3. On Android 10+, location permission might be needed

### Issue: App crashes
**Solutions**:
1. Check all files are copied correctly
2. Rebuild project: Build → Clean Project → Rebuild Project
3. Check Android Studio logs for errors

---

## 📊 Check Logs

To see what's happening:

```bash
# Connect phone via USB
adb logcat | grep -E "WireGuard|WifiMonitor"
```

You'll see logs like:
- "Connected to home WiFi: YourSSID"
- "Connected to other WiFi - enabling VPN"
- "WiFi disconnected - enabling VPN"

---

## 🔄 Advanced: Auto-Start on Boot

The app is already configured to start on boot! After reboot:
1. Wait a few seconds for Android to fully start
2. Check notification tray for the monitoring service
3. If not started, open app and click "Start Monitoring"

---

## 📝 Important Notes

### SSID Format:
- Must be **exact match** (case-sensitive)
- No quotes needed
- Example: If your WiFi is "MyHome WiFi", enter exactly: `MyHome WiFi`

### Tunnel Name:
- Check WireGuard app for exact name
- Usually: `wg0`, `home`, `pihole`, etc.
- Must match exactly

### Battery Life:
- The app uses minimal battery
- It only monitors WiFi state changes (not actively polling)
- Foreground service keeps it alive

### Privacy:
- All data stored locally
- No internet permission needed
- No data collection

---

## 🎯 Expected Behavior

| Scenario | VPN State |
|----------|-----------|
| Connected to home WiFi | ❌ Disconnected |
| Connected to other WiFi | ✅ Connected |
| Using mobile data | ✅ Connected |
| Airplane mode | ❌ Disconnected |
| Phone restart | ✅ Auto-starts monitoring |

---

## 🛠️ Customization Ideas

Want to enhance the app? You can add:
- Multiple home WiFi SSIDs
- Schedule-based VPN (enable only during work hours)
- Whitelist of trusted WiFi networks
- Kill switch (block internet if VPN fails)
- Widget for quick toggle

---

## 📞 Getting Help

If you encounter issues:
1. Check the troubleshooting section above
2. Review Android Studio logs
3. Verify WireGuard configuration on Raspberry Pi
4. Test WireGuard connection manually first

---

## ✅ Success Checklist

- [ ] Android Studio project created
- [ ] All files copied correctly
- [ ] Project builds without errors
- [ ] App installed on phone
- [ ] WireGuard "remote control" enabled
- [ ] Settings configured in app
- [ ] Battery optimization disabled
- [ ] Monitoring service started
- [ ] Tested leaving home WiFi
- [ ] Tested returning to home WiFi
- [ ] VPN connects/disconnects automatically

---

## 🎉 You're Done!

Your phone will now automatically:
- ✅ Connect to VPN when away from home
- ✅ Disconnect VPN when at home
- ✅ Use PiHole DNS filtering through VPN
- ✅ Start monitoring after phone restart

Enjoy your automatic VPN protection! 🔒