package com.scrapms.devicebridge;

import com.scrapms.devicebridge.service.AnprHttpListener;
import com.scrapms.devicebridge.service.CameraService;
import com.scrapms.devicebridge.service.OnvifEventPoller;
import com.scrapms.devicebridge.service.SerialPortService;
import com.scrapms.devicebridge.service.SocketService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Main Application Class for Scrap MS Device Bridge
 */
public class DeviceBridgeApplication extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceBridgeApplication.class);
    private static final String APP_TITLE = "Scrap MS Device Bridge";
    private static final int MIN_WIDTH = 800;
    private static final int MIN_HEIGHT = 600;
    private static final int DEFAULT_WIDTH = 1200;
    private static final int DEFAULT_HEIGHT = 800;
    private static final int ANPR_HTTP_PORT = 8085;
    
    // Global service instances for cleanup
    private static SerialPortService serialPortService;
    private static CameraService cameraService;
    private static SocketService socketService;
    private static List<OnvifEventPoller> onvifEventPollers = new ArrayList<>();
    private static AnprHttpListener anprHttpListener;
    
    // Static initializer - runs BEFORE main() and before any class loading
    static {
        initializeNativeLibraryPath();
    }
    
    /**
     * Initialize jSerialComm native library path before any serial port operations
     * This avoids "Access is denied" errors on Windows when the default temp folder is locked
     * Extracts DLL to the same directory as the JAR file
     */
    private static void initializeNativeLibraryPath() {
        try {
            // Get the directory where the JAR is located
            File jarDir = getJarDirectory();
            System.out.println("[INIT] JAR directory: " + jarDir.getAbsolutePath());
            
            // Extract bundled DLL to JAR directory (Windows only)
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                File dllFile = extractBundledDll(jarDir);
                
                if (dllFile != null && dllFile.exists()) {
                    // CRITICAL: Override the temp directory that jSerialComm uses
                    // jSerialComm uses java.io.tmpdir for extraction
                    System.setProperty("java.io.tmpdir", jarDir.getAbsolutePath());
                    
                    System.out.println("[INIT] Set java.io.tmpdir to: " + jarDir.getAbsolutePath());
                }
            }
            
            // Try to clean up old locked files in original temp directory
            cleanupOldNativeLibraries();
            
            System.out.println("[INIT] Native library initialization complete");
        } catch (Exception e) {
            System.err.println("[INIT] Could not set custom native library path: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get the directory where the JAR file is located
     */
    private static File getJarDirectory() {
        try {
            // Get the location of the JAR file
            String jarPath = DeviceBridgeApplication.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            
            // On Windows, the path might start with / like /C:/path - need to fix it
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                if (jarPath.startsWith("/") && jarPath.length() > 2 && jarPath.charAt(2) == ':') {
                    jarPath = jarPath.substring(1); // Remove leading /
                }
                jarPath = jarPath.replace("/", "\\"); // Use Windows path separator
            }
            
            File jarFile = new File(jarPath);
            System.out.println("[INIT] JAR file path: " + jarFile.getAbsolutePath());
            System.out.println("[INIT] JAR file exists: " + jarFile.exists());
            System.out.println("[INIT] JAR file is file: " + jarFile.isFile());
            
            // If it's a JAR file, get its parent directory
            if (jarFile.isFile()) {
                File parentDir = jarFile.getParentFile();
                System.out.println("[INIT] Using JAR parent directory: " + parentDir.getAbsolutePath());
                return parentDir;
            }
            
            // If running from IDE (classes directory), use current working directory
            File currentDir = new File(System.getProperty("user.dir"));
            System.out.println("[INIT] Using current working directory: " + currentDir.getAbsolutePath());
            return currentDir;
        } catch (Exception e) {
            System.err.println("[INIT] Could not determine JAR directory: " + e.getMessage());
            e.printStackTrace();
            File currentDir = new File(System.getProperty("user.dir"));
            System.out.println("[INIT] Fallback to current directory: " + currentDir.getAbsolutePath());
            return currentDir;
        }
    }
    
    /**
     * Extract the bundled jSerialComm DLL from our JAR to the JAR directory
     * Returns the extracted DLL file
     */
    private static File extractBundledDll(File targetDir) {
        try {
            String dllResourcePath = "/native/windows-x86_64/jSerialComm.dll";
            
            // Create the exact directory structure jSerialComm expects:
            // {tmpdir}/jSerialComm/./2.10.4/jSerialComm.dll
            File jSerialCommDir = new File(targetDir, "jSerialComm" + File.separator + "." + File.separator + "2.10.4");
            if (!jSerialCommDir.exists()) {
                jSerialCommDir.mkdirs();
            }
            
            File dllFile = new File(jSerialCommDir, "jSerialComm.dll");
            
            // If file exists but is locked, try to work around it
            if (dllFile.exists()) {
                // Check if we can read it (means it's valid)
                if (dllFile.canRead() && dllFile.length() > 0) {
                    System.out.println("[INIT] jSerialComm.dll already exists and is readable: " + dllFile.getAbsolutePath());
                    return dllFile;
                }
                
                // Try to delete the locked file
                System.out.println("[INIT] Attempting to delete locked DLL file...");
                if (!dllFile.delete()) {
                    // File is locked - try renaming it
                    File backupFile = new File(jSerialCommDir, "jSerialComm.dll.old." + System.currentTimeMillis());
                    if (dllFile.renameTo(backupFile)) {
                        System.out.println("[INIT] Renamed locked file to: " + backupFile.getName());
                    } else {
                        System.err.println("[INIT] Cannot delete or rename locked DLL file. Please restart Windows.");
                        return null;
                    }
                }
            }
            
            // Extract from JAR
            try (java.io.InputStream is = DeviceBridgeApplication.class.getResourceAsStream(dllResourcePath)) {
                if (is != null) {
                    byte[] dllBytes = is.readAllBytes();
                    
                    // Write to a temp file first, then rename (avoids partial writes)
                    File tempFile = new File(jSerialCommDir, "jSerialComm.dll.tmp");
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
                        fos.write(dllBytes);
                        fos.flush();
                    }
                    
                    // Rename temp file to final name
                    if (tempFile.renameTo(dllFile)) {
                        System.out.println("[INIT] Extracted jSerialComm.dll to: " + dllFile.getAbsolutePath());
                        return dllFile;
                    } else {
                        // Rename failed, try direct copy
                        tempFile.delete();
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dllFile)) {
                            fos.write(dllBytes);
                            fos.flush();
                        }
                        System.out.println("[INIT] Extracted jSerialComm.dll (direct) to: " + dllFile.getAbsolutePath());
                        return dllFile;
                    }
                } else {
                    System.err.println("[INIT] Bundled DLL not found in JAR: " + dllResourcePath);
                }
            }
        } catch (java.io.FileNotFoundException e) {
            System.err.println("[INIT] Access denied writing DLL. Please try:");
            System.err.println("  1. Run as Administrator");
            System.err.println("  2. Add folder to Windows Defender exclusions");
            System.err.println("  3. Temporarily disable antivirus");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[INIT] Could not extract bundled DLL: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Try to clean up old jSerialComm native libraries from ORIGINAL temp directory
     * NOT from our custom directory
     */
    private static void cleanupOldNativeLibraries() {
        try {
            // Only clean up from the ORIGINAL Windows temp directory, not our custom one
            // The original temp is typically C:\Users\xxx\AppData\Local\Temp
            String userHome = System.getProperty("user.home");
            
            // Clean from original Windows temp location
            File originalTempDir = new File(userHome, "AppData" + File.separator + "Local" + File.separator + "Temp");
            File oldJSerialCommDir = new File(originalTempDir, "jSerialComm");
            
            if (oldJSerialCommDir.exists()) {
                logger.info("Found old jSerialComm in original temp: {}", oldJSerialCommDir.getAbsolutePath());
                deleteDirectory(oldJSerialCommDir);
            }
            
            // Also check user home directory for .jSerialComm
            File userJSerialCommDir = new File(userHome, ".jSerialComm");
            if (userJSerialCommDir.exists()) {
                logger.info("Found old jSerialComm user directory: {}", userJSerialCommDir.getAbsolutePath());
                deleteDirectory(userJSerialCommDir);
            }
        } catch (Exception e) {
            logger.debug("Could not cleanup old native libraries: {}", e.getMessage());
        }
    }
    
    /**
     * Recursively delete a directory
     */
    private static void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if (dir.delete()) {
            logger.debug("Deleted: {}", dir.getAbsolutePath());
        }
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            logger.info("Starting Device Bridge Application...");
            
            // Load the login view
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load(), DEFAULT_WIDTH, DEFAULT_HEIGHT);
            
            // Add CSS stylesheet
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            // Configure stage
            primaryStage.setTitle(APP_TITLE);
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(MIN_WIDTH);
            primaryStage.setMinHeight(MIN_HEIGHT);
            
            // Set application icon (if available)
            try {
                primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/images/icon.png")));
            } catch (Exception e) {
                logger.warn("Could not load application icon", e);
            }
            
            // Handle window close event
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Window close requested - cleaning up...");
                cleanupAllConnections();
                Platform.exit();
            });
            
            primaryStage.show();
            logger.info("Application started successfully");
            
        } catch (IOException e) {
            logger.error("Failed to start application", e);
            throw new RuntimeException("Failed to start application", e);
        }
    }

    @Override
    public void stop() {
        logger.info("Application stop() called - cleaning up...");
        cleanupAllConnections();
    }
    
    /**
     * Register services for global cleanup
     */
    public static void registerServices(SerialPortService serial, CameraService camera, SocketService socket) {
        serialPortService = serial;
        cameraService = camera;
        socketService = socket;
        logger.debug("Services registered for cleanup");
        
        // Start ANPR HTTP Listener to receive push notifications from camera
        startAnprHttpListener();
        
        // Start ONVIF Event Poller for Secura camera (Pull-Point mode)
        // This polls the camera for ANPR events since it doesn't support push notifications
        startOnvifEventPoller("192.168.0.156", "admin", "admin@123");
    }
    
    /**
     * Start the ANPR HTTP Listener to receive push notifications from cameras
     */
    public static void startAnprHttpListener() {
        try {
            anprHttpListener = new AnprHttpListener(ANPR_HTTP_PORT);
            
            // Set callback to send plates to backend via socket
            anprHttpListener.setPlateCallback(plateNumber -> {
                if (socketService != null && socketService.isConnected()) {
                    socketService.sendAnprReading(plateNumber, "HTTP-Push");
                } else {
                    logger.warn("ANPR plate detected but socket not connected: {}", plateNumber);
                }
            });
            
            anprHttpListener.start();
            logger.info("ANPR HTTP Listener started on port {}", ANPR_HTTP_PORT);
            logger.info("Configure your camera's HTTP Notification to: http://YOUR_IP:{}/anpr", ANPR_HTTP_PORT);
        } catch (Exception e) {
            logger.error("Failed to start ANPR HTTP Listener on port {}: {}", ANPR_HTTP_PORT, e.getMessage());
        }
    }
    
    /**
     * Start an ONVIF Event Poller for a camera (Pull-Point subscription mode)
     * Use this when the camera doesn't support push notifications
     */
    public static OnvifEventPoller startOnvifEventPoller(String cameraIp, String username, String password) {
        return startOnvifEventPoller(cameraIp, 80, username, password);
    }
    
    public static OnvifEventPoller startOnvifEventPoller(String cameraIp, int port, String username, String password) {
        OnvifEventPoller poller = new OnvifEventPoller(cameraIp, port, username, password);
        
        // Set callback to send plates to backend via socket
        poller.setPlateCallback(plateNumber -> {
            if (socketService != null && socketService.isConnected()) {
                socketService.sendAnprReading(plateNumber, "ONVIF-Poll:" + cameraIp);
            } else {
                logger.warn("ANPR plate detected but socket not connected: {}", plateNumber);
            }
        });
        
        poller.start();
        onvifEventPollers.add(poller);
        
        logger.info("Started ONVIF Event Poller for camera: {}", cameraIp);
        return poller;
    }
    
    /**
     * Stop all ONVIF Event Pollers
     */
    public static void stopAllOnvifEventPollers() {
        for (OnvifEventPoller poller : onvifEventPollers) {
            try {
                poller.stop();
            } catch (Exception e) {
                logger.error("Error stopping ONVIF poller", e);
            }
        }
        onvifEventPollers.clear();
    }
    
    /**
     * Cleanup all connections
     */
    public static void cleanupAllConnections() {
        logger.info("Cleaning up all connections...");
        
        // Stop ANPR HTTP Listener
        if (anprHttpListener != null) {
            try {
                anprHttpListener.stop();
                logger.info("ANPR HTTP Listener stopped");
            } catch (Exception e) {
                logger.error("Error stopping ANPR HTTP Listener", e);
            }
        }
        
        // Stop all ONVIF Event Pollers
        stopAllOnvifEventPollers();
        logger.info("ONVIF Event Pollers stopped");
        
        // Disconnect serial ports
        if (serialPortService != null) {
            try {
                serialPortService.disconnectAll();
                logger.info("Serial ports disconnected");
            } catch (Exception e) {
                logger.error("Error disconnecting serial ports", e);
            }
        }
        
        // Stop camera streams
        if (cameraService != null) {
            try {
                cameraService.shutdown();
                logger.info("Camera service shutdown");
            } catch (Exception e) {
                logger.error("Error shutting down camera service", e);
            }
        }
        
        // Disconnect socket
        if (socketService != null) {
            try {
                socketService.disconnect();
                logger.info("Socket disconnected");
            } catch (Exception e) {
                logger.error("Error disconnecting socket", e);
            }
        }
        
        logger.info("All connections cleaned up");
    }

    public static void main(String[] args) {
        // Native library path is already initialized in static block
        // Add shutdown hook for unexpected termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered - cleaning up...");
            cleanupAllConnections();
        }));
        
        launch(args);
    }
}
