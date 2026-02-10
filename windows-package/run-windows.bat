@echo off
REM Launcher for Scrap MS Device Bridge
REM Double-click this file to run the application

echo Starting Scrap MS Device Bridge...
java -jar device-bridge-1.0.0.jar

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Failed to start application!
    echo.
    echo Please make sure Java 17 or higher is installed.
    echo Download from: https://adoptium.net/
    echo.
    pause
)
