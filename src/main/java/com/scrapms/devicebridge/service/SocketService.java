package com.scrapms.devicebridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for WebSocket communication with backend
 */
public class SocketService {
    
    private static final Logger logger = LoggerFactory.getLogger(SocketService.class);
    
    private final ConfigService configService;
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private Socket socket;
    
    public SocketService(ConfigService configService, AuthService authService) {
        this.configService = configService;
        this.authService = authService;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Connect to WebSocket
     */
    public void connect() {
        if (socket != null && socket.connected()) {
            return;
        }
        
        try {
            String serverUrl = configService.getServerUrl();
            String token = authService.getToken();
            
            if (token == null) {
                logger.error("No auth token available for socket connection");
                return;
            }
            
            logger.info("Connecting to WebSocket: {}/device-bridge", serverUrl);
            
            IO.Options options = IO.Options.builder()
                    .setAuth(Map.of("token", token))
                    .setTransports(new String[]{"websocket", "polling"})
                    .setReconnection(true)
                    .setReconnectionAttempts(Integer.MAX_VALUE)
                    .setReconnectionDelay(2000)
                    .setReconnectionDelayMax(30000)
                    .setRandomizationFactor(0.5)
                    .build();
            
            socket = IO.socket(URI.create(serverUrl + "/device-bridge"), options);
            
            setupEventListeners();
            socket.connect();
            
        } catch (Exception e) {
            logger.error("Failed to connect to WebSocket", e);
        }
    }
    
    /**
     * Disconnect from WebSocket
     */
    public void disconnect() {
        if (socket != null) {
            socket.disconnect();
            socket = null;
            logger.info("Disconnected from WebSocket");
        }
    }
    
    /**
     * Reconnect with fresh token (e.g. after re-login)
     */
    public void reconnect() {
        disconnect();
        connect();
    }
    
    /**
     * Setup event listeners
     */
    private void setupEventListeners() {
        socket.on(Socket.EVENT_CONNECT, args -> {
            logger.info("Socket connected to /device-bridge namespace");
            
            // Authenticate
            Map<String, Object> authData = new HashMap<>();
            authData.put("token", authService.getToken());
            authData.put("tenantId", authService.getTenantId());
            authData.put("deviceType", "java");
            socket.emit("device:authenticate", authData);
            
            // Re-select devices
            List<Integer> weighbridges = configService.getSelectedWeighbridges();
            List<Integer> cameras = configService.getSelectedCameras();
            
            if (!weighbridges.isEmpty()) {
                selectWeighbridges(weighbridges);
            }
            if (!cameras.isEmpty()) {
                selectCameras(cameras);
            }
        });
        
        socket.on(Socket.EVENT_DISCONNECT, args -> {
            logger.info("Socket disconnected: {}", args.length > 0 ? args[0] : "unknown");
        });
        
        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            logger.warn("Socket connection error (will retry): {}", args.length > 0 ? args[0] : "unknown");
        });
        
        socket.on("config:weighbridge-updated", args -> {
            logger.info("Weighbridge config updated");
        });
        
        socket.on("config:camera-updated", args -> {
            logger.info("Camera config updated");
        });
    }
    
    /**
     * Select weighbridges to monitor
     */
    public void selectWeighbridges(List<Integer> ids) {
        if (socket != null && socket.connected()) {
            Map<String, Object> data = new HashMap<>();
            data.put("weighbridgeIds", ids);
            socket.emit("device:select-weighbridges", data);
            logger.info("Selected weighbridges: {}", ids);
        }
    }
    
    /**
     * Select cameras to monitor
     */
    public void selectCameras(List<Integer> ids) {
        if (socket != null && socket.connected()) {
            Map<String, Object> data = new HashMap<>();
            data.put("cameraIds", ids);
            socket.emit("device:select-cameras", data);
            logger.info("Selected cameras: {}", ids);
        }
    }
    
    /**
     * Send weight reading to backend
     */
    public void sendWeightReading(int weighbridgeId, double weight, String unit, 
                                  boolean isStable, String weighbridgeName) {
        if (socket != null && socket.connected()) {
            Map<String, Object> data = new HashMap<>();
            data.put("weighbridgeMasterId", weighbridgeId);
            data.put("weighbridgeId", weighbridgeId);
            data.put("weighbridgeName", weighbridgeName);
            data.put("weight", weight);
            data.put("unit", unit);
            data.put("isStable", isStable);
            data.put("rawData", String.format("%.2f %s", weight, unit));
            data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            socket.emit("weight:reading", data);
            logger.debug("Sent weight reading: {} {} from {}", weight, unit, weighbridgeName);
        }
    }
    
    /**
     * Send camera snapshot to backend
     */
    public void sendCameraSnapshot(int cameraId, String imageData, String cameraName) {
        if (socket != null && socket.connected()) {
            Map<String, Object> data = new HashMap<>();
            data.put("cameraMasterId", cameraId);
            data.put("cameraId", cameraId);
            data.put("cameraName", cameraName);
            data.put("imageBase64", imageData);
            data.put("imageData", imageData);
            data.put("width", 640);
            data.put("height", 480);
            data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            socket.emit("camera:snapshot", data);
            logger.debug("Sent camera snapshot from {}", cameraName);
        }
    }
    
    /**
     * Send ANPR plate reading to backend
     */
    public void sendAnprReading(String plateNumber, String source) {
        if (socket != null && socket.connected()) {
            Map<String, Object> data = new HashMap<>();
            data.put("plateNumber", plateNumber);
            data.put("source", source);
            data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            socket.emit("anpr:reading", data);
            logger.info("Sent ANPR reading: {} from {}", plateNumber, source);
        } else {
            logger.warn("Cannot send ANPR reading - socket not connected");
        }
    }
    
    /**
     * Check if connected
     */
    public boolean isConnected() {
        return socket != null && socket.connected();
    }
}
