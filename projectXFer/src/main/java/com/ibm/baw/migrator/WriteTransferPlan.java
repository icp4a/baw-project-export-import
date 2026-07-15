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
package com.ibm.baw.migrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ibm.baw.migrator.client.BAWApiClient;
import com.ibm.baw.migrator.model.*;
import com.ibm.baw.migrator.service.DependencyResolver;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Stage 1: Generates a transfer plan JSON file with ordered list of projects and snapshots
 */
public class WriteTransferPlan {
    private static final Logger logger = LoggerFactory.getLogger(WriteTransferPlan.class);

    public static void main(String[] args) {
        Options options = createOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("help")) {
                printHelp(formatter, options);
                return;
            }

            // Validate required options
            validateRequiredOptions(cmd);

            // Get configuration from command line
            String sourceUrl = cmd.getOptionValue("source-url");
            String sourceUser = cmd.getOptionValue("source-user");
            String sourcePassword = cmd.getOptionValue("source-password");
            
            String targetUrl = cmd.getOptionValue("target-url");
            
            String exportDir = cmd.getOptionValue("export-dir", "./exports");
            String outputFile = cmd.getOptionValue("output", "transfer-plan.json");
            String projectName = cmd.getOptionValue("project");
            String projectsAcronyms = cmd.getOptionValue("projects");
            boolean migrateAll = cmd.hasOption("all");
            boolean ignoreBranches = cmd.hasOption("ignore-branches");
            boolean onlyIncludeRequiredToolkits = cmd.hasOption("only-include-required-toolkits");
            String filterTargetEnvironments = cmd.getOptionValue("filter-target-environments");
            int maxVersions = -1; // -1 means no limit
            if (cmd.hasOption("max-versions")) {
                try {
                    maxVersions = Integer.parseInt(cmd.getOptionValue("max-versions"));
                    if (maxVersions <= 0) {
                        logger.error("max-versions must be a positive integer");
                        System.exit(1);
                    }
                } catch (NumberFormatException e) {
                    logger.error("Invalid max-versions value: {}", cmd.getOptionValue("max-versions"));
                    System.exit(1);
                }
            }
            
            // Parse filter target environments into a set
            Set<String> filteredEnvironments = new HashSet<>();
            if (filterTargetEnvironments != null && !filterTargetEnvironments.isEmpty()) {
                String[] envs = filterTargetEnvironments.split(",");
                for (String env : envs) {
                    String trimmed = env.trim();
                    if (!trimmed.isEmpty()) {
                        filteredEnvironments.add(trimmed);
                    }
                }
                logger.info("Filtering snapshots with target environments: {}", filteredEnvironments);
            }

            // Initialize source API client
            logger.info("Connecting to source system: {}", sourceUrl);
            BAWApiClient sourceClient = new BAWApiClient(sourceUrl, sourceUser, sourcePassword);

            // Create transfer plan
            TransferPlan plan = new TransferPlan();
            plan.setSourceUrl(sourceUrl);
            plan.setTargetUrl(targetUrl);
            plan.setExportDir(exportDir);
            plan.setIgnoreBranches(ignoreBranches);
            plan.setCreatedAt(Instant.now().toString());

            // Create dependency resolver
            // If --only-include-required-toolkits is set, don't apply maxVersions limit to toolkits
            // (it should only apply to Process Apps in that case)
            int toolkitMaxVersions = onlyIncludeRequiredToolkits ? -1 : maxVersions;
            DependencyResolver dependencyResolver = new DependencyResolver(sourceClient, ignoreBranches, toolkitMaxVersions);
            
            if (onlyIncludeRequiredToolkits) {
                if (maxVersions > 0) {
                    logger.info("--only-include-required-toolkits is set: maxVersions ({}) will only apply to Process Apps, not toolkits", maxVersions);
                }
                if (!filteredEnvironments.isEmpty()) {
                    logger.info("--only-include-required-toolkits is set: target environment filtering will only apply to Process Apps, not required toolkits");
                }
            }

