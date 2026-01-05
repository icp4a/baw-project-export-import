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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response model for the what_used API endpoint
 * Represents the full dependency tree for a snapshot
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatUsedResponse {
    
    @JsonProperty("toolkits_used")
    private List<ToolkitVersionUsed> toolkitsUsed;

    public List<ToolkitVersionUsed> getToolkitsUsed() {
        return toolkitsUsed;
    }

    public void setToolkitsUsed(List<ToolkitVersionUsed> toolkitsUsed) {
        this.toolkitsUsed = toolkitsUsed;
    }

    @Override
    public String toString() {
        return "WhatUsedResponse{" +
                "toolkitsUsed=" + (toolkitsUsed != null ? toolkitsUsed.size() : 0) +
                '}';
    }
}

// Made with Bob