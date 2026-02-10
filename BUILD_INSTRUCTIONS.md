# Build Instructions for Java Bridge App

## Prerequisites

1. **Java Development Kit (JDK) 17 or higher**

   ```bash
   java -version
   ```

   Download from: https://adoptium.net/

2. **Apache Maven 3.6 or higher**

   ```bash
   mvn -version
   ```

   Download from: https://maven.apache.org/download.cgi

3. **JavaFX SDK** (included in dependencies, no separate install needed)

## Project Status

### ✅ Completed Components

1. **Project Structure**
   - Maven POM configuration
   - Package structure
   - Resource directories

2. **Model Classes**
   - WeighbridgeMaster
   - CameraMaster
   - WeighbridgeConfig
   - CameraConfig

3. **Service Layer**
   - ConfigService - Configuration management
   - AuthService - Authentication
   - ApiService - HTTP API calls
   - SerialPortService - Weighbridge serial communication
   - CameraService - Camera streaming
   - SocketService - WebSocket communication

4. **Main Application**
   - DeviceBridgeApplication - JavaFX entry point

### ⏳ Pending Components

1. **Controller Layer** (Need to create)
   - LoginController
   - DeviceSelectionController
   - MonitoringController

2. **FXML Views** (Need to create)
   - login.fxml
   - device-selection.fxml
   - monitoring.fxml

3. **CSS Styles** (Need to create)
   - styles.css

4. **Resources** (Need to create)
   - Application icon
   - Images

## Build Commands

### 1. Clean and Compile

```bash
cd java-bridge-app
mvn clean compile
```

### 2. Run Application (Development)

```bash
mvn javafx:run
```

### 3. Create Executable JAR

```bash
mvn clean package
```

Output: `target/device-bridge-1.0.0.jar`

Run the JAR:

```bash
java -jar target/device-bridge-1.0.0.jar
```

### 4. Create Windows Installer

#### Step 1: Create Custom Java Runtime

```bash
jlink --add-modules java.base,java.desktop,java.logging,java.sql,java.naming,java.management,javafx.controls,javafx.fxml,javafx.web,javafx.media --output target/java-runtime
```

#### Step 2: Create Installer

```bash
mvn jpackage:jpackage
```

Output: `target/dist/ScrapMS-DeviceBridge-1.0.0.exe`

## Development Workflow

### 1. Install Dependencies

```bash
mvn clean install
```

### 2. Run Tests

```bash
mvn test
```

### 3. Check for Updates

```bash
mvn versions:display-dependency-updates
```

### 4. Generate Project Reports

```bash
mvn site
```

## IDE Setup

### IntelliJ IDEA

1. Open IntelliJ IDEA
2. File → Open → Select `java-bridge-app` folder
3. Wait for Maven to import dependencies
4. Right-click on `DeviceBridgeApplication.java` → Run

### Eclipse

1. Open Eclipse
2. File → Import → Maven → Existing Maven Projects
3. Select `java-bridge-app` folder
4. Right-click on project → Run As → Java Application

### VS Code

1. Install Java Extension Pack
2. Open `java-bridge-app` folder
3. Press F5 to run

## Troubleshooting

### Issue: JavaFX not found

**Solution**: Ensure JavaFX dependencies are in pom.xml and run:

```bash
mvn clean install
```

### Issue: Serial port access denied (Linux)

**Solution**: Add user to dialout group:

```bash
sudo usermod -a -G dialout $USER
```

Then logout and login again.

### Issue: FFmpeg not found

**Solution**: FFmpeg is included in JavaCV dependencies. If issues persist:

- Windows: Download from https://ffmpeg.org/download.html
- Linux: `sudo apt-get install ffmpeg`
- macOS: `brew install ffmpeg`

### Issue: Build fails with "package does not exist"

**Solution**: Clean and rebuild:

```bash
mvn clean install -U
```

## Next Steps

To complete the application, you need to:

1. **Create Controllers** (3-4 hours)
   - Implement LoginController
   - Implement DeviceSelectionController
   - Implement MonitoringController

2. **Create FXML Views** (2-3 hours)
   - Design login.fxml
   - Design device-selection.fxml
   - Design monitoring.fxml

3. **Create CSS Styles** (1-2 hours)
   - Create styles.css with modern UI

4. **Add Resources** (30 minutes)
   - Add application icon
   - Add placeholder images

5. **Testing** (2-3 hours)
   - Test serial port communication
   - Test camera streaming
   - Test WebSocket connection
   - Test on Windows

## Estimated Total Time

- Controllers: 3-4 hours
- FXML Views: 2-3 hours
- CSS Styling: 1-2 hours
- Resources: 30 minutes
- Testing: 2-3 hours

**Total: 9-13 hours**

## Performance Comparison

| Metric       | Electron   | Java      |
| ------------ | ---------- | --------- |
| Startup Time | 2-3 sec    | 1-2 sec   |
| Memory Usage | 150-200 MB | 80-120 MB |
| Package Size | ~150 MB    | ~80 MB    |
| CPU Usage    | Higher     | Lower     |
| Native Feel  | No         | Yes       |

## Support

For issues or questions:

- Check logs in: `~/.config/ScrapMS/DeviceBridge/` (Linux)
- Check logs in: `%APPDATA%/ScrapMS/DeviceBridge/` (Windows)
- Email: support@scrapms.com
