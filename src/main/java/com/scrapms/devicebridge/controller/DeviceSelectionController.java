package com.scrapms.devicebridge.controller;

import com.scrapms.devicebridge.model.CameraMaster;
import com.scrapms.devicebridge.model.WeighbridgeMaster;
import com.scrapms.devicebridge.service.ApiService;
import com.scrapms.devicebridge.service.AuthService;
import com.scrapms.devicebridge.service.ConfigService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeviceSelectionController {
    
    private static final Logger logger = LoggerFactory.getLogger(DeviceSelectionController.class);
    
    @FXML private VBox weighbridgeList;
    @FXML private VBox cameraList;
    @FXML private Button continueButton;
    @FXML private Button backButton;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator progressIndicator;
    
    private ConfigService configService;
    private AuthService authService;
    private ApiService apiService;
    
    private List<WeighbridgeMaster> weighbridges = new ArrayList<>();
    private List<CameraMaster> cameras = new ArrayList<>();
    private List<CheckBox> weighbridgeCheckboxes = new ArrayList<>();
    private List<CheckBox> cameraCheckboxes = new ArrayList<>();
    
    @FXML
    public void initialize() {
        configService = ConfigService.getInstance();
        authService = new AuthService(configService);
        apiService = new ApiService(configService, authService);
        
        progressIndicator.setVisible(true);
        statusLabel.setText("Loading devices...");
        continueButton.setDisable(true);
        
        loadDevices();
    }
    
    private void loadDevices() {
        // Load weighbridges
        apiService.getWeighbridges().thenAccept(wbs -> {
            weighbridges = wbs;
            Platform.runLater(this::renderWeighbridges);
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                statusLabel.setText("Failed to load weighbridges: " + throwable.getMessage());
                logger.error("Failed to load weighbridges", throwable);
            });
            return null;
        });
        
        // Load cameras
        apiService.getCameras().thenAccept(cams -> {
            cameras = cams;
            Platform.runLater(() -> {
                renderCameras();
                progressIndicator.setVisible(false);
                statusLabel.setText("Select devices to monitor");
                continueButton.setDisable(false);
            });
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                statusLabel.setText("Failed to load cameras: " + throwable.getMessage());
                logger.error("Failed to load cameras", throwable);
                progressIndicator.setVisible(false);
            });
            return null;
        });
    }
    
    private void renderWeighbridges() {
        weighbridgeList.getChildren().clear();
        weighbridgeCheckboxes.clear();
        
        List<Integer> selectedIds = configService.getSelectedWeighbridges();
        
        if (weighbridges.isEmpty()) {
            Label emptyLabel = new Label("No weighbridges available");
            emptyLabel.setStyle("-fx-text-fill: #888;");
            weighbridgeList.getChildren().add(emptyLabel);
            return;
        }
        
        for (WeighbridgeMaster wb : weighbridges) {
            CheckBox checkbox = new CheckBox();
            checkbox.setText(wb.getName() + " (" + wb.getCode() + ")");
            checkbox.setSelected(selectedIds.contains(wb.getId()));
            checkbox.setUserData(wb.getId());
            checkbox.getStyleClass().add("device-checkbox");
            
            VBox.setMargin(checkbox, new Insets(5, 0, 5, 0));
            weighbridgeCheckboxes.add(checkbox);
            weighbridgeList.getChildren().add(checkbox);
        }
    }
    
    private void renderCameras() {
        cameraList.getChildren().clear();
        cameraCheckboxes.clear();
        
        List<Integer> selectedIds = configService.getSelectedCameras();
        
        if (cameras.isEmpty()) {
            Label emptyLabel = new Label("No cameras available");
            emptyLabel.setStyle("-fx-text-fill: #888;");
            cameraList.getChildren().add(emptyLabel);
            return;
        }
        
        for (CameraMaster cam : cameras) {
            CheckBox checkbox = new CheckBox();
            checkbox.setText(cam.getName() + " (" + cam.getCode() + ")");
            checkbox.setSelected(selectedIds.contains(cam.getId()));
            checkbox.setUserData(cam.getId());
            checkbox.getStyleClass().add("device-checkbox");
            
            VBox.setMargin(checkbox, new Insets(5, 0, 5, 0));
            cameraCheckboxes.add(checkbox);
            cameraList.getChildren().add(checkbox);
        }
    }
    
    @FXML
    private void handleContinue() {
        // Get selected weighbridges
        List<Integer> selectedWeighbridges = new ArrayList<>();
        for (CheckBox cb : weighbridgeCheckboxes) {
            if (cb.isSelected()) {
                selectedWeighbridges.add((Integer) cb.getUserData());
            }
        }
        
        // Get selected cameras
        List<Integer> selectedCameras = new ArrayList<>();
        for (CheckBox cb : cameraCheckboxes) {
            if (cb.isSelected()) {
                selectedCameras.add((Integer) cb.getUserData());
            }
        }
        
        // Validate selection
        if (selectedWeighbridges.isEmpty() && selectedCameras.isEmpty()) {
            statusLabel.setText("Please select at least one device");
            return;
        }
        
        // Save selection
        configService.setSelectedWeighbridges(selectedWeighbridges);
        configService.setSelectedCameras(selectedCameras);
        configService.setAutoConnect(true);
        
        logger.info("Selected {} weighbridges and {} cameras", 
                   selectedWeighbridges.size(), selectedCameras.size());
        
        // Navigate to monitoring
        navigateToMonitoring();
    }
    
    @FXML
    private void handleBack() {
        authService.logout();
        navigateToLogin();
    }
    
    private void navigateToMonitoring() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/monitoring.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            Stage stage = (Stage) continueButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Monitoring - Scrap MS Device Bridge");
            stage.setMaximized(true);
            
        } catch (IOException e) {
            logger.error("Failed to load monitoring screen", e);
            statusLabel.setText("Failed to load monitoring screen");
        }
    }
    
    private void navigateToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Login - Scrap MS Device Bridge");
            
        } catch (IOException e) {
            logger.error("Failed to load login screen", e);
        }
    }
}
