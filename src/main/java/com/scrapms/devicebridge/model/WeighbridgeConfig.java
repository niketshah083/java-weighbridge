package com.scrapms.devicebridge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WeighbridgeConfig {
    
    private Integer id;
    private Integer weighbridgeMasterId;
    private Integer tenantId;
    private String serialPort;
    private Integer baudRate;
    private Integer dataBits;
    private Integer stopBits;
    private String parity;
    private String weightRegex;
    private String weightStartMarker;
    private String weightEndMarker;
    private Double weightMultiplier;
    private String weightUnit;
    private Integer pollingInterval;
    private Integer stableReadings;
    private Double stabilityThreshold;
    private Boolean isActive;

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getWeighbridgeMasterId() {
        return weighbridgeMasterId;
    }

    public void setWeighbridgeMasterId(Integer weighbridgeMasterId) {
        this.weighbridgeMasterId = weighbridgeMasterId;
    }

    public Integer getTenantId() {
        return tenantId;
    }

    public void setTenantId(Integer tenantId) {
        this.tenantId = tenantId;
    }

    public String getSerialPort() {
        return serialPort;
    }

    public void setSerialPort(String serialPort) {
        this.serialPort = serialPort;
    }

    public Integer getBaudRate() {
        return baudRate != null ? baudRate : 9600;
    }

    public void setBaudRate(Integer baudRate) {
        this.baudRate = baudRate;
    }

    public Integer getDataBits() {
        return dataBits != null ? dataBits : 8;
    }

    public void setDataBits(Integer dataBits) {
        this.dataBits = dataBits;
    }

    public Integer getStopBits() {
        return stopBits != null ? stopBits : 1;
    }

    public void setStopBits(Integer stopBits) {
        this.stopBits = stopBits;
    }

    public String getParity() {
        return parity != null ? parity : "none";
    }

    public void setParity(String parity) {
        this.parity = parity;
    }

    public String getWeightRegex() {
        return weightRegex;
    }

    public void setWeightRegex(String weightRegex) {
        this.weightRegex = weightRegex;
    }

    public String getWeightStartMarker() {
        return weightStartMarker;
    }

    public void setWeightStartMarker(String weightStartMarker) {
        this.weightStartMarker = weightStartMarker;
    }

    public String getWeightEndMarker() {
        return weightEndMarker;
    }

    public void setWeightEndMarker(String weightEndMarker) {
        this.weightEndMarker = weightEndMarker;
    }

    public Double getWeightMultiplier() {
        return weightMultiplier != null ? weightMultiplier : 1.0;
    }

    public void setWeightMultiplier(Double weightMultiplier) {
        this.weightMultiplier = weightMultiplier;
    }

    public String getWeightUnit() {
        return weightUnit != null ? weightUnit : "kg";
    }

    public void setWeightUnit(String weightUnit) {
        this.weightUnit = weightUnit;
    }

    public Integer getPollingInterval() {
        return pollingInterval != null ? pollingInterval : 1000;
    }

    public void setPollingInterval(Integer pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    public Integer getStableReadings() {
        return stableReadings != null ? stableReadings : 3;
    }

    public void setStableReadings(Integer stableReadings) {
        this.stableReadings = stableReadings;
    }

    public Double getStabilityThreshold() {
        return stabilityThreshold != null ? stabilityThreshold : 5.0;
    }

    public void setStabilityThreshold(Double stabilityThreshold) {
        this.stabilityThreshold = stabilityThreshold;
    }

    public Boolean getIsActive() {
        return isActive != null ? isActive : true;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
