package com.ibm.baw.migrator.service;

import com.ibm.baw.migrator.client.BAWApiClient;
import com.ibm.baw.migrator.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Service for migrating Process Apps and their dependencies between systems
 */
public class MigrationService {
    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class);
    
    private final BAWApiClient sourceClient;
    private final BAWApiClient targetClient;
    private final DependencyResolver dependencyResolver;
    private final File exportDirectory;
    private final boolean ignoreBranches;

    public MigrationService(BAWApiClient sourceClient, BAWApiClient targetClient, File exportDirectory, boolean ignoreBranches) {
        this.sourceClient = sourceClient;
        this.targetClient = targetClient;
        this.dependencyResolver = new DependencyResolver(sourceClient, ignoreBranches);
        this.exportDirectory = exportDirectory;
        this.ignoreBranches = ignoreBranches;
        
        if (!exportDirectory.exists()) {
            exportDirectory.mkdirs();
        }
    }

    /**
     * Migrate all Process Apps from source to target
     */
    public void migrateAllProcessApps() throws IOException {
        logger.info("Starting migration of all Process Apps");
        
        // Get all projects from source
        ProjectsResponse response = sourceClient.getProjects();
        List<Project> processApps = new ArrayList<>();
        
        for (Project project : response.getProjects()) {
            if ("processapp".equals(project.getType()) && !project.isToolkit()) {
                processApps.add(project);
            }
        }
        
        logger.info("Found {} Process Apps to migrate", processApps.size());
        
        for (Project processApp : processApps) {
            try {
                migrateProcessApp(processApp);
            } catch (Exception e) {
                logger.error("Failed to migrate Process App: {}", processApp.getDisplayName(), e);
            }
        }
        
        logger.info("Migration completed");
    }

    /**
     * Migrate a specific Process App with all its dependencies
     */
    public void migrateProcessApp(Project processApp) throws IOException {
        logger.info("Migrating Process App: {}", processApp.getDisplayName());
        
        // Step 1: Resolve all toolkit dependencies
        List<ToolkitDependency> dependencies = dependencyResolver.resolveDependencies(processApp);
        
        // Step 2: Export and import toolkits in order (leaf-first)
        Map<String, Project> importedProjects = new HashMap<>();
        
        for (ToolkitDependency dependency : dependencies) {
            try {
                Project importedToolkit = migrateToolkit(dependency);
                importedProjects.put(dependency.getProject().getId(), importedToolkit);
            } catch (Exception e) {
                logger.error("Failed to migrate toolkit: {}",
                           dependency.getProject().getDisplayName(), e);
                throw new IOException("Failed to migrate required toolkit: " +
                                    dependency.getProject().getDisplayName(), e);
            }
        }
        
        // Step 3: Export and import the Process App itself
        logger.info("Migrating Process App snapshots: {}", processApp.getDisplayName());
        
        // Get branches to process
        List<Branch> branchesToProcess = getBranchesToProcess(processApp);
        
        for (Branch branch : branchesToProcess) {
            logger.info("Processing branch: {} for Process App: {}", branch.getName(), processApp.getDisplayName());
            
            SnapshotsResponse snapshotsResponse = sourceClient.getSnapshots(
                processApp.getId(),
                branch.getName()
            );
            
            if (snapshotsResponse.getSnapshots() == null || snapshotsResponse.getSnapshots().isEmpty()) {
                logger.warn("No snapshots found for Process App: {} on branch: {}",
                           processApp.getDisplayName(), branch.getName());
                continue;
            }
            
            // Sort snapshots by creation date (oldest first)
            List<Snapshot> sortedSnapshots = new ArrayList<>(snapshotsResponse.getSnapshots());
            sortedSnapshots.sort(Comparator.comparing(Snapshot::getCreationDate,
                                                      Comparator.nullsLast(String::compareTo)));
            
            for (Snapshot snapshot : sortedSnapshots) {
                try {
                    exportAndImportSnapshot(processApp, snapshot, branch.getName());
                } catch (Exception e) {
                    logger.error("Failed to migrate snapshot: {} of Process App: {} on branch: {}",
                               snapshot.getDisplayName(), processApp.getDisplayName(), branch.getName(), e);
                }
            }
        }
        
        logger.info("Successfully migrated Process App: {}", processApp.getDisplayName());
    }

    /**
     * Migrate a toolkit with all its versions
     */
    private Project migrateToolkit(ToolkitDependency dependency) throws IOException {
        Project toolkit = dependency.getProject();
        logger.info("Migrating toolkit: {} with snapshots from {} branches",
                   toolkit.getDisplayName(), dependency.getBranchSnapshots().size());
        
        // Check if toolkit already exists on target
        Project existingToolkit = findProjectOnTarget(toolkit.getName());
        if (existingToolkit != null) {
            logger.info("Toolkit already exists on target: {}", toolkit.getDisplayName());
            return existingToolkit;
        }
        
        // Sort branch names to ensure default branch is processed first
        List<String> branchNames = new ArrayList<>(dependency.getBranchSnapshots().keySet());
        String defaultBranchName = toolkit.getDefaultBranchName();
        branchNames.sort((b1, b2) -> {
            // Default branch comes first
            boolean b1IsDefault = defaultBranchName.equals(b1);
            boolean b2IsDefault = defaultBranchName.equals(b2);
            if (b1IsDefault && !b2IsDefault) return -1;
            if (!b1IsDefault && b2IsDefault) return 1;
            // Otherwise maintain original order
            return b1.compareTo(b2);
        });
        
        // Export and import all snapshots from all branches in order (default branch first, then oldest first)
        Project importedToolkit = null;
        for (String branchName : branchNames) {
            List<Snapshot> snapshots = dependency.getBranchSnapshots().get(branchName);
            
            logger.info("Processing {} snapshots from branch: {} for toolkit: {} (default: {})",
                       snapshots.size(), branchName, toolkit.getDisplayName(), defaultBranchName);
            
            for (Snapshot snapshot : snapshots) {
                try {
                    importedToolkit = exportAndImportSnapshot(toolkit, snapshot, branchName);
                } catch (Exception e) {
                    logger.error("Failed to migrate snapshot: {} of toolkit: {} on branch: {}",
                               snapshot.getDisplayName(), toolkit.getDisplayName(), branchName, e);
                    throw e;
                }
            }
        }
        
        return importedToolkit;
    }

    /**
     * Export a snapshot from source and import to target
     */
    private Project exportAndImportSnapshot(Project project, Snapshot snapshot, String branchName) throws IOException {
        logger.info("Exporting snapshot: {} from project: {} on branch: {}",
                   snapshot.getDisplayName(), project.getDisplayName(), branchName);
        
        // Export snapshot
        File exportedFile = sourceClient.exportSnapshot(
            project.getId(),
            branchName,
            snapshot.getName(),
            exportDirectory
        );
        
        logger.info("Importing snapshot: {} to target system", snapshot.getDisplayName());
        
        // Import to target
        Project importedProject = targetClient.importProject(exportedFile);
        
        logger.info("Successfully imported snapshot: {} as project: {}", 
                   snapshot.getDisplayName(), importedProject.getDisplayName());
        
        return importedProject;
    }

    /**
     * Find a project on the target system by name
     */
    private Project findProjectOnTarget(String projectName) {
        try {
            ProjectsResponse response = targetClient.getProjects();
            if (response.getProjects() != null) {
                for (Project project : response.getProjects()) {
                    if (projectName.equals(project.getName())) {
                        return project;
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to check for existing project on target: {}", projectName, e);
        }
        return null;
    }

    /**
     * Get the list of branches to process for a project
     */
    private List<Branch> getBranchesToProcess(Project project) throws IOException {
        if (ignoreBranches) {
            // Only process the default branch
            logger.info("Processing only default branch for project: {}", project.getDisplayName());
            Branch defaultBranch = new Branch();
            defaultBranch.setName(project.getDefaultBranchName());
            defaultBranch.setDefault(true);
            return Collections.singletonList(defaultBranch);
        } else {
            // Process all branches
            logger.info("Fetching all branches for project: {}", project.getDisplayName());
            BranchesResponse branchesResponse = sourceClient.getBranches(project.getId());
            
            if (branchesResponse.getBranches() == null || branchesResponse.getBranches().isEmpty()) {
                logger.warn("No branches found for project: {}, using default branch", project.getDisplayName());
                Branch defaultBranch = new Branch();
                defaultBranch.setName(project.getDefaultBranchName());
                defaultBranch.setDefault(true);
                return Collections.singletonList(defaultBranch);
            }
            
            // Sort branches to ensure default branch is processed first
            List<Branch> branches = new ArrayList<>(branchesResponse.getBranches());
            String defaultBranchName = project.getDefaultBranchName();
            branches.sort((b1, b2) -> {
                // Default branch comes first
                boolean b1IsDefault = defaultBranchName.equals(b1.getName());
                boolean b2IsDefault = defaultBranchName.equals(b2.getName());
                if (b1IsDefault && !b2IsDefault) return -1;
                if (!b1IsDefault && b2IsDefault) return 1;
                // Otherwise maintain original order
                return 0;
            });
            
            logger.info("Found {} branches for project: {} (default branch: {} will be processed first)",
                       branches.size(), project.getDisplayName(), defaultBranchName);
            return branches;
        }
    }

    /**
     * Get migration statistics
     */
    public MigrationStats getMigrationStats() {
        // This could be enhanced to track actual migration progress
        return new MigrationStats();
    }

    /**
     * Simple class to hold migration statistics
     */
    public static class MigrationStats {
        private int totalProjects = 0;
        private int successfulProjects = 0;
        private int failedProjects = 0;
        private int totalSnapshots = 0;

        public int getTotalProjects() {
            return totalProjects;
        }

        public void setTotalProjects(int totalProjects) {
            this.totalProjects = totalProjects;
        }

        public int getSuccessfulProjects() {
            return successfulProjects;
        }

        public void setSuccessfulProjects(int successfulProjects) {
            this.successfulProjects = successfulProjects;
        }

        public int getFailedProjects() {
            return failedProjects;
        }

        public void setFailedProjects(int failedProjects) {
            this.failedProjects = failedProjects;
        }

        public int getTotalSnapshots() {
            return totalSnapshots;
        }

        public void setTotalSnapshots(int totalSnapshots) {
            this.totalSnapshots = totalSnapshots;
        }
    }
}

// Made with Bob
