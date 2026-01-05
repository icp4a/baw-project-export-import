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
 * Represents a toolkit version that is used by a snapshot
 * Includes nested dependencies (recursive structure)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolkitVersionUsed {
    
    @JsonProperty("container")
    private String container;
    
    @JsonProperty("container_name")
    private String containerName;
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("toolkit")
    private Boolean toolkit;
    
    @JsonProperty("template")
    private Boolean template;
    
    @JsonProperty("snapshot_name")
    private String snapshotName;
    
    @JsonProperty("snapshot")
    private String snapshot;
    
    @JsonProperty("branch_acronym")
    private String branchAcronym;
    
    @JsonProperty("latest_snapshot")
    private Boolean latestSnapshot;
    
    @JsonProperty("version_discrepency")
    private Boolean versionDiscrepancy;
    
    @JsonProperty("user_has_permission")
    private Boolean userHasPermission;
    
    // Recursive: toolkits used by this toolkit
    @JsonProperty("toolkits_used")
    private List<ToolkitVersionUsed> toolkitsUsed;

    // Getters and Setters
    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getToolkit() {
        return toolkit;
    }

    public void setToolkit(Boolean toolkit) {
        this.toolkit = toolkit;
    }

    public Boolean getTemplate() {
        return template;
    }

    public void setTemplate(Boolean template) {
        this.template = template;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public void setSnapshotName(String snapshotName) {
        this.snapshotName = snapshotName;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }

    public String getBranchAcronym() {
        return branchAcronym;
    }

    public void setBranchAcronym(String branchAcronym) {
        this.branchAcronym = branchAcronym;
    }

    public Boolean getLatestSnapshot() {
        return latestSnapshot;
    }

    public void setLatestSnapshot(Boolean latestSnapshot) {
        this.latestSnapshot = latestSnapshot;
    }

    public Boolean getVersionDiscrepancy() {
        return versionDiscrepancy;
    }

    public void setVersionDiscrepancy(Boolean versionDiscrepancy) {
        this.versionDiscrepancy = versionDiscrepancy;
    }

    public Boolean getUserHasPermission() {
        return userHasPermission;
    }

    public void setUserHasPermission(Boolean userHasPermission) {
        this.userHasPermission = userHasPermission;
    }

    public List<ToolkitVersionUsed> getToolkitsUsed() {
        return toolkitsUsed;
    }

    public void setToolkitsUsed(List<ToolkitVersionUsed> toolkitsUsed) {
        this.toolkitsUsed = toolkitsUsed;
    }

    @Override
    public String toString() {
        return "ToolkitVersionUsed{" +
                "container='" + container + '\'' +
                ", containerName='" + containerName + '\'' +
                ", snapshot='" + snapshot + '\'' +
                ", snapshotName='" + snapshotName + '\'' +
                ", nestedDependencies=" + (toolkitsUsed != null ? toolkitsUsed.size() : 0) +
                '}';
    }
}

// Made with Bob