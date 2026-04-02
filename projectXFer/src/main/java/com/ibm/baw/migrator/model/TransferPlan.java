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
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a transfer plan containing ordered list of projects and snapshots to migrate
 */
public class TransferPlan {
    @JsonProperty("source_url")
    private String sourceUrl;
    
    @JsonProperty("target_url")
    private String targetUrl;
    
    @JsonProperty("export_dir")
    private String exportDir;
    
    @JsonProperty("ignore_branches")
    private boolean ignoreBranches;
    
    @JsonProperty("created_at")
    private String createdAt;
    
    @JsonProperty("toolkits")
    private List<TransferItem> toolkits = new ArrayList<>();
    
    @JsonProperty("process_apps")
    private List<TransferItem> processApps = new ArrayList<>();

    // Getters and Setters
    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getExportDir() {
        return exportDir;
    }

    public void setExportDir(String exportDir) {
        this.exportDir = exportDir;
    }

    public boolean isIgnoreBranches() {
        return ignoreBranches;
    }

    public void setIgnoreBranches(boolean ignoreBranches) {
        this.ignoreBranches = ignoreBranches;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<TransferItem> getToolkits() {
        return toolkits;
    }

    public void setToolkits(List<TransferItem> toolkits) {
        this.toolkits = toolkits;
    }

    public List<TransferItem> getProcessApps() {
        return processApps;
    }

    public void setProcessApps(List<TransferItem> processApps) {
        this.processApps = processApps;
    }

    /**
     * Represents a single project/toolkit to be transferred with its snapshots
     */
    public static class TransferItem {
        @JsonProperty("project_id")
        private String projectId;
        
        @JsonProperty("project_name")
        private String projectName;
        
        @JsonProperty("project_acronym")
        private String projectAcronym;
        
        @JsonProperty("display_name")
        private String displayName;
        
        @JsonProperty("is_toolkit")
        private boolean isToolkit;
        
        @JsonProperty("depth")
        private int depth;
        
        @JsonProperty("branches")
        private List<BranchSnapshots> branches = new ArrayList<>();

        // Getters and Setters
        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getProjectName() {
            return projectName;
        }

        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }

        public String getProjectAcronym() {
            return projectAcronym;
        }

        public void setProjectAcronym(String projectAcronym) {
            this.projectAcronym = projectAcronym;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public boolean isToolkit() {
            return isToolkit;
        }

        public void setToolkit(boolean toolkit) {
            isToolkit = toolkit;
        }

        public int getDepth() {
            return depth;
        }

        public void setDepth(int depth) {
            this.depth = depth;
        }

        public List<BranchSnapshots> getBranches() {
            return branches;
        }

        public void setBranches(List<BranchSnapshots> branches) {
            this.branches = branches;
        }
    }

    /**
     * Represents snapshots for a specific branch
     */
    public static class BranchSnapshots {
        @JsonProperty("branch_name")
        private String branchName;
        
        @JsonProperty("is_default")
        private boolean isDefault;
        
        @JsonProperty("snapshots")
        private List<SnapshotInfo> snapshots = new ArrayList<>();

        // Getters and Setters
        public String getBranchName() {
            return branchName;
        }

        public void setBranchName(String branchName) {
            this.branchName = branchName;
        }

        public boolean isDefault() {
            return isDefault;
        }

        public void setDefault(boolean aDefault) {
            isDefault = aDefault;
        }

        public List<SnapshotInfo> getSnapshots() {
            return snapshots;
        }

        public void setSnapshots(List<SnapshotInfo> snapshots) {
            this.snapshots = snapshots;
        }
    }

    /**
     * Represents a single snapshot to be transferred
     */
    public static class SnapshotInfo {
        @JsonProperty("snapshot_id")
        private String snapshotId;
        
        @JsonProperty("snapshot_name")
        private String snapshotName;
        
        @JsonProperty("display_name")
        private String displayName;
        
        @JsonProperty("creation_date")
        private String creationDate;
        
        @JsonProperty("target_environment")
        private String targetEnvironment;

        // Getters and Setters
        public String getSnapshotId() {
            return snapshotId;
        }

        public void setSnapshotId(String snapshotId) {
            this.snapshotId = snapshotId;
        }

        public String getSnapshotName() {
            return snapshotName;
        }

        public void setSnapshotName(String snapshotName) {
            this.snapshotName = snapshotName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getCreationDate() {
            return creationDate;
        }

        public void setCreationDate(String creationDate) {
            this.creationDate = creationDate;
        }

        public String getTargetEnvironment() {
            return targetEnvironment;
        }

        public void setTargetEnvironment(String targetEnvironment) {
            this.targetEnvironment = targetEnvironment;
        }
    }
}

// Made with Bob