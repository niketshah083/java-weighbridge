package com.scrapms.devicebridge.service;

import com.scrapms.devicebridge.model.CameraConfig;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import okhttp3.*;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.JavaFXFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Optimized Camera Service for RTSP streaming and snapshot capture
 * 
 * Performance optimizations:
 * - Fixed thread pool to prevent resource exhaustion
 * - Frame skipping under load
 * - Reduced JPEG quality for live preview (higher for snapshots)
 * - Optimized FFmpeg settings for lower latency
 * - Reusable converters and buffers
 * - Adaptive frame rate based on system load
 */
public class CameraService {
    
    private static final Logger logger = LoggerFactory.getLogger(CameraService.class);
    
    // Use fixed thread pool instead of cached to prevent resource exhaustion
    // Max 6 threads: 3 cameras * 2 (stream + snapshot)
    private static final int MAX_CAMERA_THREADS = 6;
    
    private final Map<Integer, FFmpegFrameGrabber> grabbers = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> activeStreams = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> capturingInProgress = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(MAX_CAMERA_THREADS, r -> {
        Thread t = new Thread(r, "CameraService-Worker");
        t.setDaemon(true);
        return t;
    });
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    
    // Preview settings - lower quality for performance
    private static final int PREVIEW_WIDTH = 480;
    private static final int PREVIEW_HEIGHT = 270;
    private static final float PREVIEW_JPEG_QUALITY = 0.4f; // 40% quality for preview
    
    // Snapshot settings - higher quality
    private static final float SNAPSHOT_JPEG_QUALITY = 0.85f; // 85% quality for snapshots
    
    // Frame skip - only process every Nth frame from camera
    private static final int FRAME_SKIP = 3; // Process every 3rd frame
    
