package com.scrapms.devicebridge.service;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP Listener for receiving ANPR events from cameras.
 * 
 * Configure your camera's HTTP Notification/Alarm Server to send events to:
 * http://YOUR_COMPUTER_IP:8085/anpr
 * 
 * Supports various formats:
 * - JSON: {"plateNumber": "MH12AB1234", ...}
 * - XML: <PlateNumber>MH12AB1234</PlateNumber>
 * - Form data: plateNumber=MH12AB1234
 */
public class AnprHttpListener {
    private static final Logger logger = LoggerFactory.getLogger(AnprHttpListener.class);
    
    private HttpServer server;
    private final int port;
    private Consumer<String> plateCallback;
    private boolean running = false;
    
    public AnprHttpListener(int port) {
        this.port = port;
    }
    
    public void setPlateCallback(Consumer<String> callback) {
        this.plateCallback = callback;
    }
    
    /**
     * Start the HTTP listener
     */
    public void start() throws IOException {
        if (running) {
            logger.warn("ANPR HTTP Listener already running on port {}", port);
            return;
        }
        
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        
        // Main ANPR endpoint
        server.createContext("/anpr", new AnprHandler());
        server.createContext("/anpr/notification", new AnprHandler());
        server.createContext("/onvif/notification", new AnprHandler());
        
        // Health check endpoint
        server.createContext("/health", exchange -> {
            String response = "OK";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });
        
        server.start();
        running = true;
        
        logger.info("ANPR HTTP Listener started on port {}", port);
        logger.info("Configure your camera to send ANPR events to: http://YOUR_IP:{}/anpr", port);
    }
    
    /**
     * Stop the HTTP listener
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            running = false;
            logger.info("ANPR HTTP Listener stopped");
        }
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public int getPort() {
        return port;
    }
    
    /**
     * Handler for ANPR events
     */
    private class AnprHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            
            logger.info("Received {} request on {}", method, path);
            
            try {
                // Read request body
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                if (!body.isEmpty()) {
                    logger.info("ANPR Event received ({} bytes): {}", body.length(), 
                        body.length() > 500 ? body.substring(0, 500) + "..." : body);
                    
                    // Extract plate number from various formats
                    String plate = extractPlateNumber(body);
                    
                    if (plate != null && !plate.isEmpty()) {
                        logger.info("Detected License Plate: {}", plate);
                        
                        if (plateCallback != null) {
                            plateCallback.accept(plate);
                        }
                    }
                }
                
                // Send success response
                String response = "<?xml version=\"1.0\"?><response><status>OK</status></response>";
                exchange.getResponseHeaders().add("Content-Type", "application/xml");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                
            } catch (Exception e) {
                logger.error("Error processing ANPR event", e);
                exchange.sendResponseHeaders(500, 0);
                exchange.getResponseBody().close();
            }
        }
    }
    
    /**
     * Extract plate number from various formats (JSON, XML, form data)
     */
    private String extractPlateNumber(String content) {
        if (content == null || content.isEmpty()) return null;
        
        // Common field names for license plates
        String[] plateFields = {
            "plateNumber", "PlateNumber", "plate_number", "licensePlate", "LicensePlate",
            "license_plate", "plateNo", "PlateNo", "plate_no", "vehiclePlate", "VehiclePlate",
            "numberPlate", "NumberPlate", "regNumber", "RegNumber", "plateResult", "PlateResult"
        };
        
        for (String field : plateFields) {
            // JSON format: "plateNumber": "MH12AB1234" or "plateNumber":"MH12AB1234"
            Pattern jsonPattern = Pattern.compile(
                "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"",
                Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = jsonPattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            
            // XML format: <PlateNumber>MH12AB1234</PlateNumber>
            Pattern xmlPattern = Pattern.compile(
                "<" + field + "[^>]*>([^<]+)</" + field + ">",
                Pattern.CASE_INSENSITIVE
            );
            matcher = xmlPattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            
            // ONVIF SimpleItem format: Name="PlateNumber" Value="MH12AB1234"
            Pattern onvifPattern = Pattern.compile(
                "Name=[\"']" + field + "[\"']\\s+Value=[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE
            );
            matcher = onvifPattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            
            // Form data format: plateNumber=MH12AB1234
            Pattern formPattern = Pattern.compile(
                field + "=([^&\\s]+)",
                Pattern.CASE_INSENSITIVE
            );
            matcher = formPattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        
        // Try to find Indian license plate pattern directly
        Pattern indianPlate = Pattern.compile("[A-Z]{2}\\d{2}[A-Z]{1,2}\\d{4}");
        Matcher matcher = indianPlate.matcher(content.toUpperCase());
        if (matcher.find()) {
            return matcher.group();
        }
        
        return null;
    }
}
