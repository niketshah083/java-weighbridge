package com.scrapms.devicebridge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CameraConfig {
    
    private Integer id;
    private Integer cameraMasterId;
    private Integer tenantId;
    private String rtspUrl;
    private String streamUrl;
    private String username;
    private String password;
    private Integer snapshotWidth;
    private Integer snapshotHeight;
    private Integer snapshotQuality;
    private String transport;
    private Integer timeout;
    private Boolean isActive;

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getCameraMasterId() {
        return cameraMasterId;
    }

    public void setCameraMasterId(Integer cameraMasterId) {
        this.cameraMasterId = cameraMasterId;
    }

    public Integer getTenantId() {
        return tenantId;
    }

    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }

    public String getRtspUrl() {
        return rtspUrl;
    }

    public void setRtspUrl(String rtspUrl) {
        this.rtspUrl = rtspUrl;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public void setStreamUrl(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getSnapshotWidth() {
        return snapshotWidth != null ? snapshotWidth : 640;
    }

    public void setSnapshotWidth(Integer snapshotWidth) {
        this.snapshotWidth = snapshotWidth;
    }

    public Integer getSnapshotHeight() {
        return snapshotHeight != null ? snapshotHeight : 480;
    }

    public void setSnapshotHeight(Integer snapshotHeight) {
        this.snapshotHeight = snapshotHeight;
    }

    public Integer getSnapshotQuality() {
        return snapshotQuality != null ? snapshotQuality : 80;
    }

    public void setSnapshotQuality(Integer snapshotQuality) {
        this.snapshotQuality = snapshotQuality;
    }

    public String getTransport() {
        return transport != null ? transport : "tcp";
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public Integer getTimeout() {
        return timeout != null ? timeout : 10;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Boolean getIsActive() {
        return isActive != null ? isActive : true;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
    
    /**
     * Get the effective camera URL (HTTP stream preferred over RTSP)
     */
    public String getEffectiveUrl() {
        // Prefer HTTP stream URL if set (easier to work with)
        if (streamUrl != null && !streamUrl.trim().isEmpty()) {
            return streamUrl;
        }
        // Fall back to RTSP URL
        return rtspUrl;
    }
}
