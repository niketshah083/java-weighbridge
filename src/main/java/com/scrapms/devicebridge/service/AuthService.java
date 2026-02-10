package com.scrapms.devicebridge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling authentication
 */
public class AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final ConfigService configService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public AuthService(ConfigService configService) {
        this.configService = configService;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Login with email and password
     */
    public CompletableFuture<LoginResult> login(String email, String password) {
        CompletableFuture<LoginResult> future = new CompletableFuture<>();
        
        try {
            String serverUrl = configService.getServerUrl();
            String url = serverUrl + "/api/auth/login";
            
            // Create request body
            String json = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
            RequestBody body = RequestBody.create(json, JSON);
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();
            
            logger.info("Attempting login for user: {}", email);
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    logger.error("Login request failed", e);
                    future.complete(new LoginResult(false, "Connection failed: " + e.getMessage(), null, null));
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        String responseStr = responseBody != null ? responseBody.string() : "";
                        
                        logger.debug("Login response: {}", responseStr);
                        
                        if (response.isSuccessful()) {
                            JsonNode jsonNode = objectMapper.readTree(responseStr);
                            
                            if (jsonNode.has("success") && jsonNode.get("success").asBoolean()) {
                                JsonNode dataNode = jsonNode.get("data");
                                
                                // Extract token - check both "token" and "accessToken"
                                String token = null;
                                if (dataNode.has("token")) {
                                    token = dataNode.get("token").asText();
                                } else if (dataNode.has("accessToken")) {
                                    token = dataNode.get("accessToken").asText();
                                }
                                
                                if (token == null) {
                                    logger.error("No token found in response");
                                    future.complete(new LoginResult(false, "No token in response", null, null));
                                    return;
                                }
                                
                                // Extract user info
                                JsonNode userNode = dataNode.has("user") ? dataNode.get("user") : dataNode;
                                
                                ConfigService.UserInfo user = new ConfigService.UserInfo();
                                
                                // Safely extract user fields
                                if (userNode.has("id")) {
                                    user.setId(userNode.get("id").asInt());
                                }
                                if (userNode.has("email")) {
                                    user.setEmail(userNode.get("email").asText());
                                }
                                if (userNode.has("name")) {
                                    user.setName(userNode.get("name").asText());
                                }
                                if (userNode.has("tenantId")) {
                                    user.setTenantId(userNode.get("tenantId").asInt());
                                }
                                
                                // Save to config
                                configService.setToken(token);
                                configService.setUser(user);
                                
                                logger.info("Login successful for user: {}", email);
                                future.complete(new LoginResult(true, "Login successful", token, user));
                            } else {
                                String message = jsonNode.has("message") ? 
                                        jsonNode.get("message").asText() : "Login failed";
                                logger.warn("Login failed: {}", message);
                                future.complete(new LoginResult(false, message, null, null));
                            }
                        } else {
                            logger.warn("Login failed with status: {}", response.code());
                            future.complete(new LoginResult(false, "Login failed: " + response.message(), null, null));
                        }
                    } catch (Exception e) {
                        logger.error("Error parsing login response", e);
                        future.complete(new LoginResult(false, "Error: " + e.getMessage(), null, null));
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("Error creating login request", e);
            future.complete(new LoginResult(false, "Error: " + e.getMessage(), null, null));
        }
        
        return future;
    }
    
    /**
     * Logout
     */
    public void logout() {
        logger.info("Logging out user");
        configService.clearAuth();
    }
    
    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        return configService.getToken() != null && configService.getUser() != null;
    }
    
    /**
     * Get current auth token
     */
    public String getToken() {
        return configService.getToken();
    }
    
    /**
     * Get current user
     */
    public ConfigService.UserInfo getUser() {
        return configService.getUser();
    }
    
    /**
     * Get tenant ID
     */
    public Integer getTenantId() {
        ConfigService.UserInfo user = configService.getUser();
        return user != null ? user.getTenantId() : null;
    }
    
    /**
     * Login Result Model
     */
    public static class LoginResult {
        private final boolean success;
        private final String message;
        private final String token;
        private final ConfigService.UserInfo user;
        
        public LoginResult(boolean success, String message, String token, ConfigService.UserInfo user) {
            this.success = success;
            this.message = message;
            this.token = token;
            this.user = user;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getToken() { return token; }
        public ConfigService.UserInfo getUser() { return user; }
    }
}
