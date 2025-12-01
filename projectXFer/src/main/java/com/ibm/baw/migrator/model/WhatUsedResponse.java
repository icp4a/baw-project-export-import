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