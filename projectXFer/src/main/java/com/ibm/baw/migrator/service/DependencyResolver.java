package com.ibm.baw.migrator.service;

import com.ibm.baw.migrator.client.BAWApiClient;
import com.ibm.baw.migrator.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves toolkit dependencies for Process Apps using the what_used API endpoint
 * This provides the complete dependency tree in a single API call
 */
public class DependencyResolver {
    private static final Logger logger = LoggerFactory.getLogger(DependencyResolver.class);
    private final BAWApiClient apiClient;
    private final Map<String, Project> projectCache = new HashMap<>();
    private final Map<String, List<Snapshot>> snapshotCache = new HashMap<>();
    private final Map<String, String> acronymToIdMap = new HashMap<>();
    private boolean acronymMapInitialized = false;
    private final boolean ignoreBranches;

    public DependencyResolver(BAWApiClient apiClient, boolean ignoreBranches) {
        this.apiClient = apiClient;
        this.ignoreBranches = ignoreBranches;
    }

    /**
     * Resolve all toolkit dependencies for a project
     * Returns a list of ToolkitDependency objects ordered by dependency depth (leaf-first)
     */
    public List<ToolkitDependency> resolveDependencies(Project project) throws IOException {
        logger.info("Resolving dependencies for project: {}", project.getDisplayName());
        
        // Use a map to collect all unique toolkit dependencies
        Map<String, ToolkitDependency> dependencyMap = new HashMap<>();
        
        // Get branches to process
        List<Branch> branchesToProcess = getBranchesToProcess(project);
        
        // Process each branch
        for (Branch branch : branchesToProcess) {
            logger.info("Processing dependencies from branch: {} for project: {}",
                       branch.getName(), project.getDisplayName());
            
            // Get all snapshots for this branch
            SnapshotsResponse snapshotsResponse = apiClient.getSnapshots(
                project.getId(),
                branch.getName()
            );
            
            if (snapshotsResponse.getSnapshots() == null || snapshotsResponse.getSnapshots().isEmpty()) {
                logger.warn("No snapshots found for project: {} on branch: {}",
                           project.getDisplayName(), branch.getName());
                continue;
            }

            // Process each snapshot to build the complete dependency tree
            for (Snapshot snapshot : snapshotsResponse.getSnapshots()) {
                processSnapshotDependencies(project, snapshot, branch.getName(), dependencyMap);
            }
        }

        // Convert map to list and calculate depths
        List<ToolkitDependency> allDependencies = new ArrayList<>(dependencyMap.values());
        calculateDepths(allDependencies);

        // Sort by depth (descending) so leaf nodes come first
        allDependencies.sort((a, b) -> Integer.compare(b.getDepth(), a.getDepth()));

        logger.info("Found {} toolkit dependencies for project: {}", 
                    allDependencies.size(), project.getDisplayName());
        
        return allDependencies;
    }

    /**
     * Process dependencies for a single snapshot using the Artifact Management API
     */
    private void processSnapshotDependencies(Project project, Snapshot snapshot, String branchName,
                                             Map<String, ToolkitDependency> dependencyMap) throws IOException {
        
        // Use the project acronym as container acronym and snapshot name as version acronym
        String containerAcronym = project.getAcronym();
        String versionAcronym = snapshot.getName();
        
        if (containerAcronym == null || versionAcronym == null) {
            logger.warn("Invalid project or snapshot name: project={}, snapshot={}",
                       containerAcronym, versionAcronym);
            return;
        }
        
        try {
            // Get snapshot with dependencies using Artifact Management API
            Snapshot snapshotWithDeps = apiClient.getSnapshotWithDependencies(containerAcronym, versionAcronym);
            
            if (snapshotWithDeps.getDependencies() == null || snapshotWithDeps.getDependencies().isEmpty()) {
                logger.debug("No dependencies found for snapshot: {}", versionAcronym);
                return;
            }
            
            // Process each direct dependency recursively
            // Start with depth 1 for direct dependencies of the Process App
            // The API returns Project objects (containers) without snapshot information
            // We need to process ALL snapshots of each dependent toolkit from ALL branches
            for (Project dependency : snapshotWithDeps.getDependencies()) {
                processDependency(dependency, dependencyMap, 1);
            }
            
        } catch (IOException e) {
            logger.error("Failed to get dependencies for snapshot: container={}, version={}",
                        containerAcronym, versionAcronym, e);
        }
    }

