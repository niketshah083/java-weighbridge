package com.scrapms.devicebridge.util;

import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Weighbridge Simulator for testing without real hardware
 * Sends simulated weight data to a serial port
 */
public class WeighbridgeSimulator {
    
    private static final Logger logger = LoggerFactory.getLogger(WeighbridgeSimulator.class);
    
    private SerialPort port;
    private Timer timer;
    private final Random random = new Random();
    private double baseWeight = 1000.0;
    private double variance = 50.0;
    private int intervalMs = 1000;
    private String format = "%.2f kg\r\n";
    
    /**
     * List available serial ports
     */
    public static void listPorts() {
        System.out.println("\n=== Available Serial Ports ===");
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) {
            System.out.println("No serial ports found!");
            System.out.println("\nTo create virtual ports on Linux:");
            System.out.println("  sudo apt install socat");
            System.out.println("  socat -d -d pty,raw,echo=0,link=/tmp/ttyV0 pty,raw,echo=0,link=/tmp/ttyV1");
            System.out.println("\nTo create virtual ports on Windows:");
            System.out.println("  Download com0com from https://sourceforge.net/projects/com0com/");
        } else {
            for (SerialPort port : ports) {
                System.out.println("  - " + port.getSystemPortName() + " (" + port.getDescriptivePortName() + ")");
            }
        }
        System.out.println("==============================\n");
    }
    
    /**
     * Start simulator on specified port
     */
    public boolean start(String portName, double baseWeight, double variance, int intervalMs, String format) {
        this.baseWeight = baseWeight;
        this.variance = variance;
        this.intervalMs = intervalMs;
        this.format = format;
        
        try {
            logger.info("Starting weighbridge simulator on port: {}", portName);
            
            port = SerialPort.getCommPort(portName);
            port.setBaudRate(9600);
            port.setNumDataBits(8);
            port.setNumStopBits(1);
            port.setParity(SerialPort.NO_PARITY);
            
            if (!port.openPort()) {
                logger.error("Failed to open port: {}", portName);
                return false;
            }
            
            logger.info("Port opened successfully. Starting to send weight data...");
            
            timer = new Timer("WeighbridgeSimulator");
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    sendWeight();
                }
            }, 0, intervalMs);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error starting simulator", e);
            return false;
        }
    }
    
    /**
     * Send a weight reading
     */
    private void sendWeight() {
        try {
            double weight = baseWeight + (random.nextDouble() - 0.5) * variance;
            String data = String.format(format, weight);
            
            OutputStream out = port.getOutputStream();
            out.write(data.getBytes());
            out.flush();
            
            logger.debug("Sent: {}", data.trim());
            
        } catch (Exception e) {
            logger.error("Error sending weight", e);
        }
    }
    
    /**
     * Stop simulator
     */
    public void stop() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (port != null && port.isOpen()) {
            port.closePort();
        }
        logger.info("Simulator stopped");
    }
    
    /**
     * Main method for standalone testing
     */
    public static void main(String[] args) {
        System.out.println("\n========================================");
        System.out.println("  Weighbridge Simulator");
        System.out.println("========================================\n");
        
        // List available ports
        listPorts();
        
        if (args.length < 1) {
            System.out.println("Usage: java WeighbridgeSimulator <port> [baseWeight] [variance] [intervalMs]");
            System.out.println("\nExamples:");
            System.out.println("  Linux:   java WeighbridgeSimulator /tmp/ttyV1 1000 50 1000");
            System.out.println("  Windows: java WeighbridgeSimulator COM11 1000 50 1000");
            System.out.println("\nThis will send weight data like: '1023.45 kg' every second");
            return;
        }
        
        String portName = args[0];
        double baseWeight = args.length > 1 ? Double.parseDouble(args[1]) : 1000.0;
        double variance = args.length > 2 ? Double.parseDouble(args[2]) : 50.0;
        int intervalMs = args.length > 3 ? Integer.parseInt(args[3]) : 1000;
        
        WeighbridgeSimulator simulator = new WeighbridgeSimulator();
        
        if (simulator.start(portName, baseWeight, variance, intervalMs, "%.2f kg\r\n")) {
            System.out.println("\nSimulator running. Press Ctrl+C to stop.\n");
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(simulator::stop));
            
            // Keep running
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
                simulator.stop();
            }
        }
    }
}
