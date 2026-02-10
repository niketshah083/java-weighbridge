@echo off
REM Setup and Build Script for Windows
REM This script will guide you through the setup process

echo ========================================
echo Scrap MS Device Bridge - Windows Setup
echo ========================================
echo.

REM Check Java
echo [1/3] Checking Java installation...
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java not found!
    echo.
    echo Please install JDK 17 or higher from:
    echo https://adoptium.net/temurin/releases/?version=17
    echo.
    echo After installation, restart this script.
    pause
    exit /b 1
)
echo Java found!
java -version
echo.

REM Check Maven
echo [2/3] Checking Maven installation...
mvn -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven not found!
    echo.
    echo Please install Maven from:
    echo https://maven.apache.org/download.cgi
    echo.
    echo Installation steps:
    echo 1. Download Binary zip archive
    echo 2. Extract to C:\Program Files\Maven
    echo 3. Add C:\Program Files\Maven\bin to PATH
    echo 4. Restart Command Prompt
    echo.
    pause
    exit /b 1
)
echo Maven found!
mvn -version
echo.

REM Build project
echo [3/3] Building project...
echo This will take 2-5 minutes on first run...
echo.
call mvn clean package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Build failed!
    echo.
    echo Common issues:
    echo - No internet connection (Maven needs to download dependencies)
    echo - Firewall blocking Maven
    echo - Insufficient disk space
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.
echo JAR file created at: target\device-bridge-1.0.0.jar
echo.
echo What's next?
echo.
echo 1. Run the application:
echo    mvn javafx:run
echo.
echo 2. Or run JAR directly:
echo    java -jar target\device-bridge-1.0.0.jar
echo.
echo 3. Create Windows installer:
echo    build-exe.bat
echo.
pause
