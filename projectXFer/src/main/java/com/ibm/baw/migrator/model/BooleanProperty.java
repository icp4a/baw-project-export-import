package com.ibm.baw.migrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BooleanProperty {
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("value")
    private boolean value;

    public BooleanProperty() {
    }

    public BooleanProperty(String name, boolean value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "BooleanProperty{" +
                "name='" + name + '\'' +
                ", value=" + value +
                '}';
    }
}

// Made with Bob