            // Determine which projects to process
            List<Project> projectsToMigrate = new ArrayList<>();
            
            if (migrateAll) {
                logger.info("Planning migration of all Process Apps");
                ProjectsResponse response = sourceClient.getProjects();
                for (Project project : response.getProjects()) {
                    if ("processapp".equals(project.getType()) && !project.isToolkit()) {
                        projectsToMigrate.add(project);
                    }
                }
            } else if (projectsAcronyms != null) {
                String[] acronyms = projectsAcronyms.split(",");
                logger.info("Planning migration of {} Process Apps by acronym", acronyms.length);
                
                for (String acronym : acronyms) {
                    acronym = acronym.trim();
                    if (acronym.isEmpty()) {
                        continue;
                    }
                    
                    Project project = findProjectByAcronym(sourceClient, acronym);
                    if (project == null) {
                        logger.error("Process App not found with acronym: {}", acronym);
                        continue;
                    }
                    projectsToMigrate.add(project);
                }
            } else if (projectName != null) {
                logger.info("Planning migration of Process App: {}", projectName);
                Project project = findProjectByName(sourceClient, projectName);
                if (project == null) {
                    logger.error("Process App not found: {}", projectName);
                    System.exit(1);
                }
                projectsToMigrate.add(project);
            } else {
                logger.error("Either --project, --projects, or --all must be specified");
                printHelp(formatter, options);
                System.exit(1);
            }

            logger.info("Found {} Process Apps to include in transfer plan", projectsToMigrate.size());

            // Track all toolkits across all process apps
            Map<String, TransferPlan.TransferItem> allToolkits = new LinkedHashMap<>();
            
            // Track required toolkit snapshots if filtering is enabled
            // Map: toolkit acronym -> Set of required snapshot names
            Map<String, Set<String>> requiredToolkitSnapshots = new HashMap<>();

