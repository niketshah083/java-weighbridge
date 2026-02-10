========================================
Scrap MS Device Bridge - Windows Setup
========================================

You copied the source code without the target folder.
That's fine! Just build it on Windows.

STEP 1: Install Prerequisites
------------------------------
1. Install JDK 17 or higher
   Download: https://adoptium.net/temurin/releases/?version=17
   Choose: Windows x64 .msi installer

2. Install Maven
   Download: https://maven.apache.org/download.cgi
   Choose: Binary zip archive
   Extract to: C:\Program Files\Maven
   Add to PATH: C:\Program Files\Maven\bin

STEP 2: Build the Project
--------------------------
Open Command Prompt in the java-bridge-app folder:

Option A - Automated (Recommended):
   Double-click: SETUP_WINDOWS.bat

Option B - Manual:
   mvn clean package -DskipTests

This will:
- Download dependencies (~500MB)
- Build the JAR file
- Takes 2-5 minutes first time

STEP 3: Run the Application
----------------------------
After build completes:

   mvn javafx:run

Or:
   java -jar target\device-bridge-1.0.0.jar

STEP 4: Create EXE (Optional)
------------------------------
To create Windows installer:

   build-exe.bat

This creates: target\dist\ScrapMS-DeviceBridge-1.0.0.exe

========================================

Quick Commands:
- Build: mvn clean package -DskipTests
- Run: mvn javafx:run
- Create EXE: build-exe.bat

Need help? Read BUILD_ON_WINDOWS.md

========================================
