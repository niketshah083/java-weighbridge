package com.scrapms.devicebridge.service;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ONVIF Event Poller - Polls ANPR events from cameras.
 * Supports multiple approaches:
 * 1. ONVIF PullPoint subscription (standard)
 * 2. ONVIF GetEventProperties (discovery)
 * 3. Direct HTTP polling for proprietary ANPR APIs
 * 
 * Camera: Secura SIP-20EH5W-BO4 (ANPR)
 */
public class OnvifEventPoller {
    private static final Logger logger = LoggerFactory.getLogger(OnvifEventPoller.class);
    
    private final String cameraIp;
    private final int cameraPort;
    private final String username;
    private final String password;
    private final OkHttpClient httpClient;
    
    private ScheduledExecutorService scheduler;
    private Consumer<String> plateCallback;
    private String subscriptionAddress;
    private String eventServiceUrl;
    private boolean running = false;
    private boolean useAlternativePolling = false;
    
    // Polling interval in seconds
    private int pollIntervalSeconds = 3;
    
    // ONVIF namespaces - using SOAP 1.1 which is more widely supported
    private static final String SOAP_ENV_11 = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP_ENV_12 = "http://www.w3.org/2003/05/soap-envelope";
    private static final String WSSE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String WSU = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    
    public OnvifEventPoller(String cameraIp, String username, String password) {
        this(cameraIp, 80, username, password);
    }
    
    public OnvifEventPoller(String cameraIp, int cameraPort, String username, String password) {
        this.cameraIp = cameraIp;
        this.cameraPort = cameraPort;
        this.username = username;
        this.password = password;
        
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .authenticator((route, response) -> {
                    // HTTP Basic Auth fallback
                    String credential = Credentials.basic(username, password);
                    return response.request().newBuilder()
                            .header("Authorization", credential)
                            .build();
                })
                .build();
    }
    
    public void setPlateCallback(Consumer<String> callback) {
        this.plateCallback = callback;
    }
    
    public void setPollIntervalSeconds(int seconds) {
        this.pollIntervalSeconds = Math.max(1, seconds);
    }