    /**
     * Capture snapshot from camera (high quality)
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
                
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    captureHttpSnapshot(cameraId, config, callback);
                } else if (url.startsWith("rtsp://")) {
                    captureRtspSnapshot(cameraId, config, callback);
                } else {
                    callback.accept(new CaptureResult(false, "Unsupported URL protocol", null));
                }
                
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
            logger.info("[HTTP] Capturing snapshot from camera {}", cameraId);
            
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
                    
                    logger.info("[HTTP] Snapshot captured from camera {}", cameraId);
                    callback.accept(new CaptureResult(true, null, dataUrl));
                } else {
                    callback.accept(new CaptureResult(false, "HTTP " + response.code(), null));
                }
            }
            
        } catch (Exception e) {
            logger.error("[HTTP] Error capturing snapshot", e);
            callback.accept(new CaptureResult(false, e.getMessage(), null));
        }
    }
    
    private void captureRtspSnapshot(int cameraId, CameraConfig config, Consumer<CaptureResult> callback) {
        FFmpegFrameGrabber grabber = null;
        try {
            String url = buildRtspUrl(config);
            logger.info("[RTSP] Capturing snapshot from camera {}", cameraId);
            
            grabber = createOptimizedGrabber(url, config);
            grabber.start();
            
            Frame frame = null;
            Java2DFrameConverter converter = new Java2DFrameConverter();
            
            // Try to get a valid frame (skip first few which might be empty)
            for (int i = 0; i < 30 && frame == null; i++) {
                frame = grabber.grabImage();
                if (frame != null && frame.image != null) break;
                frame = null;
            }
            
            if (frame != null && frame.image != null) {
                BufferedImage bufferedImage = converter.convert(frame);
                
                if (bufferedImage != null) {
                    // Use high quality for snapshots
                    byte[] imageBytes = encodeJpeg(bufferedImage, SNAPSHOT_JPEG_QUALITY);
                    String base64 = Base64.getEncoder().encodeToString(imageBytes);
                    String dataUrl = "data:image/jpeg;base64," + base64;
                    
                    logger.info("[RTSP] Snapshot captured from camera {} ({} bytes)", cameraId, imageBytes.length);
                    callback.accept(new CaptureResult(true, null, dataUrl));
                } else {
                    callback.accept(new CaptureResult(false, "Failed to convert frame", null));
                }
            } else {
                callback.accept(new CaptureResult(false, "No video frame available", null));
            }
            
            grabber.stop();
            
        } catch (Exception e) {
            logger.error("[RTSP] Error capturing snapshot", e);
            callback.accept(new CaptureResult(false, e.getMessage(), null));
        } finally {
            if (grabber != null) {
                try { grabber.release(); } catch (Exception e) { /* ignore */ }
            }
        }
    }
    
    /**
     * Start live preview with optimized settings
     */
    public void startLivePreview(int cameraId, CameraConfig config, 
                                Consumer<Image> onFrame, Consumer<String> onBase64Frame, int targetFps) {
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
                    streamRtsp(cameraId, config, onFrame, onBase64Frame, targetFps);
                } else if (url.startsWith("http://") || url.startsWith("https://")) {
                    streamHttp(cameraId, config, onFrame, onBase64Frame, targetFps);
                }
                
            } catch (Exception e) {
                logger.error("Error in live preview for camera {}", cameraId, e);
            } finally {
                grabbers.remove(cameraId);
                activeStreams.remove(cameraId);
            }
        });
    }
    
    private void streamRtsp(int cameraId, CameraConfig config, 
                           Consumer<Image> onFrame, Consumer<String> onBase64Frame, int targetFps) {
        String url = buildRtspUrl(config);
        logger.info("[RTSP] Starting optimized stream for camera {} at {} fps", cameraId, targetFps);
        
        int retryCount = 0;
        int maxRetries = 999; // Keep retrying indefinitely
        
        while (retryCount < maxRetries && activeStreams.getOrDefault(cameraId, false)) {
            FFmpegFrameGrabber grabber = null;
            try {
                grabber = createOptimizedGrabber(url, config);
                grabber.start();
                grabbers.put(cameraId, grabber);
                
                // Use JavaFX converter for direct conversion (faster than Java2D)
                JavaFXFrameConverter fxConverter = new JavaFXFrameConverter();
                Java2DFrameConverter java2dConverter = null;
                
                long frameDelayMs = 1000 / targetFps;
                long lastFrameTime = 0;
                long lastSuccessfulFrame = System.currentTimeMillis();
                int frameCount = 0;
                int skipCount = 0;
                int grabCount = 0;
                int consecutiveNullFrames = 0;
                long lastLogTime = System.currentTimeMillis();
                
                // Timeout - if no frame for 5 seconds, reconnect (reduced from 10s)
                final long FRAME_TIMEOUT_MS = 5000;
                // Max consecutive null frames before forcing reconnect
                final int MAX_CONSECUTIVE_NULL_FRAMES = 150;
                
                ByteArrayOutputStream baos = onBase64Frame != null ? new ByteArrayOutputStream(30000) : null;
                
                logger.info("[RTSP] Stream started for camera {}", cameraId);
                retryCount = 0; // Reset retry count on successful connection
                
                while (activeStreams.getOrDefault(cameraId, false)) {
                    Frame frame = null;
                    try {
                        // Use timeout for grabImage to prevent indefinite blocking
                        frame = grabber.grabImage();
                    } catch (org.bytedeco.javacv.FFmpegFrameGrabber.Exception e) {
                        // FFmpeg specific error - likely stream issue, force reconnect
                        logger.warn("[RTSP] FFmpeg error grabbing frame, forcing reconnect: {}", e.getMessage());
                        break;
                    } catch (Exception e) {
                        logger.warn("[RTSP] Error grabbing frame: {}", e.getMessage());
                        consecutiveNullFrames++;
                    }
                    
                    if (frame != null && frame.image != null) {
                        consecutiveNullFrames = 0;
                        lastSuccessfulFrame = System.currentTimeMillis();
                        grabCount++;
                        
                        // Skip frames to reduce CPU load
                        if (grabCount % FRAME_SKIP != 0) {
                            continue;
                        }
                        
                        long currentTime = System.currentTimeMillis();
                        
                        // Frame rate limiting
                        if (currentTime - lastFrameTime >= frameDelayMs) {
                            lastFrameTime = currentTime;
                            frameCount++;
                            
                            // Convert directly to JavaFX Image
                            if (onFrame != null) {
                                try {
                                    Image fxImage = fxConverter.convert(frame);
                                    if (fxImage != null) {
                                        onFrame.accept(fxImage);
                                    }
                                } catch (Exception e) {
                                    logger.debug("Error converting frame: {}", e.getMessage());
                                }
                            }
                            
                            // Only encode to base64 if callback is provided
                            if (onBase64Frame != null && baos != null) {
                                if (java2dConverter == null) {
                                    java2dConverter = new Java2DFrameConverter();
                                }
                                BufferedImage bufferedImage = java2dConverter.convert(frame);
                                if (bufferedImage != null) {
                                    BufferedImage resized = resizeIfNeeded(bufferedImage, PREVIEW_WIDTH, PREVIEW_HEIGHT);
                                    baos.reset();
                                    encodeJpegToStream(resized, PREVIEW_JPEG_QUALITY, baos);
                                    String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                                    onBase64Frame.accept("data:image/jpeg;base64," + base64);
                                }
                            }
                        } else {
                            skipCount++;
                        }
                        
                        // Log stats every 10 seconds
                        if (currentTime - lastLogTime >= 10000) {
                            double actualFps = frameCount / ((currentTime - lastLogTime) / 1000.0);
                            logger.info("[RTSP] Camera {}: {:.1f} fps, {} frames processed", 
                                    cameraId, actualFps, frameCount);
                            frameCount = 0;
                            skipCount = 0;
                            lastLogTime = currentTime;
                        }
                    } else {
                        consecutiveNullFrames++;
                        
                        // Check for timeout based on time
                        long timeSinceLastFrame = System.currentTimeMillis() - lastSuccessfulFrame;
                        if (timeSinceLastFrame > FRAME_TIMEOUT_MS) {
                            logger.warn("[RTSP] Camera {} stream stalled for {}ms, forcing reconnect...", 
                                    cameraId, timeSinceLastFrame);
                            break; // Exit inner loop to reconnect
                        }
                        
                        // Also check consecutive null frames (catches frozen stream faster)
                        if (consecutiveNullFrames > MAX_CONSECUTIVE_NULL_FRAMES) {
                            logger.warn("[RTSP] Camera {} received {} consecutive null frames, forcing reconnect...", 
                                    cameraId, consecutiveNullFrames);
                            break; // Exit inner loop to reconnect
                        }
                        
                        // Small sleep when no frame available
                        if (consecutiveNullFrames > 10) {
                            Thread.sleep(10);
                        }
                    }
                }
                
                // Force cleanup of grabber resources
                logger.info("[RTSP] Cleaning up grabber for camera {}", cameraId);
                grabbers.remove(cameraId);
                try { 
                    grabber.stop(); 
                } catch (Exception e) { 
                    logger.debug("Error stopping grabber: {}", e.getMessage());
                }
                try { 
                    grabber.release(); 
                } catch (Exception e) { 
                    logger.debug("Error releasing grabber: {}", e.getMessage());
                }
                grabber = null;
                
                logger.info("[RTSP] Stream stopped for camera {}", cameraId);
                
                // If we exited due to timeout, continue to retry
                if (!activeStreams.getOrDefault(cameraId, false)) {
                    break; // User stopped the stream
                }
                
            } catch (Exception e) {
                retryCount++;
                logger.warn("[RTSP] Stream error for camera {} (attempt {}): {}", 
                        cameraId, retryCount, e.getMessage());
                
                if (grabber != null) {
                    grabbers.remove(cameraId);
                    try { grabber.stop(); } catch (Exception ex) { /* ignore */ }
                    try { grabber.release(); } catch (Exception ex) { /* ignore */ }
                    grabber = null;
                }
            }
            
            // Wait before reconnecting (shorter delay for faster recovery)
            if (activeStreams.getOrDefault(cameraId, false)) {
                try { 
                    logger.info("[RTSP] Reconnecting camera {} in 1 second...", cameraId);
                    Thread.sleep(1000); 
                } catch (InterruptedException ie) { 
                    break; 
                }
            }
        }
        
        logger.info("[RTSP] Stream thread exiting for camera {}", cameraId);
    }
    
    private void streamHttp(int cameraId, CameraConfig config, 
                           Consumer<Image> onFrame, Consumer<String> onBase64Frame, int targetFps) {
        String url = config.getEffectiveUrl();
        logger.info("[HTTP] Starting HTTP polling for camera {} at {} fps", cameraId, targetFps);
        
        long frameDelayMs = 1000 / targetFps;
        
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
                
                Thread.sleep(frameDelayMs);
                
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                logger.debug("[HTTP] Error fetching frame: {}", e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
            }
        }
        
        logger.info("[HTTP] Stopped polling for camera {}", cameraId);
    }
    
    /**
     * Create an optimized FFmpeg grabber with performance settings
     */
    private FFmpegFrameGrabber createOptimizedGrabber(String url, CameraConfig config) {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(url);
        
        // Transport protocol (tcp is more reliable, udp is faster)
        grabber.setOption("rtsp_transport", config.getTransport());
        
        // Connection timeout (in microseconds)
        grabber.setOption("stimeout", String.valueOf(config.getTimeout() * 1000000));
        
        // ===== CRITICAL: READ TIMEOUT TO PREVENT FREEZE =====
        // This prevents grabImage() from blocking indefinitely when stream stalls
        grabber.setOption("timeout", "5000000"); // 5 second read timeout (microseconds)
        grabber.setOption("rw_timeout", "5000000"); // 5 second read/write timeout
        
        // ===== PERFORMANCE OPTIMIZATIONS =====
        
        // Reduce buffer size for lower latency
        grabber.setOption("buffer_size", "524288"); // 512KB buffer
        
        // Reduce analysis duration for faster startup
        grabber.setOption("analyzeduration", "500000"); // 0.5 second
        grabber.setOption("probesize", "500000"); // 500KB probe
        
        // Disable audio
        grabber.setAudioChannels(0);
        
        // Use single thread for decoding
        grabber.setVideoOption("threads", "1");
        
        // Low latency flags - added genpts to handle timestamp issues
        grabber.setOption("fflags", "nobuffer+discardcorrupt+genpts");
        grabber.setOption("flags", "low_delay");
        
        // Reduce frame queue
        grabber.setOption("max_delay", "100000"); // 0.1 second max delay
        
        // Reconnect on error (for network issues)
        grabber.setOption("reconnect", "1");
        grabber.setOption("reconnect_streamed", "1");
        grabber.setOption("reconnect_delay_max", "2"); // Max 2 seconds between reconnects
        
        // Request scaled down output - this reduces CPU significantly
        grabber.setImageWidth(480);
        grabber.setImageHeight(270);
        
        return grabber;
    }
    
    /**
     * Resize image if larger than target dimensions
     */
    private BufferedImage resizeIfNeeded(BufferedImage original, int maxWidth, int maxHeight) {
        int origWidth = original.getWidth();
        int origHeight = original.getHeight();
        
        if (origWidth <= maxWidth && origHeight <= maxHeight) {
            return original;
        }
        
        // Calculate scale to fit within bounds
        double scale = Math.min((double) maxWidth / origWidth, (double) maxHeight / origHeight);
        int newWidth = (int) (origWidth * scale);
        int newHeight = (int) (origHeight * scale);
        
        // Use fast scaling algorithm
        java.awt.Image scaled = original.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_FAST);
        
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();
        
        return resized;
    }
    
    /**
     * Encode BufferedImage to JPEG with specified quality
     */
    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        encodeJpegToStream(image, quality, baos);
        return baos.toByteArray();
    }
    
    /**
     * Encode BufferedImage to JPEG stream with specified quality (reusable stream)
     */
    private void encodeJpegToStream(BufferedImage image, float quality, ByteArrayOutputStream baos) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            // Fallback to standard ImageIO
            ImageIO.write(image, "jpg", baos);
            return;
        }
        
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }
    
    /**
     * Stop live preview for a camera
     */
    public void stopLivePreview(int cameraId) {
        logger.info("Stopping live preview for camera {}", cameraId);
        activeStreams.put(cameraId, false);
        
        FFmpegFrameGrabber grabber = grabbers.remove(cameraId);
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                logger.warn("Error stopping grabber for camera {}: {}", cameraId, e.getMessage());
            }
        }
    }
    
    /**
     * Stop all camera previews
     */
    public void stopAllPreviews() {
        logger.info("Stopping all camera previews...");
        activeStreams.keySet().forEach(id -> activeStreams.put(id, false));
        new java.util.ArrayList<>(grabbers.keySet()).forEach(this::stopLivePreview);
        logger.info("All camera previews stopped");
    }
    
    /**
     * Build RTSP URL with credentials
     */
    private String buildRtspUrl(CameraConfig config) {
        String url = config.getRtspUrl();
        
        if (config.getUsername() != null && config.getPassword() != null) {
            if (url.startsWith("rtsp://")) {
                return String.format("rtsp://%s:%s@%s", 
                        config.getUsername(), 
                        config.getPassword(), 
                        url.substring(7));
            }
        }
        
        return url;
    }
    
    /**
     * Shutdown service and cleanup resources
     */
    public void shutdown() {
        logger.info("Shutting down camera service...");
        
        stopAllPreviews();
        activeStreams.clear();
        capturingInProgress.clear();
        grabbers.clear();
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        logger.info("Camera service shutdown complete");
    }
    
    /**
     * Capture Result
     */
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
