# Windows Testing Guide

## Files You Need

1. **device-bridge-1.0.0.jar** (725 MB) - The application
2. **run-windows.bat** - Double-click launcher

## Prerequisites on Windows

You need **Java 17 or higher** installed.

### Check if Java is installed:

1. Open Command Prompt
2. Run: `java -version`
3. Should show version 17 or higher

### If Java is not installed:

Download from: https://adoptium.net/temurin/releases/?version=17

- Choose: Windows x64
- Download: .msi installer
- Install and restart

## How to Run

### Method 1: Double-click (Easiest)

1. Copy both files to Windows:
   - `device-bridge-1.0.0.jar`
   - `run-windows.bat`
2. Double-click `run-windows.bat`
3. Application will start!

### Method 2: Command Line

1. Open Command Prompt
2. Navigate to folder with JAR:
   ```cmd
   cd path\to\folder
   ```
3. Run:
   ```cmd
   java -jar device-bridge-1.0.0.jar
   ```

## What to Test

1. **Login Screen**
   - Server URL: https://scrapapi.accomation.io
   - Email: tenant4@yopmail.com
   - Password: (your password)

2. **Device Selection**
   - Select weighbridges
   - Select cameras
   - Click "Start Monitoring"

3. **Monitoring Screen**
   - Toggle Debug Mode ON/OFF
   - Check weighbridge readings
   - Check camera snapshots
   - Verify socket connection

## Troubleshooting

### "Java not found"

- Install Java 17+ from https://adoptium.net/
- Restart Command Prompt after installation

### "Could not find or load main class"

- Make sure JAR file is not corrupted
- Re-download the JAR file

### Application doesn't start

- Check if port 8080 is available
- Check internet connection
- Check firewall settings

## File Locations

The app stores config at:

```
C:\Users\YourName\AppData\Roaming\ScrapMS\DeviceBridge\config.json
```

## Next Steps

After testing, you can:

1. Create EXE installer using `build-exe.bat` on Windows
2. Or distribute JAR + BAT file to users
