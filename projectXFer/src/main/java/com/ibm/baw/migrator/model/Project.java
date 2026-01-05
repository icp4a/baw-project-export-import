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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {
    @JsonProperty("id")
    private String id;
    
    // The acronym of the project (short name)
    // Different APIs use different field names for the acronym
    @JsonProperty("name")
    private String acronym;
    
    // Alternative acronym field name used by Artifact Management API
    @JsonProperty("container")
    private String containerAcronym;
    
    // The full name of the project
    @JsonProperty("container_name")
    private String fullName;
    
    @JsonProperty("display_name")
    private String displayName;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("toolkit")
    private boolean toolkit;
    
    @JsonProperty("default_branch_id")
    private String defaultBranchId;
    
    @JsonProperty("default_branch_name")
    private String defaultBranchName;
    
    @JsonProperty("properties")
    private List<Property> properties;
    
    @JsonProperty("boolean_properties")
    private List<BooleanProperty> booleanProperties;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // Get the acronym, preferring 'name' field but falling back to 'container' field
    @JsonIgnore
    public String getAcronym() {
        return acronym != null ? acronym : containerAcronym;
    }

    public void setAcronym(String acronym) {
        this.acronym = acronym;
    }

    public void setContainerAcronym(String containerAcronym) {
        this.containerAcronym = containerAcronym;
    }

    // Get the full name
    @JsonIgnore
    public String getName() {
        return fullName != null ? fullName : displayName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getDefaultBranchId() {
        return defaultBranchId;
    }

    public void setDefaultBranchId(String defaultBranchId) {
        this.defaultBranchId = defaultBranchId;
    }

    public String getDefaultBranchName() {
        return defaultBranchName;
    }

    public void setDefaultBranchName(String defaultBranchName) {
        this.defaultBranchName = defaultBranchName;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public List<BooleanProperty> getBooleanProperties() {
        return booleanProperties;
    }

    public void setBooleanProperties(List<BooleanProperty> booleanProperties) {
        this.booleanProperties = booleanProperties;
    }
    
    public boolean isSystemToolkit() {
        if (booleanProperties != null) {
            for (BooleanProperty prop : booleanProperties) {
                if ("system_toolkit".equals(prop.getName()) || "system".equals(prop.getName()) || "system_data".equals(prop.getName())) {
                    return prop.isValue();
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Project{" +
                "id='" + id + '\'' +
                ", acronym='" + getAcronym() + '\'' +
                ", name='" + getName() + '\'' +
                ", displayName='" + displayName + '\'' +
                ", type='" + type + '\'' +
                ", toolkit=" + toolkit +
                '}';
    }
}

// Made with Bob
