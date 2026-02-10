package com.scrapms.devicebridge.controller;

import com.scrapms.devicebridge.DeviceBridgeApplication;
import com.scrapms.devicebridge.model.*;
import com.scrapms.devicebridge.service.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;

public class MonitoringController {
    
    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);
    
    @FXML private ToggleButton debugModeToggle;
    @FXML private Label debugModeLabel;
    @FXML private Label connectionStatus;
    @FXML private VBox weighbridgePanel;
    @FXML private VBox cameraPanel;
    @FXML private Label wbCount;
    @FXML private Label camCount;
    
    private ConfigService configService;
    private AuthService authService;
    private ApiService apiService;
    private SerialPortService serialPortService;
    private CameraService cameraService;
    private SocketService socketService;
    
    private List<WeighbridgeMaster> selectedWeighbridges = new ArrayList<>();
    private List<CameraMaster> selectedCameras = new ArrayList<>();
    private Map<Integer, WeighbridgeConfig> weighbridgeConfigs = new HashMap<>();
    private Map<Integer, CameraConfig> cameraConfigs = new HashMap<>();
    private Map<Integer, VBox> weighbridgeCards = new HashMap<>();
    private Map<Integer, VBox> cameraCards = new HashMap<>();
    private Map<Integer, Timer> cameraTimers = new HashMap<>();
    private static final int CAMERA_STREAM_FPS = 3; // 3 frames per second for live stream (reduced for performance)
    // Camera snapshots are only sent to socket when weight is captured
    
    @FXML
    public void initialize() {
        configService = ConfigService.getInstance();
        authService = new AuthService(configService);
        apiService = new ApiService(configService, authService);
        serialPortService = new SerialPortService();
        cameraService = new CameraService();
        socketService = new SocketService(configService, authService);
        
        // Register services for global cleanup (app close, etc.)
        DeviceBridgeApplication.registerServices(serialPortService, cameraService, socketService);
        
        // Load debug mode
        boolean debugMode = configService.getDebugMode();
        debugModeToggle.setSelected(debugMode);
        updateDebugModeUI();
        
        // Connect to WebSocket
        socketService.connect();
        connectionStatus.setText("Connected");
        
        // Load devices
        loadDevices();
    }
    
    private void loadDevices() {
        List<Integer> selectedWbIds = configService.getSelectedWeighbridges();
        List<Integer> selectedCamIds = configService.getSelectedCameras();
        
        // Load weighbridges
        apiService.getWeighbridges().thenAccept(wbs -> {
            selectedWeighbridges = wbs.stream()
                    .filter(wb -> selectedWbIds.contains(wb.getId()))
                    .toList();
            
            Platform.runLater(() -> {
                wbCount.setText(selectedWeighbridges.size() + " active");
                renderWeighbridges();
            });
        });
        
        // Load cameras
        apiService.getCameras().thenAccept(cams -> {
            selectedCameras = cams.stream()
                    .filter(cam -> selectedCamIds.contains(cam.getId()))
                    .toList();
            
            Platform.runLater(() -> {
                camCount.setText(selectedCameras.size() + " active");
                renderCameras();
            });
        });
    }
    
    private void renderWeighbridges() {
        weighbridgePanel.getChildren().clear();
        
        if (selectedWeighbridges.isEmpty()) {
            Label emptyLabel = new Label("No weighbridges selected");
            emptyLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 14px;");
            weighbridgePanel.getChildren().add(emptyLabel);
            return;
        }
        
        for (WeighbridgeMaster wb : selectedWeighbridges) {
            VBox card = createWeighbridgeCard(wb);
            weighbridgeCards.put(wb.getId(), card);
            weighbridgePanel.getChildren().add(card);
            VBox.setMargin(card, new Insets(0, 0, 10, 0));
            
            // Load config and connect if not in debug mode
            if (!debugModeToggle.isSelected()) {
                connectWeighbridge(wb.getId());
            }
        }
    }
    
    private VBox createWeighbridgeCard(WeighbridgeMaster wb) {
        VBox card = new VBox(10);
        card.getStyleClass().add("weighbridge-card");
        card.setPadding(new Insets(15));
        
        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        VBox info = new VBox(2);
        Label name = new Label(wb.getName());
        name.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        Label code = new Label(wb.getCode() + " • " + (wb.getLocation() != null ? wb.getLocation() : ""));
        code.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
        info.getChildren().addAll(name, code);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label statusDot = new Label("●");
        statusDot.setStyle("-fx-text-fill: #4ade80; -fx-font-size: 10px;");
        statusDot.setId("status-dot-" + wb.getId());
        
        header.getChildren().addAll(info, spacer, statusDot);
        
        // Weight Display
        VBox weightDisplay = new VBox(5);
        weightDisplay.getStyleClass().add("weight-display");
        weightDisplay.setAlignment(Pos.CENTER);
        
        HBox weightBox = new HBox(5);
        weightBox.setAlignment(Pos.CENTER);
        
        Label weightValue = new Label("0.00");
        weightValue.setId("weight-" + wb.getId());
        weightValue.getStyleClass().add("weight-value");
        
        Label weightUnit = new Label("kg");
        weightUnit.setId("unit-" + wb.getId());
        weightUnit.getStyleClass().add("weight-unit");
        
        weightBox.getChildren().addAll(weightValue, weightUnit);
        
        Label weightStatus = new Label("Waiting for data...");
        weightStatus.setId("status-" + wb.getId());
        weightStatus.getStyleClass().add("weight-status");
        
        weightDisplay.getChildren().addAll(weightBox, weightStatus);
        
        // Actions
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER);
        
        Button readBtn = new Button("📖 Read Weight");
        readBtn.getStyleClass().add("btn-secondary");
        readBtn.setOnAction(e -> readWeight(wb.getId()));
        
        Button configBtn = new Button("⚙️ Config");
        configBtn.getStyleClass().add("btn-info");
        configBtn.setOnAction(e -> showWeighbridgeConfig(wb));
        
        actions.getChildren().addAll(readBtn, configBtn);
        
        // Debug panel (only show in debug mode)
        VBox debugPanel = new VBox(8);
        debugPanel.setId("debug-panel-" + wb.getId());
        debugPanel.setVisible(debugModeToggle.isSelected());
        debugPanel.setManaged(debugModeToggle.isSelected());
        debugPanel.setStyle("-fx-background-color: #0f0f1a; -fx-background-radius: 8px; -fx-padding: 10px;");
        
        Label debugTitle = new Label("🔧 Debug Mode");
        debugTitle.setStyle("-fx-text-fill: #888; -fx-font-size: 12px;");
        
        Button testWeightBtn = new Button("Send Test Weight");
        testWeightBtn.getStyleClass().add("btn-secondary");
        testWeightBtn.setOnAction(e -> sendTestWeight(wb.getId()));
        
        debugPanel.getChildren().addAll(debugTitle, testWeightBtn);
        
        card.getChildren().addAll(header, weightDisplay, actions, debugPanel);
        
        return card;
    }
    
    private void renderCameras() {
        cameraPanel.getChildren().clear();
        
        if (selectedCameras.isEmpty()) {
            Label emptyLabel = new Label("No cameras selected");
            emptyLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 14px;");
            cameraPanel.getChildren().add(emptyLabel);
            return;
        }
        
        for (CameraMaster cam : selectedCameras) {
            VBox card = createCameraCard(cam);
            cameraCards.put(cam.getId(), card);
            cameraPanel.getChildren().add(card);
            VBox.setMargin(card, new Insets(0, 0, 10, 0));
            
            // Always start camera preview - cameras run regardless of debug mode
            // Debug mode only affects weighbridges (uses test data instead of real serial port)
            startCameraPreview(cam.getId());
        }
    }
    
    private VBox createCameraCard(CameraMaster cam) {
        VBox card = new VBox();
        card.getStyleClass().add("camera-card");
        
        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 12, 10, 12));
        
        VBox info = new VBox(2);
        Label name = new Label(cam.getName());
        name.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold;");
        Label code = new Label(cam.getCode() + " • " + cam.getCameraType());
        code.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
        info.getChildren().addAll(name, code);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button configBtn = new Button("⚙️ Config");
        configBtn.getStyleClass().add("btn-info");
        configBtn.setOnAction(e -> showCameraConfig(cam));
        
        header.getChildren().addAll(info, spacer, configBtn);
        
        // Preview
        StackPane preview = new StackPane();
        preview.setId("preview-" + cam.getId());
        preview.setStyle("-fx-background-color: #000;");
        preview.setPrefHeight(270);
        
        Label placeholder = new Label("📷\nConnecting to camera...");
        placeholder.setStyle("-fx-text-fill: #667eea; -fx-font-size: 14px; -fx-text-alignment: center;");
        placeholder.setAlignment(Pos.CENTER);
        
        preview.getChildren().add(placeholder);
        
        // Actions overlay
        HBox overlay = new HBox(5);
        overlay.setAlignment(Pos.BOTTOM_RIGHT);
        overlay.setPadding(new Insets(8));
        
        Button captureBtn = new Button("📸");
        captureBtn.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white; -fx-background-radius: 50%;");
        captureBtn.setTooltip(new Tooltip("Capture Snapshot"));
        captureBtn.setOnAction(e -> captureSnapshot(cam.getId()));
        
        Button syncBtn = new Button("�");
        syncBtn.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white; -fx-background-radius: 50%;");
        syncBtn.setTooltip(new Tooltip("Sync - Reconnect Camera"));
        syncBtn.setOnAction(e -> syncCamera(cam.getId()));
        
        overlay.getChildren().addAll(captureBtn, syncBtn);
        
        StackPane.setAlignment(overlay, Pos.BOTTOM_RIGHT);
        preview.getChildren().add(overlay);
        
        card.getChildren().addAll(header, preview);
        
        return card;
    }
    
    @FXML
    private void handleDebugModeToggle() {
        boolean debugMode = debugModeToggle.isSelected();
        configService.setDebugMode(debugMode);
        updateDebugModeUI();
        
        if (debugMode) {
            // Debug mode ON: Only disconnect weighbridges (use test data)
            // Camera streams should continue running for preview
            serialPortService.disconnectAll();
            logger.info("Debug mode ON - weighbridges disconnected, camera streams continue");
        } else {
            // Debug mode OFF: Connect to real weighbridges
            // Camera streams are already running
            selectedWeighbridges.forEach(wb -> connectWeighbridge(wb.getId()));
            logger.info("Debug mode OFF - connecting to real weighbridges");
        }
        
        // Update UI - show/hide debug panels for weighbridges
        weighbridgeCards.values().forEach(card -> {
            VBox debugPanel = (VBox) card.lookup("#debug-panel-" + card.getId());
            if (debugPanel != null) {
                debugPanel.setVisible(debugMode);
                debugPanel.setManaged(debugMode);
            }
        });
    }
    
    private void updateDebugModeUI() {
        if (debugModeToggle.isSelected()) {
            debugModeLabel.setText("Debug Mode ON");
            debugModeLabel.setStyle("-fx-text-fill: #fbbf24;");
        } else {
            debugModeLabel.setText("Debug Mode OFF");
            debugModeLabel.setStyle("-fx-text-fill: rgba(255,255,255,0.8);");
        }
    }
    
    private void connectWeighbridge(int weighbridgeId) {
        apiService.getWeighbridgeConfig(weighbridgeId).thenAccept(config -> {
            if (config != null && config.getSerialPort() != null) {
                weighbridgeConfigs.put(weighbridgeId, config);
                
                SerialPortService.ConnectionResult result = serialPortService.connect(
                        weighbridgeId, 
                        config, 
                        reading -> handleWeightReading(weighbridgeId, reading)
                );
                
                if (result.isSuccess()) {
                    logger.info("Connected to weighbridge {}", weighbridgeId);
                } else {
                    logger.error("Failed to connect to weighbridge {}: {}", weighbridgeId, result.getError());
                }
            }
        });
    }
    
    private void handleWeightReading(int weighbridgeId, SerialPortService.WeightReading reading) {
        Platform.runLater(() -> {
            VBox card = weighbridgeCards.get(weighbridgeId);
            if (card != null) {
                Label weightValue = (Label) card.lookup("#weight-" + weighbridgeId);
                Label weightUnit = (Label) card.lookup("#unit-" + weighbridgeId);
                Label weightStatus = (Label) card.lookup("#status-" + weighbridgeId);
                
                if (weightValue != null) {
                    weightValue.setText(String.format("%.2f", reading.getWeight()));
                }
                if (weightUnit != null) {
                    weightUnit.setText(reading.getUnit());
                }
                if (weightStatus != null) {
                    weightStatus.setText(reading.isStable() ? "● Stable" : "○ Unstable");
                    weightStatus.getStyleClass().removeAll("weight-status-stable", "weight-status-unstable");
                    weightStatus.getStyleClass().add(reading.isStable() ? "weight-status-stable" : "weight-status-unstable");
                }
            }
        });
        
        // Send weight to backend
        WeighbridgeMaster wb = selectedWeighbridges.stream()
                .filter(w -> w.getId().equals(weighbridgeId))
                .findFirst()
                .orElse(null);
        
        if (wb != null) {
            socketService.sendWeightReading(weighbridgeId, reading.getWeight(), 
                    reading.getUnit(), reading.isStable(), wb.getName());
            
            // Capture and send camera snapshots when weight is received
            captureAndSendAllCameraSnapshots();
        }
    }
    
    /**
     * Capture snapshots from all cameras and send to socket
     * Called when a weight reading is received
     */
    private void captureAndSendAllCameraSnapshots() {
        for (CameraMaster cam : selectedCameras) {
            CameraConfig config = cameraConfigs.get(cam.getId());
            if (config != null) {
                cameraService.captureSnapshot(cam.getId(), config, result -> {
                    if (result.isSuccess()) {
                        socketService.sendCameraSnapshot(cam.getId(), result.getImageData(), cam.getName());
                        logger.info("Sent camera snapshot from {} (triggered by weight reading)", cam.getName());
                    } else {
                        logger.warn("Failed to capture snapshot from {}: {}", cam.getName(), result.getError());
                    }
                });
            }
        }
    }
    
    private void readWeight(int weighbridgeId) {
        if (debugModeToggle.isSelected()) {
            sendTestWeight(weighbridgeId);
        } else {
            SerialPortService.WeightReading reading = serialPortService.requestReading(weighbridgeId);
            if (reading != null) {
                handleWeightReading(weighbridgeId, reading);
            }
        }
    }
    
    private void sendTestWeight(int weighbridgeId) {
        ConfigService.WeightRange range = configService.getDebugWeightRange();
        double weight = range.getMin() + Math.random() * (range.getMax() - range.getMin());
        
        SerialPortService.WeightReading reading = new SerialPortService.WeightReading(
                weight, "kg", true, String.format("%.2f kg", weight), java.time.LocalDateTime.now()
        );
        
        handleWeightReading(weighbridgeId, reading);
    }
    
    private void startCameraPreview(int cameraId) {
        apiService.getCameraConfig(cameraId).thenAccept(config -> {
            if (config != null) {
                cameraConfigs.put(cameraId, config);
                
                // Start live streaming - only for UI preview, NOT sending to socket
                cameraService.startLivePreview(cameraId, config, 
                    // onFrame - update UI only
                    image -> {
                        Platform.runLater(() -> updateCameraPreview(cameraId, image));
                    },
                    // onBase64Frame - null, don't send to socket automatically
                    null,
                    CAMERA_STREAM_FPS
                );
                
                logger.info("Started live stream for camera {} at {} fps (UI only, no auto socket send)", cameraId, CAMERA_STREAM_FPS);
            }
        });
    }
    
    private void updateCameraPreview(int cameraId, Image image) {
        VBox card = cameraCards.get(cameraId);
        if (card != null) {
            StackPane preview = (StackPane) card.lookup("#preview-" + cameraId);
            if (preview != null) {
                // Find or create ImageView
                ImageView imageView = null;
                for (var child : preview.getChildren()) {
                    if (child instanceof ImageView) {
                        imageView = (ImageView) child;
                        break;
                    }
                }
                
                if (imageView == null) {
                    imageView = new ImageView();
                    imageView.setPreserveRatio(true);
                    imageView.fitWidthProperty().bind(preview.widthProperty());
                    imageView.fitHeightProperty().bind(preview.heightProperty());
                    
                    // Clear placeholder and add imageview
                    preview.getChildren().clear();
                    preview.getChildren().add(imageView);
                    
                    // Re-add overlay buttons
                    HBox overlay = createCameraOverlay(cameraId);
                    StackPane.setAlignment(overlay, Pos.BOTTOM_RIGHT);
                    preview.getChildren().add(overlay);
                }
                
                imageView.setImage(image);
            }
        }
    }
    
    private HBox createCameraOverlay(int cameraId) {
        HBox overlay = new HBox(5);
        overlay.setAlignment(Pos.BOTTOM_RIGHT);
        overlay.setPadding(new Insets(8));
        
        Button captureBtn = new Button("📸");
        captureBtn.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white;");
        captureBtn.setTooltip(new Tooltip("Capture Snapshot"));
        captureBtn.setOnAction(e -> captureSnapshot(cameraId));
        
        Button syncBtn = new Button("🔃");
        syncBtn.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white;");
        syncBtn.setTooltip(new Tooltip("Sync - Reconnect Camera"));
        syncBtn.setOnAction(e -> syncCamera(cameraId));
        
        overlay.getChildren().addAll(captureBtn, syncBtn);
        return overlay;
    }
    
    /**
     * Sync camera - disconnect and reconnect to refresh the live feed
     */
    private void syncCamera(int cameraId) {
        logger.info("Syncing camera {} - disconnecting and reconnecting...", cameraId);
        
        // Show syncing status on the preview
        VBox card = cameraCards.get(cameraId);
        if (card != null) {
            StackPane preview = (StackPane) card.lookup("#preview-" + cameraId);
            if (preview != null) {
                // Show syncing message
                Label syncingLabel = new Label("🔃\nSyncing camera...");
                syncingLabel.setStyle("-fx-text-fill: #667eea; -fx-font-size: 14px; -fx-text-alignment: center;");
                syncingLabel.setAlignment(Pos.CENTER);
                
                preview.getChildren().clear();
                preview.getChildren().add(syncingLabel);
            }
        }
        
        // Stop the current camera stream
        cameraService.stopLivePreview(cameraId);
        
        // Small delay before reconnecting to ensure clean disconnect
        new Thread(() -> {
            try {
                Thread.sleep(500); // 500ms delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Restart the camera preview
            Platform.runLater(() -> {
                startCameraPreview(cameraId);
                logger.info("Camera {} sync complete - reconnected", cameraId);
            });
        }).start();
    }
    
    private void stopCameraTimer(int cameraId) {
        Timer timer = cameraTimers.remove(cameraId);
        if (timer != null) {
            timer.cancel();
            logger.info("Stopped camera timer for camera {}", cameraId);
        }
        cameraService.stopLivePreview(cameraId);
    }
    
    private void stopAllCameraTimers() {
        cameraTimers.forEach((id, timer) -> timer.cancel());
        cameraTimers.clear();
        cameraService.stopAllPreviews();
    }
    
    private void captureSnapshot(int cameraId) {
        CameraConfig config = cameraConfigs.get(cameraId);
        if (config == null) return;
        
        cameraService.captureSnapshot(cameraId, config, result -> {
            Platform.runLater(() -> {
                VBox card = cameraCards.get(cameraId);
                if (card != null) {
                    StackPane preview = (StackPane) card.lookup("#preview-" + cameraId);
                    if (preview != null && result.isSuccess()) {
                        try {
                            String base64 = result.getImageData().split(",")[1];
                            byte[] imageBytes = Base64.getDecoder().decode(base64);
                            Image image = new Image(new ByteArrayInputStream(imageBytes));
                            
                            ImageView imageView = new ImageView(image);
                            imageView.setPreserveRatio(true);
                            imageView.setFitWidth(preview.getWidth());
                            
                            preview.getChildren().clear();
                            preview.getChildren().add(imageView);
                            
                            // Re-add overlay
                            HBox overlay = new HBox(5);
                            overlay.setAlignment(Pos.BOTTOM_RIGHT);
                            overlay.setPadding(new Insets(8));
                            
                            Button captureBtn = new Button("📸");
                            captureBtn.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white;");
                            captureBtn.setTooltip(new Tooltip("Capture Snapshot"));
                            captureBtn.setOnAction(e -> captureSnapshot(cameraId));
                            
                            Button syncBtn = new Button("�");
                            syncBtn.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-text-fill: white;");
                            syncBtn.setTooltip(new Tooltip("Sync - Reconnect Camera"));
                            syncBtn.setOnAction(e -> syncCamera(cameraId));
                            
                            overlay.getChildren().addAll(captureBtn, syncBtn);
                            StackPane.setAlignment(overlay, Pos.BOTTOM_RIGHT);
                            preview.getChildren().add(overlay);
                            
                            // Send to backend
                            CameraMaster cam = selectedCameras.stream()
                                    .filter(c -> c.getId().equals(cameraId))
                                    .findFirst()
                                    .orElse(null);
                            
                            if (cam != null) {
                                socketService.sendCameraSnapshot(cameraId, result.getImageData(), cam.getName());
                            }
                            
                        } catch (Exception e) {
                            logger.error("Error displaying image", e);
                        }
                    }
                }
            });
        });
    }
    

    private void showWeighbridgeConfig(WeighbridgeMaster wb) {
        apiService.getWeighbridgeConfig(wb.getId()).thenAccept(config -> {
            Platform.runLater(() -> showConfigDialog("⚖️ " + wb.getName() + " - Configuration", 
                    formatWeighbridgeConfig(config)));
        });
    }
    
    private void showCameraConfig(CameraMaster cam) {
        apiService.getCameraConfig(cam.getId()).thenAccept(config -> {
            Platform.runLater(() -> showConfigDialog("📷 " + cam.getName() + " - Configuration", 
                    formatCameraConfig(config)));
        });
    }
    
    private String formatWeighbridgeConfig(WeighbridgeConfig config) {
        if (config == null) {
            return "No configuration found for this weighbridge.";
        }
        
        return String.format("""
                Serial Port Settings:
                • Serial Port: %s
                • Baud Rate: %d
                • Data Bits: %d
                • Stop Bits: %d
                • Parity: %s
                
                Weight Parsing:
                • Weight Regex: %s
                • Start Marker: %s
                • End Marker: %s
                • Multiplier: %.4f
                • Unit: %s
                
                Stability Settings:
                • Polling Interval: %d ms
                • Stable Readings: %d
                • Stability Threshold: %.2f
                • Status: %s
                """,
                config.getSerialPort() != null ? config.getSerialPort() : "Not configured",
                config.getBaudRate(),
                config.getDataBits(),
                config.getStopBits(),
                config.getParity(),
                config.getWeightRegex() != null ? config.getWeightRegex() : "Default (any number)",
                config.getWeightStartMarker() != null ? config.getWeightStartMarker() : "None",
                config.getWeightEndMarker() != null ? config.getWeightEndMarker() : "None",
                config.getWeightMultiplier(),
                config.getWeightUnit(),
                config.getPollingInterval(),
                config.getStableReadings(),
                config.getStabilityThreshold(),
                config.getIsActive() ? "Active" : "Inactive"
        );
    }
    
    private String formatCameraConfig(CameraConfig config) {
        if (config == null) {
            return "No configuration found for this camera.";
        }
        
        String url = config.getEffectiveUrl();
        if (url != null && config.getPassword() != null) {
            url = url.replace(config.getPassword(), "••••••••");
        }
        
        return String.format("""
                Connection Settings:
                • URL: %s
                • Username: %s
                • Password: %s
                • Transport: %s
                • Timeout: %d seconds
                
                Snapshot Settings:
                • Width: %d px
                • Height: %d px
                • Quality: %d%%
                • Status: %s
                """,
                url != null ? url : "Not configured",
                config.getUsername() != null ? config.getUsername() : "Not set",
                config.getPassword() != null ? "••••••••" : "Not set",
                config.getTransport(),
                config.getTimeout(),
                config.getSnapshotWidth(),
                config.getSnapshotHeight(),
                config.getSnapshotQuality(),
                config.getIsActive() ? "Active" : "Inactive"
        );
    }
    
    private void showConfigDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        
        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(15);
        textArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        
        alert.getDialogPane().setContent(textArea);
        alert.getDialogPane().setPrefWidth(600);
        
        alert.showAndWait();
    }
    
    @FXML
    private void handleBack() {
        logger.info("Navigating back to device selection - cleaning up connections...");
        
        // Cleanup all connections
        stopAllCameraTimers();
        serialPortService.disconnectAll();
        cameraService.shutdown();
        socketService.disconnect();
        
        // Clear registered services since we're leaving monitoring
        DeviceBridgeApplication.registerServices(null, null, null);
        
        logger.info("All connections cleaned up");
        
        // Navigate back
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/device-selection.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            Stage stage = (Stage) debugModeToggle.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Device Selection - Scrap MS Device Bridge");
            stage.setMaximized(false);
            
        } catch (IOException e) {
            logger.error("Failed to load device selection screen", e);
        }
    }
}
