# Scrap MS Device Bridge - Java Version

A JavaFX desktop application for integrating weighbridge and camera devices with the Scrap MS backend system.

## Features

- **Weighbridge Integration**: Connect to weighbridge devices via serial port (RS232/USB)
- **Camera Integration**: Capture snapshots from IP cameras (RTSP/HTTP streams)
- **Real-time Communication**: WebSocket connection to backend for live data streaming
- **Debug Mode**: Test functionality without physical devices
- **Configuration Management**: View and manage device configurations
- **Cross-platform**: Runs on Windows, Linux, and macOS

## Requirements

- Java 17 or higher
- Maven 3.6 or higher
- JavaFX 21
- Serial port drivers (for weighbridge connection)
- FFmpeg (for camera streaming - included in dependencies)

## Project Structure

```
java-bridge-app/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/scrapms/devicebridge/
│   │   │       ├── DeviceBridgeApplication.java    # Main application
│   │   │       ├── controller/                      # JavaFX controllers
│   │   │       ├── model/                           # Data models
│   │   │       ├── service/                         # Business logic
│   │   │       │   ├── AuthService.java            # Authentication
│   │   │       │   ├── ConfigService.java          # Configuration management
│   │   │       │   ├── SocketService.java          # WebSocket communication
│   │   │       │   ├── SerialPortService.java      # Weighbridge serial port
│   │   │       │   └── CameraService.java          # Camera streaming
│   │   │       └── util/                            # Utility classes
│   │   └── resources/
│   │       ├── fxml/                                # JavaFX FXML files
│   │       ├── css/                                 # Stylesheets
│   │       └── images/                              # Icons and images
│   └── test/
│       └── java/                                    # Unit tests
├── pom.xml                                          # Maven configuration
└── README.md                                        # This file
```

## Building the Application

### Development Build

```bash
mvn clean compile
```

### Run Application

```bash
mvn javafx:run
```

### Create Executable JAR

```bash
mvn clean package
```

The executable JAR will be created in `target/device-bridge-1.0.0.jar`

### Create Native Installer (Windows EXE)

```bash
# First, create a custom Java runtime
jlink --add-modules java.base,java.desktop,javafx.controls,javafx.fxml,javafx.web \
      --output target/java-runtime

# Then create the installer
mvn jpackage:jpackage
```

The installer will be created in `target/dist/`

## Running the Application

### From JAR

```bash
java -jar target/device-bridge-1.0.0.jar
```

### From Maven

```bash
mvn javafx:run
```

## Configuration

The application stores configuration in:

- **Windows**: `%APPDATA%/ScrapMS/DeviceBridge/config.json`
- **Linux**: `~/.config/ScrapMS/DeviceBridge/config.json`
- **macOS**: `~/Library/Application Support/ScrapMS/DeviceBridge/config.json`

## Dependencies

- **JavaFX**: UI framework
- **OkHttp**: HTTP client for API calls
- **Socket.IO Client**: WebSocket communication
- **Jackson**: JSON processing
- **jSerialComm**: Serial port communication
- **JavaCV**: Camera streaming (includes FFmpeg)
- **SLF4J + Logback**: Logging

## Features Comparison with Electron Version

| Feature          | Electron | Java   |
| ---------------- | -------- | ------ |
| Cross-platform   | ✅       | ✅     |
| Serial Port      | ✅       | ✅     |
| RTSP Cameras     | ✅       | ✅     |
| HTTP Cameras     | ✅       | ✅     |
| WebSocket        | ✅       | ✅     |
| Debug Mode       | ✅       | ✅     |
| Native Installer | ✅       | ✅     |
| Memory Usage     | Higher   | Lower  |
| Startup Time     | Slower   | Faster |

## Development

### Adding New Features

1. Create model classes in `model/` package
2. Create service classes in `service/` package
3. Create FXML views in `resources/fxml/`
4. Create controllers in `controller/` package
5. Update `DeviceBridgeApplication.java` if needed

### Testing

```bash
mvn test
```

## Troubleshooting

### Serial Port Not Found

- Ensure serial port drivers are installed
- Check port permissions on Linux: `sudo usermod -a -G dialout $USER`
- Verify port name in configuration

### Camera Connection Failed

- Check network connectivity to camera
- Verify RTSP/HTTP URL is correct
- Ensure FFmpeg is properly installed
- Check firewall settings

### Application Won't Start

- Verify Java 17+ is installed: `java -version`
- Check JavaFX is available
- Review logs in application data directory

## License

Proprietary - Scrap MS

## Support

For support, contact: support@scrapms.com
