# How to Create EXE for Java Bridge App

## Prerequisites

### On Windows:

1. **JDK 17 or higher** (includes jpackage)
2. **WiX Toolset 3.11+** (for creating .msi installer)
   - Download from: https://wixtoolset.org/releases/
   - Add to PATH: `C:\Program Files (x86)\WiX Toolset v3.11\bin`
3. **Maven** (already installed)

### On Linux (for testing):

1. JDK 17+ (already have)
2. Maven (already have)

---

## Method 1: Using jpackage (Recommended)

### Step 1: Build the JAR

```bash
cd java-bridge-app
mvn clean package
```

This creates: `target/device-bridge-1.0.0.jar`

### Step 2: Create EXE (On Windows)

#### Option A: Simple EXE (no installer)

```bash
jpackage --input target ^
  --name "ScrapMS-DeviceBridge" ^
  --main-jar device-bridge-1.0.0.jar ^
  --main-class com.scrapms.devicebridge.DeviceBridgeApplication ^
  --type app-image ^
  --dest target/dist ^
  --app-version 1.0.0 ^
  --vendor "Scrap MS" ^
  --icon src/main/resources/icon.ico ^
  --java-options "-Dfile.encoding=UTF-8"
```

#### Option B: Windows Installer (.exe installer)

```bash
jpackage --input target ^
  --name "ScrapMS-DeviceBridge" ^
  --main-jar device-bridge-1.0.0.jar ^
  --main-class com.scrapms.devicebridge.DeviceBridgeApplication ^
  --type exe ^
  --dest target/dist ^
  --app-version 1.0.0 ^
  --vendor "Scrap MS" ^
  --icon src/main/resources/icon.ico ^
  --win-menu ^
  --win-dir-chooser ^
  --win-shortcut ^
  --java-options "-Dfile.encoding=UTF-8"
```

#### Option C: MSI Installer (requires WiX)

```bash
jpackage --input target ^
  --name "ScrapMS-DeviceBridge" ^
  --main-jar device-bridge-1.0.0.jar ^
  --main-class com.scrapms.devicebridge.DeviceBridgeApplication ^
  --type msi ^
  --dest target/dist ^
  --app-version 1.0.0 ^
  --vendor "Scrap MS" ^
  --icon src/main/resources/icon.ico ^
  --win-menu ^
  --win-dir-chooser ^
  --win-shortcut ^
  --java-options "-Dfile.encoding=UTF-8"
```

### Output:

- **app-image**: Creates folder with EXE + runtime (portable)
- **exe**: Creates installer that installs the app
- **msi**: Creates MSI installer (more professional)

---

## Method 2: Using Maven Plugin (Automated)

I've already configured the `jpackage-maven-plugin` in `pom.xml`.

### On Windows, run:

```bash
# First, create a custom JRE runtime
jlink --add-modules java.base,java.desktop,java.logging,java.net.http,java.sql,jdk.unsupported ^
  --output target/java-runtime ^
  --strip-debug ^
  --no-header-files ^
  --no-man-pages ^
  --compress=2

# Then build the package
mvn clean package
mvn jpackage:jpackage
```

This will create the installer in `target/dist/`

---

## Method 3: Using Launch4j (Alternative)

Launch4j wraps JAR into EXE without bundling JRE.

### Steps:

1. Download Launch4j: http://launch4j.sourceforge.net/
2. Build JAR: `mvn clean package`
3. Open Launch4j GUI
4. Configure:
   - **Output file**: `ScrapMS-DeviceBridge.exe`
   - **Jar**: `target/device-bridge-1.0.0.jar`
   - **Main class**: `com.scrapms.devicebridge.DeviceBridgeApplication`
   - **Min JRE version**: 17
   - **Icon**: (optional)
5. Click "Build wrapper"

**Note**: Users need Java 17+ installed.

---

## Method 4: Using jpackage with Maven Shade Plugin

### Step 1: Build fat JAR

```bash
mvn clean package
```

This creates: `target/device-bridge-1.0.0.jar` (with all dependencies)

### Step 2: Create EXE

```bash
jpackage --input target ^
  --name "ScrapMS-DeviceBridge" ^
  --main-jar device-bridge-1.0.0.jar ^
  --type exe ^
  --dest target/dist ^
  --app-version 1.0.0 ^
  --vendor "Scrap MS" ^
  --win-menu ^
  --win-dir-chooser ^
  --win-shortcut
```

---

## Quick Start (Recommended for You)

### On Windows:

1. **Install WiX Toolset** (if you want MSI):

   ```
   Download from: https://wixtoolset.org/releases/
   Install and add to PATH
   ```

2. **Build and Package**:

   ```bash
   cd java-bridge-app
   mvn clean package

   jpackage --input target ^
     --name "ScrapMS-DeviceBridge" ^
     --main-jar device-bridge-1.0.0.jar ^
     --main-class com.scrapms.devicebridge.DeviceBridgeApplication ^
     --type exe ^
     --dest target/dist ^
     --app-version 1.0.0 ^
     --vendor "Scrap MS" ^
     --win-menu ^
     --win-dir-chooser ^
     --win-shortcut
   ```

3. **Find your installer**:
   ```
   target/dist/ScrapMS-DeviceBridge-1.0.0.exe
   ```

---

## Testing on Linux (Current System)

You can test the packaging process on Linux:

```bash
cd java-bridge-app
mvn clean package

# Create Linux package (for testing)
jpackage --input target \
  --name "ScrapMS-DeviceBridge" \
  --main-jar device-bridge-1.0.0.jar \
  --main-class com.scrapms.devicebridge.DeviceBridgeApplication \
  --type deb \
  --dest target/dist \
  --app-version 1.0.0 \
  --vendor "Scrap MS" \
  --linux-shortcut
```

This creates: `target/dist/scrapms-devicebridge_1.0.0_amd64.deb`

---

## Comparison

| Method                   | Pros                                        | Cons                    | Size   |
| ------------------------ | ------------------------------------------- | ----------------------- | ------ |
| **jpackage (exe)**       | Native installer, bundles JRE, professional | Requires WiX for MSI    | ~150MB |
| **jpackage (app-image)** | Portable, no install needed                 | Large folder            | ~150MB |
| **Launch4j**             | Small size, simple                          | Requires Java installed | ~50MB  |
| **Maven plugin**         | Automated, CI/CD friendly                   | Complex setup           | ~150MB |

---

## Recommended Approach

**For production deployment:**

1. Use **jpackage with exe type** (creates installer)
2. Bundles JRE (users don't need Java installed)
3. Creates Start Menu shortcuts
4. Professional installation experience

**Command:**

```bash
mvn clean package
jpackage --input target --name "ScrapMS-DeviceBridge" --main-jar device-bridge-1.0.0.jar --main-class com.scrapms.devicebridge.DeviceBridgeApplication --type exe --dest target/dist --app-version 1.0.0 --vendor "Scrap MS" --win-menu --win-dir-chooser --win-shortcut
```

---

## Troubleshooting

### "jpackage: command not found"

- Make sure you have JDK 17+ (not just JRE)
- Check: `java -version` should show JDK
- Add to PATH: `C:\Program Files\Java\jdk-17\bin`

### "WiX Toolset not found"

- Only needed for MSI installers
- Use `--type exe` instead of `--type msi`
- Or install WiX from: https://wixtoolset.org/

### Large file size

- Normal! Includes JRE (~150MB)
- Users don't need Java installed
- Can use jlink to create smaller custom JRE

---

## Next Steps

1. Build on Windows machine
2. Test the installer
3. Distribute to users
4. Users just double-click the installer!
