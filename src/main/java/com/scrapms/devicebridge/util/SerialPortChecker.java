package com.scrapms.devicebridge.util;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Simple utility to check serial port connectivity
 */
public class SerialPortChecker {
    
    public static void main(String[] args) {
        System.out.println("\n========================================");
        System.out.println("  Serial Port Connectivity Checker");
        System.out.println("========================================\n");
        
        SerialPort[] ports = SerialPort.getCommPorts();
        
        if (ports.length == 0) {
            System.out.println("❌ No serial ports detected by Java!");
            System.out.println("\nPossible issues:");
            System.out.println("  - No serial device connected");
            System.out.println("  - Missing permissions (add user to 'dialout' group)");
            System.out.println("  - Driver not loaded");
            return;
        }
        
        System.out.println("✅ Found " + ports.length + " serial port(s):\n");
        
        for (int i = 0; i < ports.length; i++) {
            SerialPort port = ports[i];
            System.out.println("Port #" + (i + 1) + ":");
            System.out.println("  System Name:     " + port.getSystemPortName());
            System.out.println("  Descriptive:     " + port.getDescriptivePortName());
            System.out.println("  Port Description:" + port.getPortDescription());
            System.out.println("  Full Path:       /dev/" + port.getSystemPortName());
            
            // Try to open the port
            System.out.print("  Can Open:        ");
            if (port.openPort()) {
                System.out.println("✅ YES");
                port.closePort();
            } else {
                System.out.println("❌ NO (Error: " + port.getLastErrorCode() + ")");
            }
            System.out.println();
        }
        
        System.out.println("========================================");
        System.out.println("Your serial port is connected and accessible!");
        System.out.println("========================================\n");
    }
}
