@echo off
REM Build EXE for Scrap MS Device Bridge
REM Run this script on Windows

echo ========================================
echo Scrap MS Device Bridge - Build EXE
echo ========================================
echo.

REM Step 1: Build JAR
echo [1/2] Building JAR with Maven...
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven build failed!
    pause
    exit /b 1
)
echo JAR built successfully!
echo.

REM Step 2: Create EXE with jpackage
echo [2/2] Creating Windows installer with jpackage...
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
  --win-shortcut ^
  --java-options "-Dfile.encoding=UTF-8"

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: jpackage failed!
    echo.
    echo Make sure you have:
    echo - JDK 17 or higher installed
    echo - jpackage in your PATH
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo Installer created at: target\dist\ScrapMS-DeviceBridge-1.0.0.exe
echo.
echo You can now distribute this installer to users.
echo Users just need to double-click the installer - no Java required!
echo.
pause
