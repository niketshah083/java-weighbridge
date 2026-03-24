package com.scrapms.devicebridge.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing application configuration
 */
public class ConfigService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    private static final String APP_NAME = "ScrapMS";
    private static final String APP_FOLDER = "DeviceBridge";
    private static final String CONFIG_FILE = "config.json";
    
    private static ConfigService instance;
    private final Gson gson;
    private final Path configPath;
    private AppConfig config;
    
    private ConfigService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.configPath = getConfigPath();
        this.config = loadConfig();
    }
    
    public static synchronized ConfigService getInstance() {
        if (instance == null) {
            instance = new ConfigService();
        }
        return instance;
    }
    
    /**
     * Get platform-specific config directory
     */
    private Path getConfigPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");
        Path configDir;
        
        if (os.contains("win")) {
            // Windows: %APPDATA%/ScrapMS/DeviceBridge
            String appData = System.getenv("APPDATA");
            configDir = Paths.get(appData != null ? appData : userHome, APP_NAME, APP_FOLDER);
        } else if (os.contains("mac")) {
            // macOS: ~/Library/Application Support/ScrapMS/DeviceBridge
            configDir = Paths.get(userHome, "Library", "Application Support", APP_NAME, APP_FOLDER);
        } else {
            // Linux: ~/.config/ScrapMS/DeviceBridge
            configDir = Paths.get(userHome, ".config", APP_NAME, APP_FOLDER);
        }
        
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            logger.error("Failed to create config directory", e);
        }
        
        return configDir.resolve(CONFIG_FILE);
    }
    
    /**
     * Load configuration from file
     */
    private AppConfig loadConfig() {
        if (Files.exists(configPath)) {
            try (FileReader reader = new FileReader(configPath.toFile())) {
                AppConfig loaded = gson.fromJson(reader, AppConfig.class);
                if (loaded != null) {
                    logger.info("Configuration loaded from: {}", configPath);
                    return loaded;
                }
            } catch (Exception e) {
                logger.error("Failed to load configuration (corrupted file?): {}", e.getMessage());
                // Backup corrupted file and create new one
                try {
                    Path backupPath = configPath.resolveSibling(CONFIG_FILE + ".corrupted");
                    Files.move(configPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Corrupted config backed up to: {}", backupPath);
                } catch (IOException ex) {
                    logger.warn("Could not backup corrupted config: {}", ex.getMessage());
                    try {
                        Files.deleteIfExists(configPath);
                    } catch (IOException ignored) {}
                }
            }
        }
        
        logger.info("Creating new configuration");
        return new AppConfig();
    }
    
    /**
     * Save configuration to file
     */
    private void saveConfig() {
        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            gson.toJson(config, writer);
            logger.info("Configuration saved to: {}", configPath);
        } catch (IOException e) {
            logger.error("Failed to save configuration", e);
        }
    }
    
    // Server URL
    public String getServerUrl() {
        return config.serverUrl;
    }
    
    public void setServerUrl(String serverUrl) {
        config.serverUrl = serverUrl;
        saveConfig();
    }
    
    // Remember Me
    public boolean getRememberMe() {
        return config.rememberMe;
    }
    
    public void setRememberMe(boolean rememberMe) {
        config.rememberMe = rememberMe;
        saveConfig();
    }
    
    // Saved Email
    public String getSavedEmail() {
        return config.savedEmail;
    }
    
    public void setSavedEmail(String email) {
        config.savedEmail = email;
        saveConfig();
    }
    
    // Saved Password (Base64 encoded)
    public String getSavedPassword() {
        if (config.savedPassword == null || config.savedPassword.isEmpty()) return "";
        try {
            return new String(java.util.Base64.getDecoder().decode(config.savedPassword));
        } catch (Exception e) {
            return "";
        }
    }
    
    public void setSavedPassword(String password) {
        if (password == null || password.isEmpty()) {
            config.savedPassword = "";
        } else {
            config.savedPassword = java.util.Base64.getEncoder().encodeToString(password.getBytes());
        }
        saveConfig();
    }
    
    // Auto Connect (skip login + device selection)
    public boolean getAutoConnect() {
        return config.autoConnect;
    }
    
    public void setAutoConnect(boolean autoConnect) {
        config.autoConnect = autoConnect;
        saveConfig();
    }
    
    // Token
    public String getToken() {
        return config.token;
    }
    
    public void setToken(String token) {
        config.token = token;
        saveConfig();
    }
    
    // User Info
    public UserInfo getUser() {
        return config.user;
    }
    
    public void setUser(UserInfo user) {
        config.user = user;
        saveConfig();
    }
    
    // Selected Devices
    public List<Integer> getSelectedWeighbridges() {
        return config.selectedWeighbridges;
    }
    
    public void setSelectedWeighbridges(List<Integer> ids) {
        config.selectedWeighbridges = ids;
        saveConfig();
    }
    
    public List<Integer> getSelectedCameras() {
        return config.selectedCameras;
    }
    
    public void setSelectedCameras(List<Integer> ids) {
        config.selectedCameras = ids;
        saveConfig();
    }
    
    // Debug Mode
    public boolean getDebugMode() {
        return config.debugMode;
    }
    
    public void setDebugMode(boolean debugMode) {
        config.debugMode = debugMode;
        saveConfig();
    }
    
    // Debug Weight Range
    public WeightRange getDebugWeightRange() {
        return config.debugWeightRange;
    }
    
    public void setDebugWeightRange(WeightRange range) {
        config.debugWeightRange = range;
        saveConfig();
    }
    
    // Clear auth data
    public void clearAuth() {
        config.token = null;
        config.user = null;
        config.autoConnect = false;
        if (!config.rememberMe) {
            config.savedEmail = null;
            config.savedPassword = "";
        }
        saveConfig();
    }
    
    /**
     * Application Configuration Model
     */
    public static class AppConfig {
        private String serverUrl = "https://scrapapi.accomation.io";
        private boolean rememberMe = false;
        private String savedEmail = "";
        private String savedPassword = "";
        private String token = null;
        private UserInfo user = null;
        private List<Integer> selectedWeighbridges = new ArrayList<>();
        private List<Integer> selectedCameras = new ArrayList<>();
        private boolean debugMode = false;
        private WeightRange debugWeightRange = new WeightRange(100, 10000);
        private boolean autoConnect = false;
    }
    
    /**
     * User Information Model
     */
    public static class UserInfo {
        private Integer id;
        private String email;
        private String name;
        private Integer tenantId;
        
        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public Integer getTenantId() { return tenantId; }
        public void setTenantId(Integer tenantId) { this.tenantId = tenantId; }
    }
    
    /**
     * Weight Range Model
     */
    public static class WeightRange {
        private double min;
        private double max;
        
        public WeightRange() {
            this(100, 10000);
        }
        
        public WeightRange(double min, double max) {
            this.min = min;
            this.max = max;
        }
        
        public double getMin() { return min; }
        public void setMin(double min) { this.min = min; }
        
        public double getMax() { return max; }
        public void setMax(double max) { this.max = max; }
    }
}