            // Process each project
            for (Project processApp : projectsToMigrate) {
                try {
                    logger.info("Analyzing Process App: {}", processApp.getDisplayName());
                    
                    // Resolve dependencies
                    List<ToolkitDependency> dependencies = dependencyResolver.resolveDependencies(processApp);
                    
                    // Add toolkits to the plan
                    for (ToolkitDependency dependency : dependencies) {
                        String toolkitKey = dependency.getProject().getId();
                        if (!allToolkits.containsKey(toolkitKey)) {
                            // If --only-include-required-toolkits is set, don't filter toolkits by target environment
                            // since they are required regardless of their target environment
                            Set<String> toolkitFilteredEnvironments = onlyIncludeRequiredToolkits ?
                                Collections.emptySet() : filteredEnvironments;
                            
                            TransferPlan.TransferItem item = createTransferItem(
                                sourceClient,
                                dependency.getProject(),
                                dependency.getBranchSnapshots(),
                                dependency.getDepth(),
                                true,
                                toolkitFilteredEnvironments
                            );
                            allToolkits.put(toolkitKey, item);
                        }
                    }
                    
                    // Add the process app itself
                    TransferPlan.TransferItem processAppItem = createTransferItemForProcessApp(
                        sourceClient,
                        processApp,
                        ignoreBranches,
                        filteredEnvironments,
                        maxVersions
                    );
                    plan.getProcessApps().add(processAppItem);
                    
                    // If filtering is enabled, collect required toolkit snapshots from this Process App
                    if (onlyIncludeRequiredToolkits) {
                        collectRequiredToolkitSnapshots(
                            sourceClient,
                            processApp,
                            processAppItem,
                            requiredToolkitSnapshots
                        );
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to analyze Process App: {}", processApp.getDisplayName(), e);
                }
            }

            // Filter toolkits if requested
            if (onlyIncludeRequiredToolkits) {
                logger.info("Filtering toolkits to only include required versions");
                List<TransferPlan.TransferItem> filteredToolkits = filterRequiredToolkits(
                    allToolkits.values(),
                    requiredToolkitSnapshots
                );
                plan.getToolkits().addAll(filteredToolkits);
                logger.info("Filtered to {} toolkit versions (from {} total toolkits)",
                           plan.getToolkits().size(), allToolkits.size());
            } else {
                // Add all toolkits to the plan (already sorted by depth)
                plan.getToolkits().addAll(allToolkits.values());
            }

            // Write the plan to JSON file
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            
            File outputFileObj = new File(outputFile);
            mapper.writeValue(outputFileObj, plan);
            
            logger.info("Transfer plan written to: {}", outputFileObj.getAbsolutePath());
            logger.info("Plan contains {} toolkits and {} process apps",
                       plan.getToolkits().size(), plan.getProcessApps().size());

            // Close client
            sourceClient.close();

            logger.info("Transfer plan generation completed successfully!");

        } catch (ParseException e) {
            logger.error("Error parsing command line arguments: {}", e.getMessage());
            printHelp(formatter, options);
            System.exit(1);
        } catch (IOException e) {
            logger.error("Failed to generate transfer plan: {}", e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * Create a TransferItem from a toolkit dependency
     */
    private static TransferPlan.TransferItem createTransferItem(
            BAWApiClient client,
            Project project,
            Map<String, List<Snapshot>> branchSnapshots,
            int depth,
            boolean isToolkit,
            Set<String> filteredEnvironments) {
        
        TransferPlan.TransferItem item = new TransferPlan.TransferItem();
        item.setProjectId(project.getId());
        item.setProjectName(project.getName());
        item.setProjectAcronym(project.getAcronym());
        item.setDisplayName(project.getDisplayName());
        item.setToolkit(isToolkit);
        item.setDepth(depth);

        // Add branches and snapshots
        for (Map.Entry<String, List<Snapshot>> entry : branchSnapshots.entrySet()) {
            String branchName = entry.getKey();
            List<Snapshot> snapshots = entry.getValue();
            
            TransferPlan.BranchSnapshots branchSnaps = new TransferPlan.BranchSnapshots();
            branchSnaps.setBranchName(branchName);
            branchSnaps.setDefault(branchName.equals(project.getDefaultBranchName()));
            
            for (Snapshot snapshot : snapshots) {
                // Fetch snapshot details to get target environment
                String targetEnvironment = null;
                try {
                    Snapshot snapshotDetails = client.getSnapshotDetails(
                        project.getId(),
                        branchName,
                        snapshot.getName()
                    );
                    targetEnvironment = snapshotDetails.getTargetEnvironment();
                } catch (IOException e) {
                    logger.warn("Failed to fetch target environment for snapshot {}: {}",
                               snapshot.getName(), e.getMessage());
                }
                
                // Filter out snapshots with target environments in the filter list
                if (filteredEnvironments != null && !filteredEnvironments.isEmpty() &&
                    targetEnvironment != null && filteredEnvironments.contains(targetEnvironment)) {
                    logger.info("Filtering out snapshot {} with target environment: {}",
                               snapshot.getName(), targetEnvironment);
                    continue;
                }
                
                TransferPlan.SnapshotInfo snapshotInfo = new TransferPlan.SnapshotInfo();
                snapshotInfo.setSnapshotId(snapshot.getId());
                snapshotInfo.setSnapshotName(snapshot.getName());
                snapshotInfo.setDisplayName(snapshot.getDisplayName());
                snapshotInfo.setCreationDate(snapshot.getCreationDate());
                snapshotInfo.setTargetEnvironment(targetEnvironment);
                branchSnaps.getSnapshots().add(snapshotInfo);
            }
            
            // Only add branch if it has snapshots after filtering
            if (!branchSnaps.getSnapshots().isEmpty()) {
                item.getBranches().add(branchSnaps);
            }
        }

        return item;
    }

    /**
     * Create a TransferItem for a process app
     */
    private static TransferPlan.TransferItem createTransferItemForProcessApp(
            BAWApiClient client,
            Project processApp,
            boolean ignoreBranches,
            Set<String> filteredEnvironments,
            int maxVersions) throws IOException {
        
        // Get branches to process
        List<Branch> branchesToProcess = getBranchesToProcess(client, processApp, ignoreBranches);
        
        // Build branch snapshots map
        Map<String, List<Snapshot>> branchSnapshots = new LinkedHashMap<>();
        
        for (Branch branch : branchesToProcess) {
            SnapshotsResponse snapshotsResponse = client.getSnapshots(
                processApp.getId(),
                branch.getName()
            );
            
            if (snapshotsResponse.getSnapshots() != null && !snapshotsResponse.getSnapshots().isEmpty()) {
                // Filter out snapshots with null id or name (invalid entries returned by the API)
                List<Snapshot> validSnapshots = snapshotsResponse.getSnapshots().stream()
                    .filter(s -> s.getId() != null && s.getName() != null)
                    .collect(java.util.stream.Collectors.toList());
                if (validSnapshots.size() < snapshotsResponse.getSnapshots().size()) {
                    logger.warn("Ignoring {} snapshot(s) with null id or name for branch: {}",
                               snapshotsResponse.getSnapshots().size() - validSnapshots.size(), branch.getName());
                }
                if (validSnapshots.isEmpty()) {
                    continue;
                }
                // Sort snapshots by creation date (oldest first)
                List<Snapshot> sortedSnapshots = new ArrayList<>(validSnapshots);
                sortedSnapshots.sort(Comparator.comparing(Snapshot::getCreationDate,
                                                         Comparator.nullsLast(String::compareTo)));
                
                // Limit the number of snapshots if maxVersions is set
                List<Snapshot> snapshotsToInclude = sortedSnapshots;
                if (maxVersions > 0 && sortedSnapshots.size() > maxVersions) {
                    // Take the LATEST snapshots (last N in the sorted list)
                    snapshotsToInclude = sortedSnapshots.subList(
                        sortedSnapshots.size() - maxVersions,
                        sortedSnapshots.size()
                    );
                    logger.info("Limiting Process App to {} latest snapshots (out of {}) for branch: {}",
                               snapshotsToInclude.size(), sortedSnapshots.size(), branch.getName());
                }
                
                branchSnapshots.put(branch.getName(), snapshotsToInclude);
            }
        }
        
        return createTransferItem(client, processApp, branchSnapshots, 0, false, filteredEnvironments);
    }

    /**
     * Get the list of branches to process for a project
     */
    private static List<Branch> getBranchesToProcess(BAWApiClient client, Project project, boolean ignoreBranches) throws IOException {
        if (ignoreBranches) {
            Branch defaultBranch = new Branch();
            defaultBranch.setName(project.getDefaultBranchName());
            defaultBranch.setDefault(true);
            return Collections.singletonList(defaultBranch);
        } else {
            BranchesResponse branchesResponse = client.getBranches(project.getId());
            
            if (branchesResponse.getBranches() == null || branchesResponse.getBranches().isEmpty()) {
                Branch defaultBranch = new Branch();
                defaultBranch.setName(project.getDefaultBranchName());
                defaultBranch.setDefault(true);
                return Collections.singletonList(defaultBranch);
            }
            
            // Sort branches to ensure default branch is processed first
            List<Branch> branches = new ArrayList<>(branchesResponse.getBranches());
            String defaultBranchName = project.getDefaultBranchName();
            branches.sort((b1, b2) -> {
                boolean b1IsDefault = defaultBranchName.equals(b1.getName());
                boolean b2IsDefault = defaultBranchName.equals(b2.getName());
                if (b1IsDefault && !b2IsDefault) return -1;
                if (!b1IsDefault && b2IsDefault) return 1;
                return 0;
            });
            
            return branches;
        }
    }

    /**
     * Create command line options
     */
    private static Options createOptions() {
        Options options = new Options();

        // Source system options
        options.addOption(Option.builder("su")
                .longOpt("source-url")
                .hasArg()
                .desc("Source system base URL (e.g., https://source-server:9443)")
                .required()
                .build());

        options.addOption(Option.builder("suser")
                .longOpt("source-user")
                .hasArg()
                .desc("Source system username")
                .required()
                .build());

        options.addOption(Option.builder("spass")
                .longOpt("source-password")
                .hasArg()
                .desc("Source system password")
                .required()
                .build());

        // Target system URL (for documentation in the plan)
        options.addOption(Option.builder("tu")
                .longOpt("target-url")
                .hasArg()
                .desc("Target system base URL (e.g., https://target-server:9443)")
                .required()
                .build());

        // Migration options
        options.addOption(Option.builder("p")
                .longOpt("project")
                .hasArg()
                .desc("Name of specific Process App to migrate")
                .build());

        options.addOption(Option.builder("ps")
                .longOpt("projects")
                .hasArg()
                .desc("Comma-separated list of Process App acronyms to migrate (e.g., PA1,PA2,PA3)")
                .build());

        options.addOption(Option.builder("a")
                .longOpt("all")
                .desc("Migrate all Process Apps")
                .build());

        options.addOption(Option.builder("e")
                .longOpt("export-dir")
                .hasArg()
                .desc("Directory for exported files (default: ./exports)")
                .build());

        options.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg()
                .desc("Output JSON file path (default: transfer-plan.json)")
                .build());

        options.addOption(Option.builder("ib")
                .longOpt("ignore-branches")
                .desc("Only export/import snapshots from the default branch (ignore other branches)")
                .build());

        options.addOption(Option.builder("fte")
                .longOpt("filter-target-environments")
                .hasArg()
                .desc("Comma-separated list of target environments to filter out (e.g., BAW_tWAS,BAW_Liberty)")
                .build());

        options.addOption(Option.builder("mv")
                .longOpt("max-versions")
                .hasArg()
                .desc("Maximum number of versions (snapshots) to analyze per project during dependency resolution (default: unlimited)")
                .build());

        options.addOption(Option.builder("ort")
                .longOpt("only-include-required-toolkits")
                .desc("Only include toolkit versions that are required by the resulting Process App list (filters out unused versions)")
                .build());

        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Print this help message")
                .build());

        return options;
    }

