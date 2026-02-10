# Quick Start - Build EXE on Windows

## What You Need

1. **JDK 17 or higher** (not JRE - must be JDK)
2. **Maven** (already installed)
3. **Windows 10/11**

## Check Your Setup

Open Command Prompt and run:

```cmd
java -version
```

Should show: `java version "17"` or higher

```cmd
mvn -version
```

Should show Maven version

```cmd
jpackage --version
```

Should show jpackage version (comes with JDK 17+)

## Build the EXE

### Option 1: Using the Script (Easiest)

1. Open Command Prompt
2. Navigate to the project:
   ```cmd
   cd java-bridge-app
   ```
3. Run the build script:
   ```cmd
   build-exe.bat
   ```
4. Wait for it to complete (takes 2-3 minutes)
5. Find your installer at: `target\dist\ScrapMS-DeviceBridge-1.0.0.exe`

### Option 2: Manual Steps

1. Build the JAR:

   ```cmd
   cd java-bridge-app
   mvn clean package -DskipTests
   ```

2. Create the EXE:
   ```cmd
   jpackage --input target --name "ScrapMS-DeviceBridge" --main-jar device-bridge-1.0.0.jar --main-class com.scrapms.devicebridge.DeviceBridgeApplication --type exe --dest target/dist --app-version 1.0.0 --vendor "Scrap MS" --win-menu --win-dir-chooser --win-shortcut --java-options "-Dfile.encoding=UTF-8"
   ```

## What You Get

The installer (`ScrapMS-DeviceBridge-1.0.0.exe`) will:

- ✅ Install the application
- ✅ Bundle Java runtime (users don't need Java)
- ✅ Create Start Menu shortcut
- ✅ Create Desktop shortcut
- ✅ Add to Programs & Features (uninstall support)

## File Size

- Installer: ~150-200 MB (includes Java runtime)
- Installed app: ~200-250 MB

## Distribution

Just share the `ScrapMS-DeviceBridge-1.0.0.exe` file with users!

## Troubleshooting

### "jpackage: command not found"

- You need JDK 17+ (not JRE)
- Download from: https://adoptium.net/
- Make sure `JAVA_HOME` is set and `%JAVA_HOME%\bin` is in PATH

### "Maven not found"

- Maven should already be installed
- If not, download from: https://maven.apache.org/download.cgi

### Build takes too long

- First build downloads dependencies (~500MB)
- Subsequent builds are faster
- Be patient, it's worth it!

## Alternative: Portable Version

If you want a portable version (no installer):

```cmd
jpackage --input target --name "ScrapMS-DeviceBridge" --main-jar device-bridge-1.0.0.jar --main-class com.scrapms.devicebridge.DeviceBridgeApplication --type app-image --dest target/dist --app-version 1.0.0 --vendor "Scrap MS"
```

This creates a folder with the EXE that you can zip and distribute.
