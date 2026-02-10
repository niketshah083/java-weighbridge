package com.scrapms.devicebridge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.function.Consumer;

/**
 * ANPR (Automatic Number Plate Recognition) Listener for Secura cameras.
 * Listens on UDP port for plate data sent from DPSS (Digital Plate Scanning System).
 */
public class SecuraAnprListener {

    private static final Logger logger = LoggerFactory.getLogger(SecuraAnprListener.class);

    private static final int DEFAULT_PORT = 50060;
    private static final int BUFFER_SIZE = 2048;

    private int port;
    private DatagramSocket socket;
    private volatile boolean running = false;
    private Thread listenerThread;
    private Consumer<String> onPlateReceived;

    public SecuraAnprListener() {
        this(DEFAULT_PORT);
    }

    public SecuraAnprListener(int port) {
        this.port = port;
    }

    /**
     * Set callback for when a plate is received
     */
    public void setOnPlateReceived(Consumer<String> callback) {
        this.onPlateReceived = callback;
    }

    /**
     * Start listening for ANPR data
     */
    public void start() {
        if (running) {
            logger.warn("ANPR listener is already running");
            return;
        }

        listenerThread = new Thread(() -> {
            try {
                socket = new DatagramSocket(port);
                running = true;
                logger.info("ANPR listener started on port {}", port);

                byte[] buffer = new byte[BUFFER_SIZE];

                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet); // Blocks until data is received

                        String receivedData = new String(packet.getData(), 0, packet.getLength());
                        logger.info("ANPR Raw Data Received: {}", receivedData);

                        // Parse and process the plate data
                        String plateNumber = parsePlateNumber(receivedData);
                        if (plateNumber != null && !plateNumber.isEmpty()) {
                            logger.info("Plate Number Detected: {}", plateNumber);
                            if (onPlateReceived != null) {
                                onPlateReceived.accept(plateNumber);
                            }
                        }

                    } catch (Exception e) {
                        if (running) {
                            logger.error("Error receiving ANPR data: {}", e.getMessage());
                        }
                    }
                }

            } catch (Exception e) {
                logger.error("Failed to start ANPR listener: {}", e.getMessage());
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                running = false;
                logger.info("ANPR listener stopped");
            }
        }, "ANPR-Listener");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Stop the ANPR listener
     */
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
        }
        logger.info("ANPR listener stop requested");
    }

    /**
     * Check if listener is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Parse plate number from raw ANPR data.
     * Override this method to customize parsing based on your camera's data format.
     */
    protected String parsePlateNumber(String rawData) {
        // Default implementation - return trimmed raw data
        // TODO: Customize based on your Secura camera's data format
        // Common formats:
        // - Plain text: "MH12AB1234"
        // - CSV: "MH12AB1234,2026-02-06,10:30:00,Camera1"
        // - JSON: {"plate":"MH12AB1234","timestamp":"..."}
        
        if (rawData == null || rawData.trim().isEmpty()) {
            return null;
        }

        String trimmed = rawData.trim();

        // If it's CSV format, take the first field
        if (trimmed.contains(",")) {
            return trimmed.split(",")[0].trim();
        }

        // If it's JSON-like, try to extract plate
        if (trimmed.startsWith("{") && trimmed.contains("plate")) {
            // Simple extraction - for proper JSON use a JSON library
            int start = trimmed.indexOf("plate");
            if (start != -1) {
                int colonPos = trimmed.indexOf(":", start);
                int quoteStart = trimmed.indexOf("\"", colonPos);
                int quoteEnd = trimmed.indexOf("\"", quoteStart + 1);
                if (quoteStart != -1 && quoteEnd != -1) {
                    return trimmed.substring(quoteStart + 1, quoteEnd);
                }
            }
        }

        // Return as-is if no special format detected
        return trimmed;
    }

    /**
     * Main method for standalone testing
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + DEFAULT_PORT);
            }
        }

        SecuraAnprListener listener = new SecuraAnprListener(port);
        listener.setOnPlateReceived(plate -> {
            System.out.println(">>> PLATE DETECTED: " + plate);
        });

        listener.start();

        // Keep main thread alive
        System.out.println("Press Ctrl+C to stop...");
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            listener.stop();
        }
    }
}