    /**
     * Validate required options
     */
    private static void validateRequiredOptions(CommandLine cmd) throws ParseException {
        String[] required = {"source-url", "source-user", "source-password", "target-url"};
        
        for (String opt : required) {
            if (!cmd.hasOption(opt)) {
                throw new ParseException("Missing required option: --" + opt);
            }
        }
    }

    /**
     * Print help message
     */
    private static void printHelp(HelpFormatter formatter, Options options) {
        String header = "\nIBM BAW Transfer Plan Generator (Stage 1)\n" +
                       "Analyzes Process Apps and generates a JSON transfer plan with ordered list of projects and snapshots.\n\n";
        
        String footer = "\nExamples:\n" +
                       "  Generate plan for a specific Process App:\n" +
                       "    java -cp process-app-migrator.jar com.ibm.baw.migrator.WriteTransferPlan \\\n" +
                       "      --source-url https://source:9443 --source-user admin --source-password pass1 \\\n" +
                       "      --target-url https://target:9443 \\\n" +
                       "      --project \"My Process App\" --output my-plan.json\n\n" +
                       "  Generate plan for multiple Process Apps:\n" +
                       "    java -cp process-app-migrator.jar com.ibm.baw.migrator.WriteTransferPlan \\\n" +
                       "      --source-url https://source:9443 --source-user admin --source-password pass1 \\\n" +
                       "      --target-url https://target:9443 \\\n" +
                       "      --projects PA1,PA2,PA3\n\n" +
                       "  Generate plan for all Process Apps:\n" +
                       "    java -cp process-app-migrator.jar com.ibm.baw.migrator.WriteTransferPlan \\\n" +
                       "      --source-url https://source:9443 --source-user admin --source-password pass1 \\\n" +
                       "      --target-url https://target:9443 \\\n" +
                       "      --all\n\n" +
                       "  Generate plan with target environment filtering:\n" +
                       "    java -cp process-app-migrator.jar com.ibm.baw.migrator.WriteTransferPlan \\\n" +
                       "      --source-url https://source:9443 --source-user admin --source-password pass1 \\\n" +
                       "      --target-url https://target:9443 \\\n" +
                       "      --project \"My Process App\" \\\n" +
                       "      --filter-target-environments BAW_tWAS\n";

        formatter.printHelp("WriteTransferPlan", header, options, footer, true);
    }

