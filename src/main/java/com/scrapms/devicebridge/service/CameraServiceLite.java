package com.scrapms.devicebridge.service;

import com.scrapms.devicebridge.model.CameraConfig;
import javafx.scene.image.Image;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Lightweight Camera Service - HTTP snapshots only (no RTSP/FFmpeg)
 * Use this version for a much smaller JAR (~5MB vs ~42MB)
 */
public class CameraServiceLite {
    
    private static final Logger logger = LoggerFactory.getLogger(CameraServiceLite.class);
    
    private final Map<Integer, Boolean> activeStreams = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> capturingInProgress = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final OkHttpClient httpClient = new OkHttpClient();
    
    /**
     * Capture snapshot from camera (HTTP only)
     */
    public void captureSnapshot(int cameraId, CameraConfig config, Consumer<CaptureResult> callback) {
        if (capturingInProgress.getOrDefault(cameraId, false)) {
            logger.debug("Capture already in progress for camera {}, skipping", cameraId);
            return;
        }
        
        executor.submit(() -> {
            capturingInProgress.put(cameraId, true);
            
            try {
                String url = config.getEffectiveUrl();
                
                if (url == null || url.trim().isEmpty()) {
                    callback.accept(new CaptureResult(false, "No camera URL configured", null));
                    return;
                }
                
                if (url.startsWith("rtsp://")) {
                    callback.accept(new CaptureResult(false, 
                        "RTSP not supported in lightweight mode. Use HTTP snapshot URL.", null));
                    return;
                }
                
                captureHttpSnapshot(cameraId, config, callback);
                
            } catch (Exception e) {
                logger.error("Error capturing snapshot from camera {}", cameraId, e);
                callback.accept(new CaptureResult(false, e.getMessage(), null));
            } finally {
                capturingInProgress.put(cameraId, false);
            }
        });
    }
    
    private void captureHttpSnapshot(int cameraId, CameraConfig config, Consumer<CaptureResult> callback) {
        try {
            String url = config.getEffectiveUrl();
            logger.debug("[HTTP] Capturing snapshot from camera {}: {}", cameraId, url);
            
            Request.Builder requestBuilder = new Request.Builder().url(url);
            
            if (config.getUsername() != null && config.getPassword() != null) {
                String credentials = Credentials.basic(config.getUsername(), config.getPassword());
                requestBuilder.addHeader("Authorization", credentials);
            }
            
            try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    byte[] imageBytes = response.body().bytes();
                    String base64 = Base64.getEncoder().encodeToString(imageBytes);
                    String dataUrl = "data:image/jpeg;base64," + base64;
                    
                    logger.debug("[HTTP] Snapshot captured from camera {}", cameraId);
                    callback.accept(new CaptureResult(true, null, dataUrl));
                } else {
                    logger.error("[HTTP] Failed to capture snapshot: {}", response.code());
                    callback.accept(new CaptureResult(false, "HTTP " + response.code(), null));
                }
            }
            
        } catch (Exception e) {
            logger.error("[HTTP] Error capturing snapshot", e);
            callback.accept(new CaptureResult(false, e.getMessage(), null));
        }
    }
    
    /**
     * Start live preview using HTTP polling
     */
    public void startLivePreview(int cameraId, CameraConfig config, 
                                Consumer<Image> onFrame, Consumer<String> onBase64Frame, int fps) {
        stopLivePreview(cameraId);
        activeStreams.put(cameraId, true);
        
        executor.submit(() -> {
            try {
                String url = config.getEffectiveUrl();
                
                if (url == null || url.trim().isEmpty()) {
                    logger.error("No URL configured for camera {}", cameraId);
                    return;
                }
                
                if (url.startsWith("rtsp://")) {
                    logger.error("RTSP not supported in lightweight mode for camera {}", cameraId);
                    return;
                }
                
                logger.info("[HTTP] Starting HTTP polling for camera {} at {} fps", cameraId, fps);
                long frameDelay = 1000 / fps;
                
                while (activeStreams.getOrDefault(cameraId, false)) {
                    try {
                        Request.Builder requestBuilder = new Request.Builder().url(url);
                        if (config.getUsername() != null && config.getPassword() != null) {
                            String credentials = Credentials.basic(config.getUsername(), config.getPassword());
                            requestBuilder.addHeader("Authorization", credentials);
                        }
                        
                        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
                            if (response.isSuccessful() && response.body() != null) {
                                byte[] imageBytes = response.body().bytes();
                                
                                if (onFrame != null) {
                                    Image fxImage = new Image(new ByteArrayInputStream(imageBytes));
                                    onFrame.accept(fxImage);
                                }
                                
                                if (onBase64Frame != null) {
                                    String base64 = Base64.getEncoder().encodeToString(imageBytes);
                                    String dataUrl = "data:image/jpeg;base64," + base64;
                                    onBase64Frame.accept(dataUrl);
                                }
                            }
                        }
                        
                        Thread.sleep(frameDelay);
                        
                    } catch (Exception e) {
                        logger.debug("[HTTP] Error fetching frame: {}", e.getMessage());
                        Thread.sleep(1000);
                    }
                }
                
                logger.info("[HTTP] Stopped polling for camera {}", cameraId);
                
            } catch (Exception e) {
                logger.error("Error in live preview for camera {}", cameraId, e);
            } finally {
                activeStreams.remove(cameraId);
            }
        });
    }
    
    public void stopLivePreview(int cameraId) {
        logger.info("Stopping live preview for camera {}", cameraId);
        activeStreams.put(cameraId, false);
    }
    
    public void stopAllPreviews() {
        logger.info("Stopping all camera previews...");
        activeStreams.keySet().forEach(id -> activeStreams.put(id, false));
        logger.info("All camera previews stopped");
    }
    
    public void shutdown() {
        logger.info("Shutting down camera service...");
        stopAllPreviews();
        activeStreams.clear();
        capturingInProgress.clear();
        executor.shutdownNow();
        logger.info("Camera service shutdown complete");
    }
    
    public static class CaptureResult {
        private final boolean success;
        private final String error;
        private final String imageData;
        
        public CaptureResult(boolean success, String error, String imageData) {
            this.success = success;
            this.error = error;
            this.imageData = imageData;
        }
        
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public String getImageData() { return imageData; }
    }
}
