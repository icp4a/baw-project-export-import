/* Copyright contributors to the IBM BAW Project Export Import project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
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