    /**
     * Find a project by name
     */
    private static Project findProjectByName(BAWApiClient client, String projectName) throws IOException {
        ProjectsResponse response = client.getProjects();
        
        if (response.getProjects() != null) {
            for (Project project : response.getProjects()) {
                if (projectName.equals(project.getName()) ||
                    projectName.equals(project.getDisplayName())) {
                    return project;
                }
            }
        }
        
        return null;
    }

    /**
     * Find a project by acronym
     */
    private static Project findProjectByAcronym(BAWApiClient client, String acronym) throws IOException {
        ProjectsResponse response = client.getProjects();
        
        if (response.getProjects() != null) {
            for (Project project : response.getProjects()) {
                if (acronym.equals(project.getAcronym())) {
                    return project;
                }
            }
        }
        
        return null;
    }

    /**
     * Collect required toolkit snapshots from a Process App's snapshots
     * This uses the what_used API to get the complete dependency tree including
     * specific toolkit versions (container + snapshot)
     */
    private static void collectRequiredToolkitSnapshots(
            BAWApiClient client,
            Project processApp,
            TransferPlan.TransferItem processAppItem,
            Map<String, Set<String>> requiredToolkitSnapshots) {
        
        logger.info("Collecting required toolkit snapshots for Process App: {}",
                   processApp.getDisplayName());
        
        // Iterate through all branches and snapshots of the Process App
        for (TransferPlan.BranchSnapshots branchSnaps : processAppItem.getBranches()) {
            for (TransferPlan.SnapshotInfo snapshot : branchSnaps.getSnapshots()) {
                try {
                    // Use what_used API to get the complete dependency tree with specific versions
                    WhatUsedResponse whatUsed = client.getWhatUsed(
                        processApp.getAcronym(),
                        snapshot.getSnapshotName()
                    );
                    
                    if (whatUsed.getToolkitsUsed() != null) {
                        // Recursively collect all toolkit versions from the dependency tree
                        collectToolkitVersionsRecursive(whatUsed.getToolkitsUsed(), requiredToolkitSnapshots);
                    }
                } catch (IOException e) {
                    logger.warn("Failed to get dependencies for snapshot {}: {}",
                               snapshot.getSnapshotName(), e.getMessage());
                }
            }
        }
        
        // Log summary
        int totalVersions = requiredToolkitSnapshots.values().stream()
            .mapToInt(Set::size)
            .sum();
        logger.info("Collected {} required toolkit versions across {} toolkits",
                   totalVersions, requiredToolkitSnapshots.size());
    }
    
