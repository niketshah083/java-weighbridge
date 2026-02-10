# Optimized Build Guide

## JAR Size Comparison

| Version                       | Size      | Description                  |
| ----------------------------- | --------- | ---------------------------- |
| Original (javacv-platform)    | ~700 MB   | All platforms, all libraries |
| Optimized (platform-specific) | ~40-42 MB | Single platform, FFmpeg only |
| Lightweight (HTTP only)       | ~5 MB     | No RTSP, HTTP snapshots only |

## Build Commands

### For Windows (Production)

```bash
mvn clean package -DskipTests -P windows-x64
```

Output: `target/device-bridge-1.0.0.jar` (~42 MB)

### For Linux

```bash
mvn clean package -DskipTests -P linux-x64
```

Output: `target/device-bridge-1.0.0.jar` (~40 MB)

### For macOS

```bash
mvn clean package -DskipTests -P macos-x64
```

## What Was Optimized

1. **Platform-specific natives**: Instead of bundling FFmpeg for ALL platforms (Windows, Linux, macOS, Android, iOS), we now only include the target platform.

2. **Removed unused libraries**: Excluded OpenCV, Tesseract, OpenBLAS, and other JavaCV modules not needed for RTSP streaming.

3. **Dependency cleanup**: Removed transitive dependencies that were pulling in unnecessary code.

## If You Need Even Smaller (~5 MB)

If your cameras support HTTP snapshot URLs (most IP cameras do), you can use the lightweight version:

```bash
mvn clean package -DskipTests -f pom-lightweight.xml
```

This removes FFmpeg entirely and uses HTTP polling for camera preview.

**Note**: Lightweight mode does NOT support RTSP streaming. Your camera config must use HTTP URLs like:

- `http://camera-ip/snapshot.jpg`
- `http://camera-ip/cgi-bin/snapshot.cgi`

## Running the Application

### Development (with Maven)

```bash
# Linux
mvn javafx:run -P linux-x64

# Windows
mvn javafx:run -P windows-x64
```

### Production (JAR file)

```bash
java -jar target/device-bridge-1.0.0.jar
```

## Why FFmpeg is Still ~40 MB

FFmpeg native libraries are inherently large because they include:

- Video codecs (H.264, H.265, VP8, VP9, etc.)
- Audio codecs
- Container formats
- Network protocols (RTSP, RTMP, etc.)

This is the minimum size needed for RTSP streaming support.
