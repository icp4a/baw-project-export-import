package com.ibm.baw.migrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Snapshot {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("display_name")
    private String displayName;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("branch_name")
    private String branchName;
    
    @JsonProperty("creation_date")
    private String creationDate;
    
    @JsonProperty("properties")
    private List<Property> properties;
    
    @JsonProperty("boolean_properties")
    private List<BooleanProperty> booleanProperties;
    
    // Dependencies are returned as Project objects (containers only, no specific snapshot info)
    @JsonProperty("dependencies")
    private List<Project> dependencies;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
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

    public List<Project> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<Project> dependencies) {
        this.dependencies = dependencies;
    }

    @Override
    public String toString() {
        return "Snapshot{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", branchName='" + branchName + '\'' +
                ", creationDate='" + creationDate + '\'' +
                '}';
    }
}

// Made with Bob
