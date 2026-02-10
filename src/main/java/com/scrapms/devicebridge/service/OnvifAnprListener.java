package com.scrapms.devicebridge.service;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * ONVIF ANPR Listener - Receives license plate events from ONVIF-compatible cameras.
 * 
 * Configure your camera's VMS/Notification URL to:
 * http://your-computer-ip:8085/onvif/notification
 */
public class OnvifAnprListener {
    private static final Logger logger = LoggerFactory.getLogger(OnvifAnprListener.class);
    
    private HttpServer server;
    private int port;
    private Consumer<String> plateCallback;
    private boolean running = false;
    
    private static final String SOAP_RESPONSE = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<S:Envelope xmlns:S=\"http://www.w3.org/2003/05/soap-envelope\">" +
        "<S:Body/></S:Envelope>";
    
    public OnvifAnprListener(int port) {
        this.port = port;
    }
    
    /**
     * Set callback to receive detected license plates
     */
    public void setPlateCallback(Consumer<String> callback) {
        this.plateCallback = callback;
    }
    
    /**
     * Start the ONVIF notification listener
     */
    public void start() throws IOException {
        if (running) {
            logger.warn("ONVIF ANPR Listener already running on port {}", port);
            return;
        }
        
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/onvif/notification", new AnprNotificationHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        running = true;
        
        logger.info("ONVIF ANPR Listener started on port {}", port);
        logger.info("Configure camera notification URL to: http://YOUR_IP:{}/onvif/notification", port);
    }
    
    /**
     * Stop the listener
     */
    public void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            logger.info("ONVIF ANPR Listener stopped");
        }
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public int getPort() {
        return port;
    }
    
    /**
     * HTTP Handler for ONVIF ANPR notifications
     */
    private class AnprNotificationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                
                if ("POST".equalsIgnoreCase(method)) {
                    // Read the SOAP XML body
                    String soapEnvelope = readRequestBody(exchange);
                    
                    logger.debug("--- New ANPR Event Received ---");
                    logger.debug("Raw XML: {}", soapEnvelope);
                    
                    // Extract license plate from various formats
                    String plate = extractPlate(soapEnvelope);
                    
                    if (plate != null && !plate.isEmpty() && !"Unknown".equals(plate)) {
                        logger.info("ONVIF ANPR - Detected Plate: {}", plate);
                        
                        // Notify callback
                        if (plateCallback != null) {
                            plateCallback.accept(plate);
                        }
                    }
                    
                    // Send SOAP response (ONVIF requires 200 OK)
                    sendResponse(exchange, 200, SOAP_RESPONSE, "application/soap+xml");
                    
                } else if ("GET".equalsIgnoreCase(method)) {
                    // Health check endpoint
                    String response = "{\"status\":\"running\",\"service\":\"ONVIF ANPR Listener\"}";
                    sendResponse(exchange, 200, response, "application/json");
                    
                } else {
                    sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
                }
                
            } catch (Exception e) {
                logger.error("Error handling ONVIF notification", e);
                sendResponse(exchange, 500, "Internal Server Error", "text/plain");
            }
        }
        
        private String readRequestBody(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        }
        
        private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
    
    /**
     * Extract license plate from SOAP/XML notification
     * Supports multiple ONVIF camera formats
     */
    private String extractPlate(String xml) {
        if (xml == null || xml.isEmpty()) {
            return null;
        }
        
        String plate = null;
        
        try {
            // Format 1: SimpleItem with LicensePlate
            // <tt:SimpleItem Name="LicensePlate" Value="ABC123"/>
            plate = extractSimpleItem(xml, "LicensePlate");
            if (plate != null) return plate;
            
            // Format 2: SimpleItem with PlateNumber
            plate = extractSimpleItem(xml, "PlateNumber");
            if (plate != null) return plate;
            
            // Format 3: SimpleItem with VehiclePlate
            plate = extractSimpleItem(xml, "VehiclePlate");
            if (plate != null) return plate;
            
            // Format 4: PlateNumber element
            // <PlateNumber>ABC123</PlateNumber>
            plate = extractElement(xml, "PlateNumber");
            if (plate != null) return plate;
            
            // Format 5: LicensePlate element
            plate = extractElement(xml, "LicensePlate");
            if (plate != null) return plate;
            
            // Format 6: Plate element
            plate = extractElement(xml, "Plate");
            if (plate != null) return plate;
            
            // Format 7: ANPR specific - look for any plate-like pattern
            plate = extractFromAnprData(xml);
            if (plate != null) return plate;
            
        } catch (Exception e) {
            logger.error("Error extracting plate from XML", e);
        }
        
        return "Unknown";
    }
    
    /**
     * Extract value from SimpleItem format: Name="key" Value="value"
     */
    private String extractSimpleItem(String xml, String itemName) {
        String searchPattern = "Name=\"" + itemName + "\"";
        int nameIndex = xml.indexOf(searchPattern);
        if (nameIndex == -1) {
            // Try lowercase
            nameIndex = xml.toLowerCase().indexOf(searchPattern.toLowerCase());
        }
        
        if (nameIndex != -1) {
            // Find Value attribute after Name
            int valueStart = xml.indexOf("Value=\"", nameIndex);
            if (valueStart != -1 && valueStart < nameIndex + 100) {
                valueStart += 7; // Length of 'Value="'
                int valueEnd = xml.indexOf("\"", valueStart);
                if (valueEnd != -1) {
                    String value = xml.substring(valueStart, valueEnd).trim();
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Extract value from XML element: <ElementName>value</ElementName>
     */
    private String extractElement(String xml, String elementName) {
        String startTag = "<" + elementName + ">";
        String endTag = "</" + elementName + ">";
        
        int startIndex = xml.indexOf(startTag);
        if (startIndex == -1) {
            // Try with namespace prefix
            startTag = ":" + elementName + ">";
            startIndex = xml.indexOf(startTag);
            if (startIndex != -1) {
                // Find the actual start of the tag
                int tagStart = xml.lastIndexOf("<", startIndex);
                startIndex = startIndex + startTag.length() - 1;
            }
        } else {
            startIndex += startTag.length();
        }
        
        if (startIndex != -1) {
            int endIndex = xml.indexOf(endTag, startIndex);
            if (endIndex == -1) {
                endIndex = xml.indexOf("</" , startIndex);
            }
            if (endIndex != -1 && endIndex > startIndex) {
                String value = xml.substring(startIndex, endIndex).trim();
                // Remove any nested tags
                if (value.contains("<")) {
                    value = value.replaceAll("<[^>]+>", "").trim();
                }
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
    
    /**
     * Extract plate from ANPR-specific data formats
     */
    private String extractFromAnprData(String xml) {
        // Look for common ANPR data patterns
        String[] patterns = {
            "plate>", "Plate>", "PLATE>",
            "licensePlate>", "LicensePlate>",
            "vehicleNumber>", "VehicleNumber>",
            "regNumber>", "RegNumber>"
        };
        
        for (String pattern : patterns) {
            int index = xml.indexOf(pattern);
            if (index != -1) {
                int start = index + pattern.length();
                int end = xml.indexOf("<", start);
                if (end != -1 && end > start) {
                    String value = xml.substring(start, end).trim();
                    if (!value.isEmpty() && value.length() >= 4 && value.length() <= 15) {
                        return value;
                    }
                }
            }
        }
        return null;
    }
}