    /**
     * Recursively collect toolkit versions from the dependency tree
     * This processes the nested structure returned by what_used API
     */
    private static void collectToolkitVersionsRecursive(
            List<ToolkitVersionUsed> toolkitsUsed,
            Map<String, Set<String>> requiredToolkitSnapshots) {
        
        if (toolkitsUsed == null || toolkitsUsed.isEmpty()) {
            return;
        }
        
        for (ToolkitVersionUsed toolkit : toolkitsUsed) {
            String containerAcronym = toolkit.getContainer();
            String snapshotName = toolkit.getSnapshotName();
            
            if (containerAcronym != null && snapshotName != null) {
                // Add this specific toolkit version to the required set
                requiredToolkitSnapshots.putIfAbsent(containerAcronym, new HashSet<>());
                requiredToolkitSnapshots.get(containerAcronym).add(snapshotName);
                
                logger.debug("Required toolkit version: {} snapshot: {}",
                           containerAcronym, snapshotName);
            }
            
            // Recursively process nested dependencies
            if (toolkit.getToolkitsUsed() != null && !toolkit.getToolkitsUsed().isEmpty()) {
                collectToolkitVersionsRecursive(toolkit.getToolkitsUsed(), requiredToolkitSnapshots);
            }
        }
    }

    /**
     * Filter toolkits to only include versions (snapshots) that are required by the Process Apps
     * This method filters toolkit snapshots to only include those specific versions that are
     * actually needed, keeping them in oldest to newest order
     */
    private static List<TransferPlan.TransferItem> filterRequiredToolkits(
            Collection<TransferPlan.TransferItem> allToolkits,
            Map<String, Set<String>> requiredToolkitSnapshots) {
        
        List<TransferPlan.TransferItem> filteredToolkits = new ArrayList<>();
        
        // For each toolkit, filter its snapshots to only include required versions
        for (TransferPlan.TransferItem toolkit : allToolkits) {
            String toolkitAcronym = toolkit.getProjectAcronym();
            
            // Check if this toolkit has any required snapshots
            Set<String> requiredSnapshots = requiredToolkitSnapshots.get(toolkitAcronym);
            if (requiredSnapshots == null || requiredSnapshots.isEmpty()) {
                logger.debug("Excluding toolkit: {} (not required by any Process App)",
                           toolkit.getDisplayName());
                continue;
            }
            
            // Create a filtered version of this toolkit with only required snapshots
            TransferPlan.TransferItem filteredToolkit = new TransferPlan.TransferItem();
            filteredToolkit.setProjectId(toolkit.getProjectId());
            filteredToolkit.setProjectName(toolkit.getProjectName());
            filteredToolkit.setProjectAcronym(toolkit.getProjectAcronym());
            filteredToolkit.setDisplayName(toolkit.getDisplayName());
            filteredToolkit.setToolkit(toolkit.isToolkit());
            filteredToolkit.setDepth(toolkit.getDepth());
            
            int originalSnapshotCount = 0;
            int filteredSnapshotCount = 0;
            
            // Process each branch
            for (TransferPlan.BranchSnapshots branchSnaps : toolkit.getBranches()) {
                TransferPlan.BranchSnapshots filteredBranch = new TransferPlan.BranchSnapshots();
                filteredBranch.setBranchName(branchSnaps.getBranchName());
                filteredBranch.setDefault(branchSnaps.isDefault());
                
                // Filter snapshots to only include required ones (already sorted oldest to newest)
                for (TransferPlan.SnapshotInfo snapshot : branchSnaps.getSnapshots()) {
                    originalSnapshotCount++;
                    
                    // Check if this specific snapshot is required
                    if (requiredSnapshots.contains(snapshot.getDisplayName())) {
                        filteredBranch.getSnapshots().add(snapshot);
                        filteredSnapshotCount++;
                        logger.debug("Including required snapshot: {} for toolkit: {}",
                                   snapshot.getSnapshotName(), toolkitAcronym);
                    }
                }
                
                // Only add branch if it has required snapshots
                if (!filteredBranch.getSnapshots().isEmpty()) {
                    filteredToolkit.getBranches().add(filteredBranch);
                }
            }
            
            // Only add toolkit if it has required snapshots after filtering
            if (!filteredToolkit.getBranches().isEmpty()) {
                filteredToolkits.add(filteredToolkit);
                logger.info("Including toolkit: {} with {} required snapshots (filtered from {} total)",
                           toolkit.getDisplayName(), filteredSnapshotCount, originalSnapshotCount);
            }
        }
        
        int totalFilteredSnapshots = filteredToolkits.stream()
            .flatMap(t -> t.getBranches().stream())
            .mapToInt(b -> b.getSnapshots().size())
            .sum();
            
        logger.info("Filtered toolkits: kept {} toolkits with {} total snapshots (from {} original toolkits)",
                   filteredToolkits.size(), totalFilteredSnapshots, allToolkits.size());
        
        return filteredToolkits;
    }
}

// Made with Bob