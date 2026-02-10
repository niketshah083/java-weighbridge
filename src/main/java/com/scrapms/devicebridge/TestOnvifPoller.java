package com.scrapms.devicebridge;

import com.scrapms.devicebridge.service.OnvifEventPoller;

/**
 * Test class to verify ONVIF Event Poller with your Secura ANPR camera.
 * 
 * Usage:
 *   java -cp target/device-bridge-1.0.0.jar com.scrapms.devicebridge.TestOnvifPoller <camera_ip> <username> <password>
 * 
 * Example:
 *   java -cp target/device-bridge-1.0.0.jar com.scrapms.devicebridge.TestOnvifPoller 192.168.0.156 admin admin123
 */
public class TestOnvifPoller {
    
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: TestOnvifPoller <camera_ip> <username> <password>");
            System.out.println("Example: TestOnvifPoller 192.168.0.156 admin admin123");
            System.exit(1);
        }
        
        String cameraIp = args[0];
        String username = args[1];
        String password = args[2];
        
        System.out.println("===========================================");
        System.out.println("ONVIF Event Poller Test");
        System.out.println("===========================================");
        System.out.println("Camera IP: " + cameraIp);
        System.out.println("Username: " + username);
        System.out.println("Password: " + "*".repeat(password.length()));
        System.out.println("===========================================");
        System.out.println();
        System.out.println("Starting ONVIF Event Poller...");
        System.out.println("Will poll for ANPR events every 2 seconds.");
        System.out.println("Press Ctrl+C to stop.");
        System.out.println();
        
        OnvifEventPoller poller = new OnvifEventPoller(cameraIp, username, password);
        
        // Set callback to print detected plates
        poller.setPlateCallback(plate -> {
            System.out.println();
            System.out.println("*******************************************");
            System.out.println("*** LICENSE PLATE DETECTED: " + plate + " ***");
            System.out.println("*******************************************");
            System.out.println();
        });
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nStopping poller...");
            poller.stop();
            System.out.println("Done.");
        }));
        
        // Start polling
        poller.start();
        
        // Keep running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