    /**
     * Recursively process a dependency and its nested dependencies
     * The API returns Project objects (containers) with only the acronym populated,
     * so we need to fetch the full project details first, then iterate through
     * ALL snapshots of each dependent toolkit to accumulate all nested dependencies.
     */
    private void processDependency(Project dependencyProject,
                                   Map<String, ToolkitDependency> dependencyMap,
                                   int depth) throws IOException {
        
        // Get container acronym from the project (this is the only field populated by the dependencies API)
        String containerAcronym = dependencyProject.getAcronym();
        
        if (containerAcronym == null) {
            logger.warn("Dependency project has no acronym");
            return;
        }
        
        logger.debug("Processing dependency: {} at depth: {}", containerAcronym, depth);
        
        // Fetch the full project details using the acronym
        Project fullProject = getProjectFromCache(containerAcronym);
        if (fullProject == null) {
            logger.warn("Could not fetch project details for dependency: {}", containerAcronym);
            return;
        }
        
        // Skip if not a toolkit
        if (!fullProject.isToolkit()) {
            logger.debug("Skipping non-toolkit dependency: {}", fullProject.getName());
            return;
        }
        
        // Skip system toolkits
        if (fullProject.isSystemToolkit()) {
            logger.debug("Skipping system toolkit: {}", fullProject.getName());
            return;
        }
        
        // Get branches to process for this toolkit
        List<Branch> branchesToProcess = getBranchesToProcess(fullProject);
        
        // Create a unique key for this toolkit (using just container acronym since we'll process all snapshots)
        String toolkitKey = containerAcronym;
        
        // Check if we already have this toolkit in the dependency map
        ToolkitDependency existingDep = dependencyMap.get(toolkitKey);
        
        if (existingDep == null) {
            // Create new dependency using the full project details
            existingDep = new ToolkitDependency(fullProject);
            existingDep.setDepth(depth);
            dependencyMap.put(toolkitKey, existingDep);
            logger.debug("Added toolkit dependency: {} at depth: {}", fullProject.getName(), depth);
        } else {
            // Update depth if current is deeper (higher depth = deeper in tree = should be processed first)
            if (depth > existingDep.getDepth()) {
                existingDep.setDepth(depth);
                logger.debug("Updated depth for toolkit: {} to {}", containerAcronym, depth);
            }
        }
        
        // Process all branches for this toolkit
        Set<String> processedNestedToolkits = new HashSet<>();
        
        for (Branch branch : branchesToProcess) {
            // Get all snapshots for this branch
            List<Snapshot> toolkitSnapshots = getSnapshotsFromCache(
                fullProject.getId(),
                branch.getName()
            );
            
            if (toolkitSnapshots == null || toolkitSnapshots.isEmpty()) {
                logger.warn("No snapshots found for toolkit: {} on branch: {}",
                           fullProject.getName(), branch.getName());
                continue;
            }
            
            // Add snapshots for this branch
            existingDep.addBranchSnapshots(branch.getName(), sortSnapshotsByDate(toolkitSnapshots));
            logger.debug("Added {} snapshots from branch: {} for toolkit: {}",
                        toolkitSnapshots.size(), branch.getName(), fullProject.getName());
            
            // Now iterate through ALL snapshots of this branch to find all nested dependencies
            for (Snapshot snapshot : toolkitSnapshots) {
            String snapshotAcronym = snapshot.getName();
            
            if (snapshotAcronym == null) {
                logger.warn("Snapshot has no name/acronym for toolkit: {}", containerAcronym);
                continue;
            }
            
            try {
                // Get this snapshot's dependencies
                Snapshot snapshotWithDeps = apiClient.getSnapshotWithDependencies(containerAcronym, snapshotAcronym);
                
                if (snapshotWithDeps.getDependencies() != null && !snapshotWithDeps.getDependencies().isEmpty()) {
                    logger.debug("Snapshot {} of toolkit {} has {} dependencies",
                                snapshotAcronym, containerAcronym, snapshotWithDeps.getDependencies().size());
                    
                    // Process each nested dependency (which are also Project objects)
                    for (Project nestedDep : snapshotWithDeps.getDependencies()) {
                        String nestedContainerAcronym = nestedDep.getAcronym();
                        
                        // Only process each nested toolkit once (avoid redundant processing)
                        if (nestedContainerAcronym != null && !processedNestedToolkits.contains(nestedContainerAcronym)) {
                            processedNestedToolkits.add(nestedContainerAcronym);
                            
                            // Recursively process with increased depth
                            processDependency(nestedDep, dependencyMap, depth + 1);
                        }
                    }
                }
            } catch (IOException e) {
                logger.warn("Failed to get dependencies for snapshot: container={}, version={}",
                           containerAcronym, snapshotAcronym, e);
            }
            }
        }
    }

