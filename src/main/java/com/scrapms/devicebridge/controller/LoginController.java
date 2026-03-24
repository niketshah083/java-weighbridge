package com.scrapms.devicebridge.controller;

import com.scrapms.devicebridge.service.AuthService;
import com.scrapms.devicebridge.service.ConfigService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LoginController {
    
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    
    @FXML private TextField serverUrlField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberMeCheckbox;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;
    @FXML private ProgressIndicator progressIndicator;
    
    private ConfigService configService;
    private AuthService authService;
    
    @FXML
    public void initialize() {
        configService = ConfigService.getInstance();
        authService = new AuthService(configService);
        
        // Load saved configuration
        serverUrlField.setText(configService.getServerUrl());
        rememberMeCheckbox.setSelected(configService.getRememberMe());
        
        if (configService.getRememberMe()) {
            emailField.setText(configService.getSavedEmail());
            passwordField.setText(configService.getSavedPassword());
        }
        
        // Hide progress indicator initially
        progressIndicator.setVisible(false);
        errorLabel.setVisible(false);
        
        // Auto-login if credentials are saved and devices are selected
        if (configService.getAutoConnect() && configService.getRememberMe()) {
            String savedEmail = configService.getSavedEmail();
            String savedPassword = configService.getSavedPassword();
            if (savedEmail != null && !savedEmail.isEmpty() && !savedPassword.isEmpty()) {
                Platform.runLater(() -> {
                    if (loginButton.getScene() != null) {
                        autoLogin(savedEmail, savedPassword);
                    }
                });
                return;
            }
        }
        
        // Check if already authenticated with valid token
        if (authService.isAuthenticated()) {
            Platform.runLater(() -> {
                if (loginButton.getScene() != null) {
                    navigateToDeviceSelection();
                }
            });
        }
    }
    
    @FXML
    private void handleLogin() {
        String serverUrl = serverUrlField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        
        // Validation
        if (serverUrl.isEmpty()) {
            showError("Please enter server URL");
            return;
        }
        
        if (email.isEmpty()) {
            showError("Please enter email");
            return;
        }
        
        if (password.isEmpty()) {
            showError("Please enter password");
            return;
        }
        
        // Save server URL
        configService.setServerUrl(serverUrl);
        
        // Save remember me preference and credentials
        configService.setRememberMe(rememberMeCheckbox.isSelected());
        if (rememberMeCheckbox.isSelected()) {
            configService.setSavedEmail(email);
            configService.setSavedPassword(password);
        } else {
            configService.setSavedEmail("");
            configService.setSavedPassword("");
        }
        
        // Show loading
        setLoading(true);
        errorLabel.setVisible(false);
        
        // Perform login
        authService.login(email, password).thenAccept(result -> {
            Platform.runLater(() -> {
                setLoading(false);
                
                if (result.isSuccess()) {
                    logger.info("Login successful");
                    navigateToDeviceSelection();
                } else {
                    showError(result.getMessage());
                }
            });
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                setLoading(false);
                showError("Login failed: " + throwable.getMessage());
            });
            return null;
        });
    }
    
    /**
     * Auto-login with saved credentials, then skip to monitoring if devices are selected
     */
    private void autoLogin(String email, String password) {
        setLoading(true);
        errorLabel.setVisible(false);
        
        logger.info("Auto-login with saved credentials...");
        
        authService.login(email, password).thenAccept(result -> {
            Platform.runLater(() -> {
                setLoading(false);
                
                if (result.isSuccess()) {
                    logger.info("Auto-login successful");
                    // Check if devices are already selected — skip to monitoring
                    boolean hasDevices = !configService.getSelectedWeighbridges().isEmpty() 
                                      || !configService.getSelectedCameras().isEmpty();
                    if (hasDevices) {
                        navigateToMonitoring();
                    } else {
                        navigateToDeviceSelection();
                    }
                } else {
                    logger.warn("Auto-login failed: {}", result.getMessage());
                    // Clear auto-connect flag so user can login manually
                    configService.setAutoConnect(false);
                    showError("Session expired. Please login again.");
                }
            });
        }).exceptionally(throwable -> {
            Platform.runLater(() -> {
                setLoading(false);
                configService.setAutoConnect(false);
                showError("Auto-login failed: " + throwable.getMessage());
            });
            return null;
        });
    }
    
    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        loginButton.setDisable(loading);
        serverUrlField.setDisable(loading);
        emailField.setDisable(loading);
        passwordField.setDisable(loading);
        rememberMeCheckbox.setDisable(loading);
    }
    
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
    
    private void navigateToDeviceSelection() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/device-selection.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Device Selection - Scrap MS Device Bridge");
            
        } catch (IOException e) {
            logger.error("Failed to load device selection screen", e);
            showError("Failed to load next screen");
        }
    }
    
    private void navigateToMonitoring() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/monitoring.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Monitoring - Scrap MS Device Bridge");
            stage.setMaximized(true);
            
        } catch (IOException e) {
            logger.error("Failed to load monitoring screen", e);
            showError("Failed to load monitoring screen");
        }
    }
}
