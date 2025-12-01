package com.ibm.baw.migrator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response object containing the CSRF token from the /system/login endpoint
 */
public class CsrfTokenResponse {
    
    @JsonProperty("csrf_token")
    private String csrfToken;
    
    @JsonProperty("expiration")
    private Long expiration;
    
    public CsrfTokenResponse() {
    }
    
    public String getCsrfToken() {
        return csrfToken;
    }
    
    public void setCsrfToken(String csrfToken) {
        this.csrfToken = csrfToken;
    }
    
    public Long getExpiration() {
        return expiration;
    }
    
    public void setExpiration(Long expiration) {
        this.expiration = expiration;
    }
}

// Made with Bob