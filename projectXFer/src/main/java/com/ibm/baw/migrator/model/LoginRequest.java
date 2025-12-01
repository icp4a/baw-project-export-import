package com.ibm.baw.migrator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request object for obtaining a CSRF token from the /system/login endpoint
 */
public class LoginRequest {
    
    @JsonProperty("refresh_groups")
    private boolean refreshGroups;
    
    @JsonProperty("requested_lifetime")
    private Integer requestedLifetime;
    
    public LoginRequest() {
        this.refreshGroups = false;
        this.requestedLifetime = 7200; // Default 2 hours
    }
    
    public LoginRequest(boolean refreshGroups, Integer requestedLifetime) {
        this.refreshGroups = refreshGroups;
        this.requestedLifetime = requestedLifetime;
    }
    
    public boolean isRefreshGroups() {
        return refreshGroups;
    }
    
    public void setRefreshGroups(boolean refreshGroups) {
        this.refreshGroups = refreshGroups;
    }
    
    public Integer getRequestedLifetime() {
        return requestedLifetime;
    }
    
    public void setRequestedLifetime(Integer requestedLifetime) {
        this.requestedLifetime = requestedLifetime;
    }
}

// Made with Bob