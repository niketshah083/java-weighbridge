package com.scrapms.devicebridge;

/**
 * Launcher class for executable JAR
 * This is needed because JavaFX requires the main class to NOT extend Application
 * when running from a shaded/fat JAR
 */
public class Launcher {
    public static void main(String[] args) {
        DeviceBridgeApplication.main(args);
    }
}
