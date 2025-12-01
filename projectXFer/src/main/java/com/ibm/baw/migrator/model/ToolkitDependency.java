package com.ibm.baw.migrator.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a toolkit dependency with its snapshots and dependencies
 */
public class ToolkitDependency {
    private Project project;
    private List<Snapshot> snapshots; // Deprecated: kept for backward compatibility
    private Map<String, List<Snapshot>> branchSnapshots; // Branch name -> List of snapshots
    private List<ToolkitDependency> dependencies;
    private int depth; // For tracking dependency depth (leaf nodes have higher depth)

    public ToolkitDependency(Project project) {
        this.project = project;
        this.snapshots = new ArrayList<>();
        this.branchSnapshots = new HashMap<>();
        this.dependencies = new ArrayList<>();
        this.depth = 0;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public List<Snapshot> getSnapshots() {
        return snapshots;
    }

    public void setSnapshots(List<Snapshot> snapshots) {
        this.snapshots = snapshots;
    }

    public Map<String, List<Snapshot>> getBranchSnapshots() {
        return branchSnapshots;
    }

    public void setBranchSnapshots(Map<String, List<Snapshot>> branchSnapshots) {
        this.branchSnapshots = branchSnapshots;
    }

    public void addBranchSnapshots(String branchName, List<Snapshot> snapshots) {
        this.branchSnapshots.put(branchName, snapshots);
    }

    public List<ToolkitDependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<ToolkitDependency> dependencies) {
        this.dependencies = dependencies;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void addDependency(ToolkitDependency dependency) {
        if (!dependencies.contains(dependency)) {
            dependencies.add(dependency);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolkitDependency that = (ToolkitDependency) o;
        return Objects.equals(project.getId(), that.project.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(project.getId());
    }

    @Override
    public String toString() {
        int totalSnapshots = branchSnapshots.values().stream()
                .mapToInt(List::size)
                .sum();
        return "ToolkitDependency{" +
                "project=" + project.getDisplayName() +
                ", branches=" + branchSnapshots.size() +
                ", totalSnapshots=" + totalSnapshots +
                ", dependencyCount=" + dependencies.size() +
                ", depth=" + depth +
                '}';
    }
}

// Made with Bob
