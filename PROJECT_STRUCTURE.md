# Java Bridge App - Project Structure

## Overview

This is a Java/JavaFX port of the Electron Device Bridge application. It provides the same functionality for connecting weighbridges and cameras to the Scrap MS backend system.

## Created Files

### 1. Maven Configuration

- **pom.xml**: Maven project configuration with all dependencies

### 2. Main Application

- **DeviceBridgeApplication.java**: Main JavaFX application entry point

### 3. Model Classes

- **WeighbridgeMaster.java**: Weighbridge device model
- **CameraMaster.java**: Camera device model
- **WeighbridgeConfig.java**: Weighbridge serial port configuration
- **CameraConfig.java**: Camera RTSP/HTTP configuration

### 4. Documentation

- **README.md**: Complete project documentation
- **PROJECT_STRUCTURE.md**: This file

## Next Steps to Complete the Project

### 1. Service Layer (to be created)

```
src/main/java/com/scrapms/devicebridge/service/
├── AuthService.java           # Handle login/logout, token management
├── ConfigService.java         # Store/retrieve app configuration
├── SocketService.java         # WebSocket connection to backend
├── SerialPortService.java     # Read weight from serial port
└── CameraService.java         # Capture snapshots from cameras
```

### 2. Controller Layer (to be created)

```
src/main/java/com/scrapms/devicebridge/controller/
├── LoginController.java       # Login screen
├── DeviceSelectionController.java  # Select weighbridges/cameras
└── MonitoringController.java  # Main monitoring screen
```

### 3. FXML Views (to be created)

```
src/main/resources/fxml/
├── login.fxml                 # Login screen layout
├── device-selection.fxml      # Device selection layout
└── monitoring.fxml            # Monitoring screen layout
```

### 4. CSS Styles (to be created)

```
src/main/resources/css/
└── styles.css                 # Application styles
```

## Key Dependencies

1. **JavaFX 21**: UI framework
2. **OkHttp 4.12**: HTTP client
3. **Socket.IO Client 2.1**: WebSocket communication
4. **Jackson 2.16**: JSON processing
5. **jSerialComm 2.10**: Serial port communication
6. **JavaCV 1.5.10**: Camera streaming (includes FFmpeg)

## Building & Running

### Development

```bash
mvn javafx:run
```

### Create JAR

```bash
mvn clean package
```

### Create Windows EXE

```bash
mvn jpackage:jpackage
```

## Architecture Comparison

### Electron App

- **Language**: TypeScript/JavaScript
- **UI**: HTML/CSS
- **Runtime**: Node.js + Chromium
- **Size**: ~150MB
- **Startup**: 2-3 seconds

### Java App

- **Language**: Java 17
- **UI**: JavaFX
- **Runtime**: JVM
- **Size**: ~80MB (with JRE)
- **Startup**: 1-2 seconds

## Features Implemented

✅ Project structure
✅ Maven configuration
✅ Model classes
✅ Main application class
✅ Documentation

## Features To Implement

⏳ Service layer (Auth, Socket, Serial, Camera)
⏳ Controller layer (Login, Selection, Monitoring)
⏳ FXML views
⏳ CSS styling
⏳ Debug mode toggle
⏳ Config modal
⏳ Error handling
⏳ Logging configuration

## Estimated Completion Time

- Service Layer: 4-6 hours
- Controller Layer: 3-4 hours
- FXML Views: 2-3 hours
- CSS Styling: 1-2 hours
- Testing & Bug Fixes: 2-3 hours

**Total**: 12-18 hours of development

## Notes

- The Java version will have better performance and lower memory usage
- Serial port communication is more reliable in Java
- JavaFX provides native look and feel on each platform
- Easier to debug and maintain than Electron
- Better integration with Java backend ecosystem
