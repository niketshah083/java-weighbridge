# Build and Run on Windows

## What You Copied

You copied the source code (without target folder) - that's perfect!

## Prerequisites on Windows

1. **JDK 17 or higher**
   - Download from: https://adoptium.net/temurin/releases/?version=17
   - Choose: Windows x64 .msi installer
   - Install and restart

2. **Maven**
   - Download from: https://maven.apache.org/download.cgi
   - Choose: Binary zip archive (apache-maven-3.x.x-bin.zip)
   - Extract to: `C:\Program Files\Maven`
   - Add to PATH:
     - Right-click "This PC" → Properties → Advanced System Settings
     - Environment Variables → System Variables → Path → Edit
     - Add: `C:\Program Files\Maven\bin`
   - Restart Command Prompt

## Verify Installation

Open Command Prompt and check:

```cmd
java -version
```

Should show: `openjdk version "17"` or higher

```cmd
mvn -version
```

Should show: `Apache Maven 3.x.x`

## Build the Project

1. Open Command Prompt
2. Navigate to project folder:

   ```cmd
   cd path\to\java-bridge-app
   ```

3. Build the JAR:

   ```cmd
   mvn clean package -DskipTests
   ```

   This will:
   - Download all dependencies (~500MB first time)
   - Compile the code
   - Create JAR in `target\device-bridge-1.0.0.jar`
   - Takes 2-5 minutes

## Run the Application

After build completes:

```cmd
mvn javafx:run
```

Or run the JAR directly:

```cmd
java -jar target\device-bridge-1.0.0.jar
```

## Create Windows EXE

After building, create installer:

```cmd
build-exe.bat
```

This creates: `target\dist\ScrapMS-DeviceBridge-1.0.0.exe`

## Troubleshooting

### "mvn: command not found"

- Maven not in PATH
- Restart Command Prompt after adding to PATH
- Or use full path: `C:\Program Files\Maven\bin\mvn`

### "JAVA_HOME not set"

- Set JAVA_HOME environment variable:
  - Variable: `JAVA_HOME`
  - Value: `C:\Program Files\Eclipse Adoptium\jdk-17.x.x`
- Restart Command Prompt

### Build fails with "Cannot resolve dependencies"

- Check internet connection
- Maven needs to download dependencies
- Try again: `mvn clean package -DskipTests -U`

### Out of memory during build

- Increase Maven memory:
  ```cmd
  set MAVEN_OPTS=-Xmx2048m
  mvn clean package -DskipTests
  ```

## Quick Commands Summary

```cmd
REM Navigate to project
cd java-bridge-app

REM Build JAR
mvn clean package -DskipTests

REM Run application
mvn javafx:run

REM Or run JAR directly
java -jar target\device-bridge-1.0.0.jar

REM Create EXE installer
build-exe.bat
```

## What Gets Built

- `target\device-bridge-1.0.0.jar` - Runnable JAR (725 MB)
- `target\dist\ScrapMS-DeviceBridge-1.0.0.exe` - Windows installer (if you run build-exe.bat)

## First Build Takes Time

- Downloads ~500MB of dependencies
- Compiles all code
- Packages everything
- Takes 2-5 minutes
- Subsequent builds are faster (30 seconds)

## After Building

You can:

1. Run directly: `mvn javafx:run`
2. Run JAR: `java -jar target\device-bridge-1.0.0.jar`
3. Create EXE: `build-exe.bat`
4. Distribute the EXE to users
