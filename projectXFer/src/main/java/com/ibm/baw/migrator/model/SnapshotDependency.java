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

/**
 * Represents a dependency of a snapshot on another project/toolkit snapshot.
 * This combines both the container (project) information and the specific version (snapshot) being used.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SnapshotDependency {
    
    // Container (Project) information
    @JsonProperty("container")
    private String container;
    
    @JsonProperty("container_name")
    private String containerName;
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("toolkit")
    private boolean toolkit;
    
    @JsonProperty("template")
    private boolean template;
    
    // Snapshot (Version) information
    @JsonProperty("snapshot_name")
    private String snapshotName;
    
    @JsonProperty("snapshot")
    private String snapshot;
    
    @JsonProperty("branch_acronym")
    private String branchAcronym;
    
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

    public boolean isToolkit() {
        return toolkit;
    }

    public void setToolkit(boolean toolkit) {
        this.toolkit = toolkit;
    }

    public boolean isTemplate() {
        return template;
    }

    public void setTemplate(boolean template) {
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

    @Override
    public String toString() {
        return "SnapshotDependency{" +
                "container='" + container + '\'' +
                ", containerName='" + containerName + '\'' +
                ", snapshot='" + snapshot + '\'' +
                ", snapshotName='" + snapshotName + '\'' +
                ", toolkit=" + toolkit +
                '}';
    }
}

// Made with Bob