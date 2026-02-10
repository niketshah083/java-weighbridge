package com.scrapms.devicebridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrapms.devicebridge.model.CameraConfig;
import com.scrapms.devicebridge.model.CameraMaster;
import com.scrapms.devicebridge.model.WeighbridgeConfig;
import com.scrapms.devicebridge.model.WeighbridgeMaster;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for making API calls to backend
 */
public class ApiService {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiService.class);
    
    private final ConfigService configService;
    private final AuthService authService;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public ApiService(ConfigService configService, AuthService authService) {
        this.configService = configService;
        this.authService = authService;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Get all weighbridge masters
     */
    public CompletableFuture<List<WeighbridgeMaster>> getWeighbridges() {
        String url = configService.getServerUrl() + "/api/weighbridge/masters";
        return makeGetRequest(url, new TypeReference<List<WeighbridgeMaster>>() {});
    }
    
    /**
     * Get all camera masters
     */
    public CompletableFuture<List<CameraMaster>> getCameras() {
        String url = configService.getServerUrl() + "/api/camera/masters";
        return makeGetRequest(url, new TypeReference<List<CameraMaster>>() {});
    }
    
    /**
     * Get weighbridge config by master ID
     */
    public CompletableFuture<WeighbridgeConfig> getWeighbridgeConfig(int masterId) {
        String url = configService.getServerUrl() + "/api/weighbridge/configs/master/" + masterId;
        return makeGetRequest(url, new TypeReference<WeighbridgeConfig>() {});
    }
    
    /**
     * Get camera config by master ID
     */
    public CompletableFuture<CameraConfig> getCameraConfig(int masterId) {
        String url = configService.getServerUrl() + "/api/camera/configs/master/" + masterId;
        return makeGetRequest(url, new TypeReference<CameraConfig>() {});
    }
    
    /**
     * Make GET request
     */
    private <T> CompletableFuture<T> makeGetRequest(String url, TypeReference<T> typeRef) {
        CompletableFuture<T> future = new CompletableFuture<>();
        
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + authService.getToken())
                    .get()
                    .build();
            
            logger.debug("GET request: {}", url);
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    logger.error("Request failed: {}", url, e);
                    future.completeExceptionally(e);
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        String responseStr = responseBody != null ? responseBody.string() : "";
                        
                        if (response.isSuccessful()) {
                            JsonNode jsonNode = objectMapper.readTree(responseStr);
                            
                            if (jsonNode.has("success") && jsonNode.get("success").asBoolean()) {
                                JsonNode dataNode = jsonNode.get("data");
                                T result = objectMapper.convertValue(dataNode, typeRef);
                                logger.debug("Request successful: {}", url);
                                future.complete(result);
                            } else {
                                String message = jsonNode.has("message") ? 
                                        jsonNode.get("message").asText() : "Request failed";
                                logger.warn("Request failed: {} - {}", url, message);
                                future.completeExceptionally(new RuntimeException(message));
                            }
                        } else {
                            logger.warn("Request failed with status {}: {}", response.code(), url);
                            future.completeExceptionally(new RuntimeException("HTTP " + response.code()));
                        }
                    } catch (Exception e) {
                        logger.error("Error parsing response: {}", url, e);
                        future.completeExceptionally(e);
                    }
                }
            });
            
        } catch (Exception e) {
            logger.error("Error creating request: {}", url, e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
}