    /**
     * Start polling for ANPR events
     */
    public void start() {
        if (running) {
            logger.warn("ONVIF Event Poller already running for {}", cameraIp);
            return;
        }
        
        logger.info("Starting ONVIF Event Poller for camera: {}:{}", cameraIp, cameraPort);
        
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OnvifPoller-" + cameraIp);
            t.setDaemon(true);
            return t;
        });
        
        running = true;
        
        // First discover camera capabilities, then start polling
        scheduler.execute(() -> {
            try {
                // Step 1: Get device capabilities to find event service URL
                discoverEventService();
                
                // Step 2: Try to create PullPoint subscription
                boolean subscriptionCreated = tryCreateSubscription();
                
                if (!subscriptionCreated) {
                    logger.info("PullPoint not supported, trying alternative polling methods");
                    useAlternativePolling = true;
                }
                
                // Step 3: Start polling
                scheduler.scheduleAtFixedRate(
                    this::pollEvents,
                    1,
                    pollIntervalSeconds,
                    TimeUnit.SECONDS
                );
                
            } catch (Exception e) {
                logger.error("Failed to initialize ONVIF poller", e);
                // Still try polling with alternative methods
                useAlternativePolling = true;
                scheduler.scheduleAtFixedRate(
                    this::pollEvents,
                    1,
                    pollIntervalSeconds,
                    TimeUnit.SECONDS
                );
            }
        });
    }
    
    /**
     * Stop polling
     */
    public void stop() {
        if (!running) return;
        
        running = false;
        
        if (subscriptionAddress != null) {
            try {
                unsubscribe();
            } catch (Exception e) {
                logger.debug("Error unsubscribing: {}", e.getMessage());
            }
        }
        
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        
        logger.info("ONVIF Event Poller stopped for {}", cameraIp);
    }
    
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Discover the event service URL from device capabilities
     */
    private void discoverEventService() {
        String deviceServiceUrl = String.format("http://%s:%d/onvif/device_service", cameraIp, cameraPort);
        
        // First try GetCapabilities
        String soapRequest = buildGetCapabilitiesRequest();
        
        logger.info("Discovering camera capabilities at: {}", deviceServiceUrl);
        
        try {
            String response = sendSoapRequest(deviceServiceUrl, soapRequest, "http://www.onvif.org/ver10/device/wsdl/GetCapabilities");
            
            if (response != null) {
                logger.info("GetCapabilities response received ({} bytes)", response.length());
                logger.debug("Capabilities: {}", response.length() > 1000 ? response.substring(0, 1000) + "..." : response);
                
                // Extract event service URL
                eventServiceUrl = extractEventServiceUrl(response);
                if (eventServiceUrl != null) {
                    logger.info("Found Event Service URL: {}", eventServiceUrl);
                } else {
                    // Try common event service URLs
                    eventServiceUrl = String.format("http://%s:%d/onvif/event_service", cameraIp, cameraPort);
                    logger.info("Using default Event Service URL: {}", eventServiceUrl);
                }
            }
        } catch (Exception e) {
            logger.warn("GetCapabilities failed: {}", e.getMessage());
            eventServiceUrl = String.format("http://%s:%d/onvif/event_service", cameraIp, cameraPort);
        }
    }
    
    /**
     * Try to create a PullPoint subscription
     */
    private boolean tryCreateSubscription() {
        if (eventServiceUrl == null) {
            eventServiceUrl = String.format("http://%s:%d/onvif/event_service", cameraIp, cameraPort);
        }
        
        // Try different endpoints
        String[] endpoints = {
            eventServiceUrl,
            String.format("http://%s:%d/onvif/Events", cameraIp, cameraPort),
            String.format("http://%s:%d/onvif/device_service", cameraIp, cameraPort),
        };
        
        for (String endpoint : endpoints) {
            logger.info("Trying CreatePullPointSubscription at: {}", endpoint);
            
            try {
                String soapRequest = buildCreatePullPointSubscriptionRequest();
                String response = sendSoapRequest(endpoint, soapRequest, 
                    "http://www.onvif.org/ver10/events/wsdl/EventPortType/CreatePullPointSubscriptionRequest");
                
                if (response != null) {
                    logger.info("CreatePullPointSubscription response: {}", 
                        response.length() > 1000 ? response.substring(0, 1000) : response);
                    
                    if (!response.contains("Fault") && !response.contains("fault")) {
                        subscriptionAddress = extractSubscriptionAddress(response);
                        if (subscriptionAddress != null) {
                            logger.info("PullPoint subscription created: {}", subscriptionAddress);
                            return true;
                        }
                        // Use the endpoint as subscription address
                        subscriptionAddress = endpoint;
                        logger.info("Using endpoint as subscription address: {}", subscriptionAddress);
                        return true;
                    }
                }
            } catch (Exception e) {
                logger.debug("CreatePullPointSubscription failed at {}: {}", endpoint, e.getMessage());
            }
        }
        
        return false;
    }

    /**
     * Poll for events - tries multiple methods
     */
    private void pollEvents() {
        if (!running) return;
        
        try {
            if (useAlternativePolling) {
                // Try alternative polling methods for cameras that don't support PullPoint
                pollAlternativeMethods();
            } else if (subscriptionAddress != null) {
                // Standard PullMessages
                pollPullMessages();
            } else {
                // Fallback to alternative
                pollAlternativeMethods();
            }
        } catch (Exception e) {
            logger.debug("Error polling events: {}", e.getMessage());
        }
    }
    
    /**
     * Poll using standard PullMessages
     */
    private void pollPullMessages() {
        try {
            String soapRequest = buildPullMessagesRequest();
            logger.debug("Sending PullMessages to: {}", subscriptionAddress);
            logger.debug("Request body: {}", soapRequest.substring(0, Math.min(500, soapRequest.length())));
            
            String response = sendSoapRequest(subscriptionAddress, soapRequest,
                "http://www.onvif.org/ver10/events/wsdl/PullPointSubscription/PullMessagesRequest");
            
            if (response != null) {
                if (response.contains("Fault") || response.contains("fault")) {
                    // Extract fault reason
                    String faultReason = extractFaultReason(response);
                    logger.warn("PullMessages fault: {}", faultReason);
                    
                    // Check if subscription expired
                    if (response.contains("InvalidSubscription") || response.contains("ResourceUnknown")) {
                        logger.info("Subscription expired, renewing...");
                        renewSubscription();
                    } else {
                        logger.debug("Switching to alternative polling");
                        useAlternativePolling = true;
                    }
                } else {
                    processEventResponse(response);
                }
            }
        } catch (Exception e) {
            logger.debug("PullMessages error: {}", e.getMessage());
            useAlternativePolling = true;
        }
    }
    
    /**
     * Extract fault reason from SOAP fault response
     */
    private String extractFaultReason(String xml) {
        // Try to extract <env:Reason><env:Text>...</env:Text></env:Reason>
        Pattern pattern = Pattern.compile("<[^>]*Text[^>]*>([^<]+)</", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Try <faultstring>
        pattern = Pattern.compile("<[^>]*faultstring[^>]*>([^<]+)</", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Try <Code><Value>
        pattern = Pattern.compile("<[^>]*Value[^>]*>([^<]+)</", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return "Unknown fault";
    }
    
    /**
     * Alternative polling methods for cameras that don't support standard ONVIF events
     */
    private void pollAlternativeMethods() {
        // Method 1: Try GetEventProperties to see what events are available
        tryGetEventProperties();
        
        // Method 2: Try common proprietary ANPR endpoints
        tryProprietaryAnprEndpoints();
    }
    
    /**
     * Try GetEventProperties to discover available events
     */
    private void tryGetEventProperties() {
        String endpoint = eventServiceUrl != null ? eventServiceUrl : 
            String.format("http://%s:%d/onvif/event_service", cameraIp, cameraPort);
        
        try {
            String soapRequest = buildGetEventPropertiesRequest();
            String response = sendSoapRequest(endpoint, soapRequest,
                "http://www.onvif.org/ver10/events/wsdl/EventPortType/GetEventPropertiesRequest");
            
            if (response != null && !response.contains("Fault")) {
                // Log full response to see available events (only once)
                if (!eventPropertiesLogged) {
                    logger.info("GetEventProperties full response ({} bytes): {}", response.length(), response);
                    eventPropertiesLogged = true;
                }
                
                // Check for any plate data in the response
                String plate = extractLicensePlate(response);
                if (plate != null) {
                    logger.info("ANPR Event - Detected Plate: {}", plate);
                    if (plateCallback != null) {
                        plateCallback.accept(plate);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("GetEventProperties error: {}", e.getMessage());
        }
    }
    
    private boolean eventPropertiesLogged = false;
    
    /**
     * Try proprietary ANPR endpoints that some cameras use
     * Kedacom cameras use proprietary APIs for ANPR
     */
    private void tryProprietaryAnprEndpoints() {
        // Kedacom-specific ANPR API endpoints
        String[] anprEndpoints = {
            // Kedacom ANPR endpoints
            String.format("http://%s:%d/LAPI/V1.0/Channels/0/Media/Video/Streams/0/Plates", cameraIp, cameraPort),
            String.format("http://%s:%d/LAPI/V1.0/System/Event/Notification/Subscriptions", cameraIp, cameraPort),
            String.format("http://%s:%d/LAPI/V1.0/Channels/0/Media/Video/Plates", cameraIp, cameraPort),
            String.format("http://%s:%d/cgi-bin/snapManager.cgi?action=attachFileProc&channel=0&heartbeat=5", cameraIp, cameraPort),
            String.format("http://%s:%d/cgi-bin/trafficEvent.cgi?action=getState", cameraIp, cameraPort),
            String.format("http://%s:%d/cgi-bin/vehicleDetect.cgi?action=getPlate", cameraIp, cameraPort),
            // Hikvision ISAPI (in case it's compatible)
            String.format("http://%s:%d/ISAPI/Traffic/channels/1/vehicleDetect/plates", cameraIp, cameraPort),
            String.format("http://%s:%d/ISAPI/Event/notification/alertStream", cameraIp, cameraPort),
            // Generic CGI endpoints
            String.format("http://%s:%d/cgi-bin/anpr.cgi", cameraIp, cameraPort),
            String.format("http://%s:%d/api/anpr/plates", cameraIp, cameraPort),
            String.format("http://%s:%d/api/lpr/result", cameraIp, cameraPort),
        };
        
        String credentials = Credentials.basic(username, password);
        
        for (String endpoint : anprEndpoints) {
            try {
                Request request = new Request.Builder()
                        .url(endpoint)
                        .get()
                        .addHeader("Accept", "application/json, application/xml, text/xml, */*")
                        .addHeader("Authorization", credentials)
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String body = response.body() != null ? response.body().string() : "";
                        if (!body.isEmpty()) {
                            logger.info("Got response from {}: {}", endpoint, 
                                body.length() > 300 ? body.substring(0, 300) : body);
                            
                            String plate = extractLicensePlate(body);
                            if (plate != null) {
                                logger.info("ANPR Event - Detected Plate: {}", plate);
                                if (plateCallback != null) {
                                    plateCallback.accept(plate);
                                }
                            }
                        }
                    } else if (response.code() != 404) {
                        // Log non-404 errors (404 just means endpoint doesn't exist)
                        logger.debug("Endpoint {} returned {}", endpoint, response.code());
                    }
                }
            } catch (Exception e) {
                // Silently ignore connection errors - these are just attempts
            }
        }
    }
    
    /**
     * Process event response and extract license plates
     */
    private void processEventResponse(String xml) {
        if (xml == null || xml.isEmpty()) return;
        
        // Check if there are any notification messages
        if (!xml.contains("NotificationMessage") && !xml.contains("Message") && !xml.contains("Plate")) {
            return; // No events
        }
        
        logger.debug("Event response received: {}", xml.length() > 500 ? xml.substring(0, 500) + "..." : xml);
        
        String plate = extractLicensePlate(xml);
        
        if (plate != null && !plate.isEmpty() && !"Unknown".equals(plate)) {
            logger.info("ANPR Event - Detected Plate: {}", plate);
            
            if (plateCallback != null) {
                plateCallback.accept(plate);
            }
        }
    }
    
    /**
     * Extract license plate from response (XML or JSON)
     */
    private String extractLicensePlate(String content) {
        if (content == null) return null;
        
        // Pattern 1: SimpleItem with various plate field names (ONVIF)
        String[] plateFields = {"LicensePlate", "PlateNumber", "VehiclePlate", "Plate", "NumberPlate", "RegNumber", "plateNumber"};
        
        for (String field : plateFields) {
            // XML attribute format: Name="field" Value="plate"
            Pattern pattern = Pattern.compile(
                "Name=[\"']" + field + "[\"']\\s+Value=[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            
            // Reversed: Value="plate" Name="field"
            pattern = Pattern.compile(
                "Value=[\"']([^\"']+)[\"']\\s+Name=[\"']" + field + "[\"']",
                Pattern.CASE_INSENSITIVE
            );
            matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            
            // XML element: <PlateNumber>ABC123</PlateNumber>
            pattern = Pattern.compile(
                "<[^>]*" + field + "[^>]*>([^<]+)</",
                Pattern.CASE_INSENSITIVE
            );
            matcher = pattern.matcher(content);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                if (!value.isEmpty() && value.length() >= 4 && value.length() <= 15) {
                    return value;
                }
            }
            
            // JSON format: "plateNumber": "ABC123"
            pattern = Pattern.compile(
                "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"",
                Pattern.CASE_INSENSITIVE
            );
            matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        
        // Pattern 2: Look for typical license plate patterns
        // Indian plates: MH12AB1234, GJ01XY5678
        Pattern platePattern = Pattern.compile("[A-Z]{2}\\d{2}[A-Z]{1,2}\\d{4}");
        Matcher matcher = platePattern.matcher(content.toUpperCase());
        if (matcher.find()) {
            return matcher.group();
        }
        
        return null;
    }

    /**
     * Renew subscription if expired
     */
    private void renewSubscription() {
        try {
            logger.info("Renewing subscription...");
            tryCreateSubscription();
        } catch (Exception e) {
            logger.error("Failed to renew subscription", e);
            useAlternativePolling = true;
        }
    }
    
    /**
     * Unsubscribe from events
     */
    private void unsubscribe() throws IOException {
        if (subscriptionAddress == null) return;
        
        try {
            String soapRequest = buildUnsubscribeRequest();
            sendSoapRequest(subscriptionAddress, soapRequest,
                "http://docs.oasis-open.org/wsn/bw-2/SubscriptionManager/UnsubscribeRequest");
        } catch (Exception e) {
            logger.debug("Unsubscribe error: {}", e.getMessage());
        }
    }
    
    /**
     * Send SOAP request and return response
     */
    private String sendSoapRequest(String url, String soapBody, String soapAction) throws IOException {
        // Use SOAP 1.2 (camera uses this)
        String contentType = "application/soap+xml; charset=utf-8; action=\"" + soapAction + "\"";
        
        // Add HTTP Basic Auth
        String credentials = Credentials.basic(username, password);
        
        RequestBody body = RequestBody.create(soapBody, MediaType.parse("application/soap+xml; charset=utf-8"));
        
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", contentType)
                .addHeader("Authorization", credentials)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (response.isSuccessful()) {
                return responseBody;
            }
            
            // Log full error for debugging
            if (response.code() == 400 || response.code() == 500) {
                logger.warn("SOAP Fault from {} ({}): {}", url, response.code(), responseBody);
                // Return the fault so we can analyze it
                return responseBody;
            }
            
            logger.debug("SOAP request to {} returned {}: {}", url, response.code(),
                responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody);
        }
        
        return null;
    }
    
    // ==================== SOAP Request Builders (SOAP 1.2) ====================
    
    private String buildGetCapabilitiesRequest() {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="%s">
                <s:Header>
                    %s
                </s:Header>
                <s:Body xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
                    <tds:GetCapabilities>
                        <tds:Category>All</tds:Category>
                    </tds:GetCapabilities>
                </s:Body>
            </s:Envelope>
            """, SOAP_ENV_12, buildSecurityHeader());
    }
    
    private String buildCreatePullPointSubscriptionRequest() {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="%s" xmlns:tev="http://www.onvif.org/ver10/events/wsdl">
                <s:Header>
                    %s
                </s:Header>
                <s:Body>
                    <tev:CreatePullPointSubscription>
                        <tev:InitialTerminationTime>PT1H</tev:InitialTerminationTime>
                    </tev:CreatePullPointSubscription>
                </s:Body>
            </s:Envelope>
            """, SOAP_ENV_12, buildSecurityHeader());
    }
    
    private String buildPullMessagesRequest() {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="%s" xmlns:tev="http://www.onvif.org/ver10/events/wsdl">
                <s:Header>
                    %s
                </s:Header>
                <s:Body>
                    <tev:PullMessages>
                        <tev:Timeout>PT5S</tev:Timeout>
                        <tev:MessageLimit>10</tev:MessageLimit>
                    </tev:PullMessages>
                </s:Body>
            </s:Envelope>
            """, SOAP_ENV_12, buildSecurityHeader());
    }
    
    private String buildGetEventPropertiesRequest() {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="%s" xmlns:tev="http://www.onvif.org/ver10/events/wsdl">
                <s:Header>
                    %s
                </s:Header>
                <s:Body>
                    <tev:GetEventProperties/>
                </s:Body>
            </s:Envelope>
            """, SOAP_ENV_12, buildSecurityHeader());
    }
    
    private String buildUnsubscribeRequest() {
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="%s" xmlns:wsnt="http://docs.oasis-open.org/wsn/b-2">
                <s:Header>
                    %s
                </s:Header>
                <s:Body>
                    <wsnt:Unsubscribe/>
                </s:Body>
            </s:Envelope>
            """, SOAP_ENV_12, buildSecurityHeader());
    }
    
    /**
     * Build WS-Security header with UsernameToken (digest authentication)
     */
    private String buildSecurityHeader() {
        try {
            // Generate nonce
            byte[] nonceBytes = new byte[16];
            new SecureRandom().nextBytes(nonceBytes);
            String nonce = Base64.getEncoder().encodeToString(nonceBytes);
            
            // Generate timestamp
            String created = Instant.now().atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
            
            // Calculate password digest: Base64(SHA1(nonce + created + password))
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(nonceBytes);
            sha1.update(created.getBytes(StandardCharsets.UTF_8));
            sha1.update(password.getBytes(StandardCharsets.UTF_8));
            String passwordDigest = Base64.getEncoder().encodeToString(sha1.digest());
            
            return String.format("""
                <wsse:Security xmlns:wsse="%s" xmlns:wsu="%s">
                    <wsse:UsernameToken>
                        <wsse:Username>%s</wsse:Username>
                        <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">%s</wsse:Password>
                        <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">%s</wsse:Nonce>
                        <wsu:Created>%s</wsu:Created>
                    </wsse:UsernameToken>
                </wsse:Security>
                """, WSSE, WSU, username, passwordDigest, nonce, created);
        } catch (Exception e) {
            logger.error("Error building security header", e);
            return "";
        }
    }
    
    /**
     * Extract event service URL from GetCapabilities response
     */
    private String extractEventServiceUrl(String xml) {
        // Look for Events XAddr
        Pattern pattern = Pattern.compile("<[^>]*Events[^>]*>[^<]*<[^>]*XAddr[^>]*>([^<]+)</", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // Alternative pattern
        pattern = Pattern.compile("XAddr>([^<]*event[^<]*)</", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(xml);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        return null;
    }
    
    /**
     * Extract subscription address from CreatePullPointSubscription response
     */
    private String extractSubscriptionAddress(String xml) {
        // Look for SubscriptionReference/Address
        Pattern pattern = Pattern.compile("<[^>]*Address[^>]*>([^<]+)</", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            String address = matcher.group(1).trim();
            if (address.startsWith("http")) {
                return address;
            }
        }
        return null;
    }
}
