# Windows Deployment Guide

## ✅ JAR File Ready!

The application has been packaged as a runnable JAR file that works on Windows (and any OS with Java).

## Files to Copy to Windows

1. **`target/device-bridge-1.0.0.jar`** - The application (725 MB)
2. **`run-windows.bat`** - Batch file for easy launching

## Installation Steps on Windows

### Step 1: Install Java (if not already installed)

1. Download Java 17 or higher from: https://adoptium.net/
2. Choose "Windows x64" installer
3. Run the installer
4. Verify installation by opening Command Prompt and typing:
   ```
   java -version
   ```
   You should see something like: `openjdk version "17.0.x" or "21.0.x"`

### Step 2: Copy Files

1. Create a folder on Windows, e.g., `C:\ScrapMS-DeviceBridge\`
2. Copy these files into that folder:
   - `device-bridge-1.0.0.jar`
   - `run-windows.bat`

### Step 3: Run the Application

**Option A: Using Batch File (Easiest)**

- Double-click `run-windows.bat`
- The application will start

**Option B: Using Command Prompt**

1. Open Command Prompt
2. Navigate to the folder:
   ```
   cd C:\ScrapMS-DeviceBridge
   ```
3. Run:
   ```
   java -jar device-bridge-1.0.0.jar
   ```

**Option C: Double-click JAR (if Java is associated)**

- Just double-click `device-bridge-1.0.0.jar`
- If this doesn't work, use Option A or B

## What's Included in the JAR?

The JAR file is self-contained and includes:

- ✅ All Java dependencies (OkHttp, Jackson, Socket.IO, Gson)
- ✅ JavaFX UI libraries (for all platforms)
- ✅ Camera/video processing (JavaCV, FFmpeg, OpenCV)
- ✅ Serial port communication (jSerialComm)
- ✅ All native libraries for Windows

**No additional installation needed** - just Java!

## Troubleshooting

### "Java is not recognized as an internal or external command"

- Java is not installed or not in PATH
- Install Java from https://adoptium.net/
- Make sure to check "Add to PATH" during installation

### Application doesn't start

- Make sure you have Java 17 or higher
- Check if antivirus is blocking the JAR file
- Try running from Command Prompt to see error messages

### "Could not find or load main class"

- The JAR file might be corrupted
- Re-copy the JAR file from Linux to Windows
- Make sure the file size is correct (~725 MB)

## Creating a True Windows EXE

If you need a native Windows EXE installer (not just a JAR), you have two options:

### Option 1: Build on Windows Machine

1. Install JDK 17+ on Windows
2. Install Maven on Windows
3. Copy the entire `java-bridge-app` folder to Windows
4. Open Command Prompt in that folder
5. Run:
   ```
   mvn clean package
   jpackage --input target --name "ScrapMS-DeviceBridge" --main-jar device-bridge-1.0.0.jar --main-class com.scrapms.devicebridge.DeviceBridgeApplication --type exe --dest target/dist --app-version 1.0.0 --vendor "Scrap MS" --win-dir-chooser --win-menu --win-shortcut
   ```
6. Find the EXE in `target/dist/`

### Option 2: Use GitHub Actions (Automated)

Set up GitHub Actions to automatically build Windows EXE on every commit. See `BUILD_EXE.md` for details.

## File Transfer Methods

### From Linux to Windows:

1. **USB Drive**: Copy files to USB, then to Windows
2. **Network Share**: Use Samba/SMB to share folder
3. **Cloud Storage**: Upload to Google Drive/Dropbox, download on Windows
4. **SCP/SFTP**: Use WinSCP or FileZilla to transfer files
5. **Email**: If file size allows (JAR is 725 MB, might be too large)

## Performance Notes

- First startup might be slow (10-20 seconds) due to large JAR size
- Subsequent startups will be faster
- The app uses ~500 MB RAM when running
- Camera streaming requires good CPU (for video processing)

## Security Notes

- Windows Defender might scan the JAR on first run (this is normal)
- Some antivirus software might flag the JAR (false positive)
- If blocked, add an exception for the JAR file

## Next Steps

Once the app is running on Windows:

1. Login with your credentials
2. Select weighbridges and cameras
3. Start monitoring
4. Toggle debug mode ON/OFF as needed

## Support

If you encounter issues:

1. Check Java version: `java -version`
2. Run from Command Prompt to see error messages
3. Check logs in: `%APPDATA%\ScrapMS\DeviceBridge\`
4. Make sure serial ports and cameras are accessible
