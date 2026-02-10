package com.scrapms.devicebridge.service;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.scrapms.devicebridge.model.WeighbridgeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for serial port communication with weighbridges
 */
public class SerialPortService {
    
    private static final Logger logger = LoggerFactory.getLogger(SerialPortService.class);
    
    private final Map<Integer, SerialPort> ports = new ConcurrentHashMap<>();
    private final Map<Integer, StringBuilder> buffers = new ConcurrentHashMap<>();
    private final Map<Integer, WeighbridgeConfig> configs = new ConcurrentHashMap<>();
    private final Map<Integer, List<Double>> lastReadings = new ConcurrentHashMap<>();
    private final Map<Integer, Consumer<WeightReading>> callbacks = new ConcurrentHashMap<>();
    private final Map<Integer, Thread> pollingThreads = new ConcurrentHashMap<>();
    
    /**
     * Connect to weighbridge serial port
     */
    public ConnectionResult connect(int weighbridgeId, WeighbridgeConfig config, 
                                   Consumer<WeightReading> onReading) {
        try {
            // Disconnect if already connected
            disconnect(weighbridgeId);
            
            // Log available ports for debugging
            List<String> availablePorts = listPorts();
            logger.info("Available serial ports: {}", availablePorts);
            
            // Convert port name for current OS
            String portName = convertPortName(config.getSerialPort());
            logger.info("Connecting to {} (original: {}) for weighbridge {}", 
                    portName, config.getSerialPort(), weighbridgeId);
            logger.info("Config - Baud: {}, DataBits: {}, StopBits: {}, Parity: {}", 
                    config.getBaudRate(), config.getDataBits(), config.getStopBits(), config.getParity());
            
            if (portName == null || portName.isEmpty()) {
                logger.error("No serial port configured for weighbridge {}", weighbridgeId);
                return new ConnectionResult(false, "No serial port configured");
            }
            
            SerialPort port = SerialPort.getCommPort(portName);
            
            // Set serial port parameters using setComPortParameters for proper configuration
            int stopBitsValue = getStopBits(config.getStopBits());
            int parityValue = getParity(config.getParity());
            
            port.setComPortParameters(
                config.getBaudRate(),
                config.getDataBits(),
                stopBitsValue,
                parityValue
            );
            
            // Set blocking read with timeout (5 seconds) - like reference code
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 5000, 0);
            
            // Enable DTR and RTS signals for hardware flow control
            port.setDTR();
            port.setRTS();
            
            if (port.openPort()) {
                logger.info("SUCCESS: Connected to {} (system port: {})", config.getSerialPort(), port.getSystemPortName());
                
                ports.put(weighbridgeId, port);
                buffers.put(weighbridgeId, new StringBuilder());
                configs.put(weighbridgeId, config);
                lastReadings.put(weighbridgeId, new ArrayList<>());
                callbacks.put(weighbridgeId, onReading);
                
                // Try event-based listener first
                boolean listenerAdded = port.addDataListener(new SerialPortDataListener() {
                    @Override
                    public int getListeningEvents() {
                        return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
                    }
                    
                    @Override
                    public void serialEvent(SerialPortEvent event) {
                        logger.debug("Serial event received: type={}", event.getEventType());
                        if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                            int available = port.bytesAvailable();
                            logger.debug("Bytes available: {}", available);
                            if (available > 0) {
                                byte[] buffer = new byte[available];
                                int read = port.readBytes(buffer, buffer.length);
                                logger.info("Read {} bytes from serial port", read);
                                handleData(weighbridgeId, new String(buffer, StandardCharsets.UTF_8));
                            }
                        }
                    }
                });
                
                logger.info("Data listener added: {}", listenerAdded);
                
                // For pts/virtual ports, also start a polling thread as backup
                // because event listeners may not work reliably on pseudo-terminals
                if (portName.contains("pts") || portName.contains("ttyV")) {
                    logger.info("Starting polling thread for virtual port: {}", portName);
                    // startPollingThread(weighbridgeId, port);
                }
                
                return new ConnectionResult(true, null);
            } else {
                logger.error("Failed to open port: {} (last error: {})", config.getSerialPort(), port.getLastErrorCode());
                return new ConnectionResult(false, "Failed to open port - error code: " + port.getLastErrorCode());
            }
            
        } catch (Exception e) {
            logger.error("Error connecting to serial port", e);
            return new ConnectionResult(false, e.getMessage());
        }
    }
    
    /**
     * Disconnect from weighbridge
     */
    public void disconnect(int weighbridgeId) {
        logger.info("Disconnecting weighbridge {}", weighbridgeId);
        
        // Stop polling thread first
        Thread pollingThread = pollingThreads.remove(weighbridgeId);
        if (pollingThread != null) {
            pollingThread.interrupt();
            logger.info("Stopped polling thread for weighbridge {}", weighbridgeId);
        }
        
        SerialPort port = ports.remove(weighbridgeId);
        if (port != null) {
            try {
                port.removeDataListener();
                if (port.isOpen()) {
                    Thread.sleep(50);
                    port.closePort();
                }
                logger.info("Disconnected weighbridge {}", weighbridgeId);
            } catch (Exception e) {
                logger.warn("Error disconnecting weighbridge {}: {}", weighbridgeId, e.getMessage());
            }
        }
        buffers.remove(weighbridgeId);
        configs.remove(weighbridgeId);
        lastReadings.remove(weighbridgeId);
        callbacks.remove(weighbridgeId);
    }
    
    /**
     * Start a polling thread for virtual ports where event listeners may not work
     */
    private void startPollingThread(int weighbridgeId, SerialPort port) {
        Thread thread = new Thread(() -> {
            logger.info("Polling thread started for weighbridge {}", weighbridgeId);
            byte[] buffer = new byte[1024];
            
            while (!Thread.currentThread().isInterrupted() && port.isOpen()) {
                try {
                    int available = port.bytesAvailable();
                    if (available > 0) {
                        int bytesToRead = Math.min(available, buffer.length);
                        int bytesRead = port.readBytes(buffer, bytesToRead);
                        if (bytesRead > 0) {
                            String data = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                            logger.info("Polling thread read {} bytes: [{}]", bytesRead, 
                                    data.replace("\r", "\\r").replace("\n", "\\n"));
                            handleData(weighbridgeId, data);
                        }
                    }
                    Thread.sleep(3000); // Poll every 3000ms
                } catch (InterruptedException e) {
                    logger.info("Polling thread interrupted for weighbridge {}", weighbridgeId);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in polling thread: {}", e.getMessage());
                }
            }
            logger.info("Polling thread stopped for weighbridge {}", weighbridgeId);
        }, "SerialPort-Poller-" + weighbridgeId);
        
        thread.setDaemon(true);
        thread.start();
        pollingThreads.put(weighbridgeId, thread);
    }
    
    /**
     * Disconnect all weighbridges
     */
    public void disconnectAll() {
        logger.info("Disconnecting all weighbridges...");
        new ArrayList<>(ports.keySet()).forEach(this::disconnect);
        logger.info("All weighbridges disconnected");
    }
    
    /**
     * Check if weighbridge is connected
     */
    public boolean isConnected(int weighbridgeId) {
        SerialPort port = ports.get(weighbridgeId);
        return port != null && port.isOpen();
    }
    
    /**
     * Request a weight reading
     */
    public WeightReading requestReading(int weighbridgeId) {
        List<Double> readings = lastReadings.get(weighbridgeId);
        WeighbridgeConfig config = configs.get(weighbridgeId);
        
        if (readings != null && !readings.isEmpty() && config != null) {
            double latestWeight = readings.get(readings.size() - 1);
            boolean isStable = checkStability(readings, config);
            
            return new WeightReading(
                latestWeight,
                config.getWeightUnit(),
                isStable,
                String.format("%.2f %s", latestWeight, config.getWeightUnit()),
                LocalDateTime.now()
            );
        }
        
        return null;
    }
    
    /**
     * Handle incoming data from serial port
     */
    private void handleData(int weighbridgeId, String data) {
        WeighbridgeConfig config = configs.get(weighbridgeId);
        if (config == null) {
            logger.warn("No config found for weighbridge {}", weighbridgeId);
            return;
        }
        
        // Log raw data with hex representation for debugging
        StringBuilder hexData = new StringBuilder();
        for (char c : data.toCharArray()) {
            hexData.append(String.format("%02X ", (int) c));
        }
        logger.info("Received data from weighbridge {}: [{}] (length: {}, hex: {})", 
                weighbridgeId, 
                data.replace("\r", "\\r").replace("\n", "\\n"),
                data.length(),
                hexData.toString().trim());
        
        StringBuilder buffer = buffers.get(weighbridgeId);
        buffer.append(data);
        
        logger.info("Buffer content: [{}] (length: {})", 
                buffer.toString().replace("\r", "\\r").replace("\n", "\\n"),
                buffer.length());
        
        // Try to parse weight
        WeightReading reading = parseWeight(buffer.toString(), config);
        
        if (reading != null) {
            logger.info("Weight parsed successfully: {} {}", reading.getWeight(), reading.getUnit());
            
            // Clear buffer after successful parse
            buffer.setLength(0);
            
            // Update last readings
            List<Double> readings = lastReadings.get(weighbridgeId);
            readings.add(reading.getWeight());
            
            // Keep only last N readings
            if (readings.size() > config.getStableReadings()) {
                readings.remove(0);
            }
            
            // Check stability
            boolean isStable = checkStability(readings, config);
            reading = new WeightReading(
                reading.getWeight(),
                reading.getUnit(),
                isStable,
                reading.getRawData(),
                reading.getTimestamp()
            );
            
            // Callback
            Consumer<WeightReading> callback = callbacks.get(weighbridgeId);
            if (callback != null) {
                logger.info("Calling callback for weighbridge {}: {} {} (stable: {})", 
                        weighbridgeId, reading.getWeight(), reading.getUnit(), reading.isStable());
                callback.accept(reading);
            } else {
                logger.warn("No callback registered for weighbridge {}", weighbridgeId);
            }
        } else {
            logger.debug("Could not parse weight from buffer yet");
            // Limit buffer size
            if (buffer.length() > 1024) {
                buffer.delete(0, buffer.length() - 512);
            }
        }
    }
    
    /**
     * Parse weight from buffer
     */
    private WeightReading parseWeight(String buffer, WeighbridgeConfig config) {
        try {
            String rawData = buffer;
            
            logger.debug("Parsing weight from: [{}] (length: {})", 
                    rawData.replace("\r", "\\r").replace("\n", "\\n"), rawData.length());
            
            // Weighbridge sends 10-char packets: STX + space + 7 digits + KG
            // Pattern: ☻ 0000070KG (STX = \u0002 or ☻)
            // Look for complete 10-char packet
            Pattern weighbridgePattern = Pattern.compile("[\\x02☻]\\s?(\\d{7})KG", Pattern.CASE_INSENSITIVE);
            Matcher weighbridgeMatcher = weighbridgePattern.matcher(rawData);
            
            if (weighbridgeMatcher.find()) {
                String weightStr = weighbridgeMatcher.group(1);
                logger.debug("Weighbridge 10-char pattern matched: {}", weightStr);
                double weightValue = Double.parseDouble(weightStr);
                logger.debug("Parsed weight value: {}", weightValue);
                
                // Apply multiplier
                weightValue *= config.getWeightMultiplier();
                logger.debug("Weight after multiplier: {}", weightValue);
                
                return new WeightReading(
                    weightValue,
                    config.getWeightUnit(),
                    false, // Stability will be checked later
                    weightStr + " " + config.getWeightUnit(),
                    LocalDateTime.now()
                );
            }
            
            // Buffer doesn't have complete packet yet, wait for more data
            if (rawData.length() < 15) {
                logger.debug("Buffer too short ({}), waiting for more data (need 15)", rawData.length());
                return null;
            }
            
            // Fallback: Remove control characters and try standard parsing
            rawData = rawData.replaceAll("[\\x00-\\x1F☻]", " ").trim();
            logger.debug("After removing control chars: [{}]", rawData);
            
            // Skip if only unit indicator
            if (rawData.matches("^\\d*\\s*KG\\s*$") && !rawData.matches(".*\\d{2,}.*")) {
                logger.debug("Skipping unit-only packet");
                return null;
            }
            
            // Extract data between markers if defined
            if (config.getWeightStartMarker() != null && !config.getWeightStartMarker().isEmpty() 
                && config.getWeightEndMarker() != null && !config.getWeightEndMarker().isEmpty()) {
                int startIdx = buffer.indexOf(config.getWeightStartMarker());
                int endIdx = buffer.indexOf(config.getWeightEndMarker(), 
                        startIdx + config.getWeightStartMarker().length());
                
                logger.debug("Looking for markers: start='{}' end='{}', found at: {} and {}", 
                        config.getWeightStartMarker(), config.getWeightEndMarker(), startIdx, endIdx);
                
                if (startIdx == -1 || endIdx == -1) {
                    logger.debug("Markers not found in buffer");
                    return null;
                }
                
                rawData = buffer.substring(startIdx + config.getWeightStartMarker().length(), endIdx);
                logger.debug("Extracted data between markers: [{}]", rawData);
            }
            
            // Extract weight using regex
            Double weightValue = null;
            
            if (config.getWeightRegex() != null && !config.getWeightRegex().isEmpty()) {
                logger.debug("Using custom regex: {}", config.getWeightRegex());
                Pattern pattern = Pattern.compile(config.getWeightRegex());
                Matcher matcher = pattern.matcher(rawData);
                if (matcher.find()) {
                    String match = matcher.groupCount() > 0 ? matcher.group(1) : matcher.group(0);
                    logger.debug("Regex matched: {}", match);
                    weightValue = Double.parseDouble(match);
                } else {
                    logger.debug("Regex did not match");
                }
            } else {
                // Default: find number before KG or just any number
                logger.debug("Using default number regex");
                Pattern pattern = Pattern.compile("(\\d+)\\s*KG|\\b(\\d+)\\b", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(rawData);
                if (matcher.find()) {
                    String match = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                    logger.debug("Default regex matched: {}", match);
                    weightValue = Double.parseDouble(match);
                } else {
                    logger.debug("Default regex did not match");
                }
            }
            
            if (weightValue == null) {
                logger.debug("No weight value extracted");
                return null;
            }
            
            // Apply multiplier
            weightValue *= config.getWeightMultiplier();
            logger.debug("Weight after multiplier: {}", weightValue);
            
            return new WeightReading(
                weightValue,
                config.getWeightUnit(),
                false, // Stability will be checked later
                rawData.trim(),
                LocalDateTime.now()
            );
            
        } catch (Exception e) {
            logger.debug("Error parsing weight: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if readings are stable
     */
    private boolean checkStability(List<Double> readings, WeighbridgeConfig config) {
        int requiredReadings = config.getStableReadings();
        double threshold = config.getStabilityThreshold();
        
        logger.debug("Checking stability: readings={}, required={}, threshold={}", 
                readings.size(), requiredReadings, threshold);
        
        if (readings.size() < requiredReadings) {
            logger.debug("Not enough readings for stability check ({}/{})", readings.size(), requiredReadings);
            return false;
        }
        
        List<Double> recentReadings = readings.subList(
                Math.max(0, readings.size() - requiredReadings), 
                readings.size()
        );
        
        double max = Collections.max(recentReadings);
        double min = Collections.min(recentReadings);
        double variance = max - min;
        
        boolean isStable = variance <= threshold;
        logger.debug("Stability check: recent={}, min={}, max={}, variance={}, threshold={}, stable={}", 
                recentReadings, min, max, variance, threshold, isStable);
        
        return isStable;
    }
    
    /**
     * Convert parity string to SerialPort constant
     */
    private int getParity(String parity) {
        if (parity == null) return SerialPort.NO_PARITY;
        switch (parity.toLowerCase()) {
            case "even": return SerialPort.EVEN_PARITY;
            case "odd": return SerialPort.ODD_PARITY;
            case "mark": return SerialPort.MARK_PARITY;
            case "space": return SerialPort.SPACE_PARITY;
            default: return SerialPort.NO_PARITY;
        }
    }
    
    /**
     * Convert stop bits integer to SerialPort constant
     */
    private int getStopBits(int stopBits) {
        switch (stopBits) {
            case 2: return SerialPort.TWO_STOP_BITS;
            case 15: // 1.5 stop bits represented as 15
            case 3: return SerialPort.ONE_POINT_FIVE_STOP_BITS;
            default: return SerialPort.ONE_STOP_BIT;
        }
    }
    
    /**
     * List available serial ports
     */
    public static List<String> listPorts() {
        List<String> portNames = new ArrayList<>();
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            portNames.add(port.getSystemPortName());
        }
        return portNames;
    }
    
    /**
     * Convert port name for current OS
     * Windows uses: COM1, COM2, etc.
     * Linux uses: /dev/ttyUSB0, /dev/ttyACM0, /dev/ttyS0, /tmp/ttyV0, etc.
     */
    private String convertPortName(String portName) {
        if (portName == null || portName.isEmpty()) {
            return portName;
        }
        
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");
        
        logger.info("Converting port name: {} (OS: {}, isWindows: {}, isLinux: {})", 
                portName, System.getProperty("os.name"), isWindows, isLinux);
        
        // Get available ports for smart fallback
        List<String> availablePorts = listPorts();
        logger.info("Available ports on system: {}", availablePorts);
        
        // On Windows, check if the requested COM port exists
        if (isWindows && portName.toUpperCase().startsWith("COM")) {
            // Check if requested port exists
            if (availablePorts.contains(portName.toUpperCase()) || availablePorts.contains(portName)) {
                logger.info("Requested port {} is available", portName);
                return portName;
            }
            
            // Port doesn't exist - try to find a USB serial port
            logger.warn("Requested port {} not found. Looking for USB serial ports...", portName);
            for (String available : availablePorts) {
                SerialPort port = SerialPort.getCommPort(available);
                String desc = port.getDescriptivePortName().toLowerCase();
                // Look for USB serial adapters (FTDI, CH340, CP210x, etc.)
                if (desc.contains("usb") || desc.contains("serial") || 
                    desc.contains("ftdi") || desc.contains("ch340") || 
                    desc.contains("cp210") || desc.contains("prolific")) {
                    logger.info("Found USB serial port: {} ({})", available, port.getDescriptivePortName());
                    return available;
                }
            }
            
            // No USB serial found, return first available COM port if any
            if (!availablePorts.isEmpty()) {
                String firstPort = availablePorts.get(0);
                logger.warn("No USB serial port found. Using first available: {}", firstPort);
                return firstPort;
            }
            
            logger.error("No COM ports available on system!");
            return portName;
        }
        
        // If port name starts with /dev/ or /tmp/ (Linux format) - handle first
        if (portName.startsWith("/dev/") || portName.startsWith("/tmp/")) {
            if (isWindows) {
                // Convert /dev/ttyUSB0 -> find appropriate COM port
                logger.info("Linux port format on Windows, finding USB serial port...");
                for (String available : availablePorts) {
                    SerialPort port = SerialPort.getCommPort(available);
                    String desc = port.getDescriptivePortName().toLowerCase();
                    if (desc.contains("usb") || desc.contains("serial") || 
                        desc.contains("ftdi") || desc.contains("ch340") || 
                        desc.contains("cp210") || desc.contains("prolific")) {
                        logger.info("Found USB serial port: {} ({})", available, port.getDescriptivePortName());
                        return available;
                    }
                }
                
                // Fallback to first available
                if (!availablePorts.isEmpty()) {
                    logger.warn("Using first available port: {}", availablePorts.get(0));
                    return availablePorts.get(0);
                }
                
                // Legacy conversion as last resort
                if (portName.contains("ttyUSB")) {
                    int num = Integer.parseInt(portName.replaceAll("\\D+", "")) + 1;
                    String comPort = "COM" + num;
                    logger.info("Converted {} to {}", portName, comPort);
                    return comPort;
                }
                if (portName.contains("ttyACM")) {
                    int num = Integer.parseInt(portName.replaceAll("\\D+", "")) + 1;
                    String comPort = "COM" + num;
                    logger.info("Converted {} to {}", portName, comPort);
                    return comPort;
                }
                if (portName.contains("ttyS")) {
                    int num = Integer.parseInt(portName.replaceAll("\\D+", "")) + 1;
                    String comPort = "COM" + num;
                    logger.info("Converted {} to {}", portName, comPort);
                    return comPort;
                }
                
                // Return first available port
                List<String> available = listPorts();
                if (!available.isEmpty()) {
                    logger.warn("Port {} not found, using first available: {}", portName, available.get(0));
                    return available.get(0);
                }
                
                return "COM1"; // Default
            }
            // Linux - use as-is (supports /dev/ttyUSB0, /tmp/ttyV0, etc.)
            logger.info("Using Linux port as-is: {}", portName);
            return portName;
        }
        
        // If port name starts with COM (Windows format)
        if (portName.toUpperCase().startsWith("COM")) {
            if (isLinux) {
                // Convert COM1 -> /dev/ttyUSB0 or /dev/ttyS0
                // Try to find available port
                String comNumber = portName.substring(3);
                int portNum;
                try {
                    portNum = Integer.parseInt(comNumber);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid COM port number: {}", portName);
                    portNum = 1;
                }
                
                // First try ttyUSB (USB serial adapters)
                String usbPort = "/dev/ttyUSB" + (portNum - 1);
                if (portExists(usbPort)) {
                    logger.info("Converted {} to {}", portName, usbPort);
                    return usbPort;
                }
                
                // Try ttyACM (Arduino, etc.)
                String acmPort = "/dev/ttyACM" + (portNum - 1);
                if (portExists(acmPort)) {
                    logger.info("Converted {} to {}", portName, acmPort);
                    return acmPort;
                }
                
                // Try ttyS (built-in serial ports)
                String sPort = "/dev/ttyS" + (portNum - 1);
                if (portExists(sPort)) {
                    logger.info("Converted {} to {}", portName, sPort);
                    return sPort;
                }
                
                // Return first available port if any
                List<String> available = listPorts();
                if (!available.isEmpty()) {
                    logger.warn("Port {} not found on Linux, using first available: {}", portName, available.get(0));
                    return available.get(0);
                }
                
                logger.error("No serial ports available on Linux for {}", portName);
                return portName;
            }
            return portName; // Windows, use as-is
        }
        
        // Unknown format - return as-is
        logger.info("Unknown port format, using as-is: {}", portName);
        return portName;
    }
    
    /**
     * Check if a port exists
     */
    private boolean portExists(String portName) {
        try {
            // First check if it's a file path that exists (for virtual ports like /tmp/ttyV0)
            if (portName.startsWith("/")) {
                java.io.File portFile = new java.io.File(portName);
                if (portFile.exists()) {
                    logger.debug("Port {} exists as file", portName);
                    return true;
                }
            }
            
            // Check against jSerialComm's list
            SerialPort[] ports = SerialPort.getCommPorts();
            for (SerialPort port : ports) {
                String sysName = port.getSystemPortName();
                if (sysName.equals(portName) || 
                    ("/dev/" + sysName).equals(portName) ||
                    portName.equals(sysName)) {
                    logger.debug("Port {} found in jSerialComm list", portName);
                    return true;
                }
            }
        } catch (Exception e) {
            logger.debug("Error checking port existence: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Connection Result
     */
    public static class ConnectionResult {
        private final boolean success;
        private final String error;
        
        public ConnectionResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
        
        public boolean isSuccess() { return success; }
        public String getError() { return error; }
    }
    
    /**
     * Weight Reading
     */
    public static class WeightReading {
        private final double weight;
        private final String unit;
        private final boolean isStable;
        private final String rawData;
        private final LocalDateTime timestamp;
        
        public WeightReading(double weight, String unit, boolean isStable, 
                           String rawData, LocalDateTime timestamp) {
            this.weight = weight;
            this.unit = unit;
            this.isStable = isStable;
            this.rawData = rawData;
            this.timestamp = timestamp;
        }
        
        public double getWeight() { return weight; }
        public String getUnit() { return unit; }
        public boolean isStable() { return isStable; }
        public String getRawData() { return rawData; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
