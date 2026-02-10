# Building Windows EXE for Java Bridge App

## Prerequisites

- JDK 17 or higher (you have Java 21.0.9 ✅)
- Maven 3.8+ (you have 3.8.7 ✅)
- WiX Toolset 3.11+ (for Windows installer - optional)

## Option 1: Using jpackage (Recommended)

### Step 1: Build the JAR

```bash
cd java-bridge-app
mvn clean package
```

### Step 2: Create Windows EXE using jpackage

```bash
jpackage \
  --input target \
  --name "ScrapMS-DeviceBridge" \
  --main-jar device-bridge-1.0.0.jar \
  --main-class com.scrapms.devicebridge.DeviceBridgeApplication \
  --type exe \
  --dest target/dist \
  --app-version 1.0.0 \
  --vendor "Scrap MS" \
  --description "Device Bridge for Weighbridge and Camera Integration" \
  --win-dir-chooser \
  --win-menu \
  --win-shortcut \
  --java-options "-Dfile.encoding=UTF-8"
```

### Step 3: Find your EXE

The installer will be at: `target/dist/ScrapMS-DeviceBridge-1.0.0.exe`

## Option 2: Using Maven Plugin (Automated)

I've already configured the `jpackage-maven-plugin` in `pom.xml`. Just run:

```bash
mvn clean package jpackage:jpackage
```

The EXE will be created in `target/dist/`

## Option 3: Using Launch4j (Alternative)

If you want a simple EXE wrapper without installer:

1. Download Launch4j from http://launch4j.sourceforge.net/
2. Build the JAR: `mvn clean package`
3. Use Launch4j GUI to wrap the JAR into EXE

## Option 4: Cross-Platform Build (From Linux)

Since you're on Linux, you can't directly create Windows EXE. You need to:

### A. Use GitHub Actions (Recommended for CI/CD)

Create `.github/workflows/build-windows.yml`:

```yaml
name: Build Windows EXE

on:
  push:
    branches: [main]
  workflow_dispatch:

jobs:
  build:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: "17"
          distribution: "temurin"
      - name: Build with Maven
        run: mvn clean package
      - name: Create Windows EXE
        run: |
          jpackage --input target --name "ScrapMS-DeviceBridge" --main-jar device-bridge-1.0.0.jar --main-class com.scrapms.devicebridge.DeviceBridgeApplication --type exe --dest target/dist --app-version 1.0.0 --vendor "Scrap MS" --win-dir-chooser --win-menu --win-shortcut
      - uses: actions/upload-artifact@v3
        with:
          name: windows-installer
          path: target/dist/*.exe
```

### B. Use Docker with Wine (Complex)

Not recommended for JavaFX apps.

### C. Build on Windows Machine

Transfer the project to a Windows machine and run the jpackage command there.

## Recommended Approach for You

Since you're on Linux, here are your best options:

### 1. **Create a Fat JAR (Works on any OS with Java)**

```bash
cd java-bridge-app
mvn clean package
```

This creates `target/device-bridge-1.0.0.jar` that can run on Windows with:

```bash
java -jar device-bridge-1.0.0.jar
```

### 2. **Use GitHub Actions** (if you have a GitHub repo)

Push your code and let GitHub build the Windows EXE automatically.

### 3. **Build on Windows**

Copy the project to a Windows machine and run:

```bash
cd java-bridge-app
mvn clean package
jpackage --input target --name "ScrapMS-DeviceBridge" --main-jar device-bridge-1.0.0.jar --main-class com.scrapms.devicebridge.DeviceBridgeApplication --type exe --dest target/dist --app-version 1.0.0 --vendor "Scrap MS" --win-dir-chooser --win-menu --win-shortcut
```

## Quick Start: Create Runnable JAR Now

Let me create a runnable JAR that works on Windows:

```bash
cd java-bridge-app
mvn clean package
```

Then copy `target/device-bridge-1.0.0.jar` to Windows and run:

```bash
java -jar device-bridge-1.0.0.jar
```

Or double-click if Java is associated with JAR files.

## Creating a Simple Batch File for Windows

Create `run.bat` to make it easier for Windows users:

```batch
@echo off
java -jar device-bridge-1.0.0.jar
pause
```

## Notes

- The JAR file includes all dependencies (thanks to maven-shade-plugin)
- JavaFX modules are bundled
- No installation needed - just run the JAR
- For true EXE, you need to build on Windows or use CI/CD
