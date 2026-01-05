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