    /**
     * Get the list of branches to process for a project
     */
    private List<Branch> getBranchesToProcess(Project project) throws IOException {
        if (ignoreBranches) {
            // Only process the default branch
            Branch defaultBranch = new Branch();
            defaultBranch.setName(project.getDefaultBranchName());
            defaultBranch.setDefault(true);
            return Collections.singletonList(defaultBranch);
        } else {
            // Process all branches
            BranchesResponse branchesResponse = apiClient.getBranches(project.getId());
            
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
            
            logger.debug("Processing {} branches for project: {} (default branch: {} will be processed first)",
                        branches.size(), project.getDisplayName(), defaultBranchName);
            return branches;
        }
    }

    /**
     * Initialize the acronym-to-ID map by fetching all projects
     */
    private void initializeAcronymMap() throws IOException {
        if (acronymMapInitialized) {
            return;
        }
        
        logger.info("Initializing acronym-to-ID map by fetching all projects...");
        ProjectsResponse response = apiClient.getProjects();
        
        if (response.getProjects() != null) {
            for (Project project : response.getProjects()) {
                String acronym = project.getAcronym();
                String id = project.getId();
                
                if (acronym != null && id != null) {
                    acronymToIdMap.put(acronym, id);
                    // Also cache the project
                    projectCache.put(id, project);
                }
            }
            logger.info("Initialized acronym map with {} projects", acronymToIdMap.size());
        }
        
        acronymMapInitialized = true;
    }
    
    /**
     * Get project from cache or fetch from API using container acronym
     * First converts acronym to ID using the acronym map
     */
    private Project getProjectFromCache(String containerAcronym) throws IOException {
        // Initialize acronym map if needed
        if (!acronymMapInitialized) {
            initializeAcronymMap();
        }
        
        // Look up project ID from acronym
        String projectId = acronymToIdMap.get(containerAcronym);
        if (projectId == null) {
            logger.warn("No project ID found for acronym: {}", containerAcronym);
            return null;
        }
        
        // Check if project is in cache
        if (!projectCache.containsKey(projectId)) {
            try {
                Project project = apiClient.getProject(projectId);
                projectCache.put(projectId, project);
            } catch (IOException e) {
                logger.error("Failed to fetch project with ID: {}", projectId, e);
                return null;
            }
        }
        return projectCache.get(projectId);
    }

    /**
     * Get snapshots from cache or fetch from API
     */
    private List<Snapshot> getSnapshotsFromCache(String projectId, String branchName) throws IOException {
        String cacheKey = projectId + ":" + branchName;
        if (!snapshotCache.containsKey(cacheKey)) {
            SnapshotsResponse response = apiClient.getSnapshots(projectId, branchName);
            List<Snapshot> snapshots = response.getSnapshots() != null ? 
                response.getSnapshots() : Collections.emptyList();
            snapshotCache.put(cacheKey, snapshots);
        }
        return snapshotCache.get(cacheKey);
    }

    /**
     * Sort snapshots by creation date (oldest first)
     */
    private List<Snapshot> sortSnapshotsByDate(List<Snapshot> snapshots) {
        return snapshots.stream()
            .sorted(Comparator.comparing(Snapshot::getCreationDate, Comparator.nullsLast(String::compareTo)))
            .collect(Collectors.toList());
    }

    /**
     * Calculate proper depths for all dependencies based on their relationships
     * This ensures that dependencies are ordered correctly (leaf-first)
     */
    private void calculateDepths(List<ToolkitDependency> dependencies) {
        // Build a map for quick lookup
        Map<String, ToolkitDependency> depMap = new HashMap<>();
        for (ToolkitDependency dep : dependencies) {
            depMap.put(dep.getProject().getId(), dep);
        }
        
        // Multiple passes to ensure all depths are correctly calculated
        boolean changed = true;
        int maxIterations = 100;
        int iteration = 0;
        
        while (changed && iteration < maxIterations) {
            changed = false;
            iteration++;
            
            for (ToolkitDependency dep : dependencies) {
                // Check if this dependency has any dependencies itself
                // by looking at its project's dependencies
                if (dep.getDependencies() != null) {
                    for (ToolkitDependency childDep : dep.getDependencies()) {
                        int newDepth = dep.getDepth() + 1;
                        if (newDepth > childDep.getDepth()) {
                            childDep.setDepth(newDepth);
                            changed = true;
                        }
                    }
                }
            }
        }
        
        logger.debug("Calculated depths in {} iterations", iteration);
    }

    /**
     * Clear caches
     */
    public void clearCache() {
        projectCache.clear();
        snapshotCache.clear();
        acronymToIdMap.clear();
        acronymMapInitialized = false;
    }
}

// Made with Bob
