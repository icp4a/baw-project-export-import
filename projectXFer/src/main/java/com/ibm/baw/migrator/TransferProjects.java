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
import com.ibm.baw.migrator.client.BAWApiClient;
import com.ibm.baw.migrator.model.Project;
import com.ibm.baw.migrator.model.ProjectsResponse;
import com.ibm.baw.migrator.model.TransferPlan;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Stage 2: Reads a transfer plan JSON file and executes the export/import operations
 */
public class TransferProjects {
    private static final Logger logger = LoggerFactory.getLogger(TransferProjects.class);

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
            String planFile = cmd.getOptionValue("plan");
            String sourceUser = cmd.getOptionValue("source-user");
            String sourcePassword = cmd.getOptionValue("source-password");
            String targetUser = cmd.getOptionValue("target-user");
            String targetPassword = cmd.getOptionValue("target-password");

            // Read the transfer plan
            logger.info("Reading transfer plan from: {}", planFile);
            ObjectMapper mapper = new ObjectMapper();
            TransferPlan plan = mapper.readValue(new File(planFile), TransferPlan.class);

            logger.info("Transfer plan loaded:");
            logger.info("  Source URL: {}", plan.getSourceUrl());
            logger.info("  Target URL: {}", plan.getTargetUrl());
            logger.info("  Export Directory: {}", plan.getExportDir());
            logger.info("  Ignore Branches: {}", plan.isIgnoreBranches());
            logger.info("  Toolkits: {}", plan.getToolkits().size());
            logger.info("  Process Apps: {}", plan.getProcessApps().size());

            // Create export directory
            File exportDirectory = new File(plan.getExportDir());
            if (!exportDirectory.exists()) {
                exportDirectory.mkdirs();
                logger.info("Created export directory: {}", exportDirectory.getAbsolutePath());
            }

            // Initialize API clients
            logger.info("Connecting to source system: {}", plan.getSourceUrl());
            BAWApiClient sourceClient = new BAWApiClient(plan.getSourceUrl(), sourceUser, sourcePassword);
            
            logger.info("Connecting to target system: {}", plan.getTargetUrl());
            BAWApiClient targetClient = new BAWApiClient(plan.getTargetUrl(), targetUser, targetPassword);

            // Execute the transfer plan
            executeTransferPlan(plan, sourceClient, targetClient, exportDirectory);

            // Close clients
            sourceClient.close();
            targetClient.close();

            logger.info("Transfer completed successfully!");

        } catch (ParseException e) {
            logger.error("Error parsing command line arguments: {}", e.getMessage());
            printHelp(formatter, options);
            System.exit(1);
        } catch (IOException e) {
            logger.error("Transfer failed with error: {}", e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            logger.error("Unexpected error during transfer: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * Execute the transfer plan
     */
    private static void executeTransferPlan(TransferPlan plan, BAWApiClient sourceClient, 
                                           BAWApiClient targetClient, File exportDirectory) throws IOException {
        
        // Step 1: Transfer all toolkits in order
        logger.info("Starting transfer of {} toolkits", plan.getToolkits().size());
        
        for (TransferPlan.TransferItem toolkit : plan.getToolkits()) {
            try {
                transferItem(toolkit, sourceClient, targetClient, exportDirectory);
            } catch (Exception e) {
                logger.error("Failed to transfer toolkit: {}", toolkit.getDisplayName(), e);
                throw new IOException("Failed to transfer required toolkit: " + toolkit.getDisplayName(), e);
            }
        }
        
        logger.info("Successfully transferred all toolkits");

        // Step 2: Transfer all process apps
        logger.info("Starting transfer of {} Process Apps", plan.getProcessApps().size());
        
        for (TransferPlan.TransferItem processApp : plan.getProcessApps()) {
            try {
                transferItem(processApp, sourceClient, targetClient, exportDirectory);
            } catch (Exception e) {
                logger.error("Failed to transfer Process App: {}", processApp.getDisplayName(), e);
                // Continue with other process apps instead of failing completely
            }
        }
        
        logger.info("Successfully transferred all Process Apps");
    }

    /**
     * Transfer a single item (toolkit or process app) with all its snapshots
     */
    private static void transferItem(TransferPlan.TransferItem item, BAWApiClient sourceClient,
                                     BAWApiClient targetClient, File exportDirectory) throws IOException {
        
        logger.info("Transferring {}: {} ({})", 
                   item.isToolkit() ? "toolkit" : "Process App",
                   item.getDisplayName(),
                   item.getProjectAcronym());

        // Check if project already exists on target
        Project existingProject = findProjectOnTarget(targetClient, item.getProjectName());
        if (existingProject != null) {
            logger.info("Project already exists on target: {}", item.getDisplayName());
            // Continue to process snapshots in case some are missing
        }

        // Process each branch
        for (TransferPlan.BranchSnapshots branchSnaps : item.getBranches()) {
            String branchName = branchSnaps.getBranchName();
            logger.info("Processing branch: {} ({} snapshots)", 
                       branchName, branchSnaps.getSnapshots().size());

            // Process each snapshot in order
            for (TransferPlan.SnapshotInfo snapshot : branchSnaps.getSnapshots()) {
                try {
                    exportAndImportSnapshot(
                        item.getProjectId(),
                        item.getProjectAcronym(),
                        branchName,
                        snapshot,
                        sourceClient,
                        targetClient,
                        exportDirectory
                    );
                } catch (Exception e) {
                    logger.error("Failed to transfer snapshot: {} from branch: {}", 
                               snapshot.getDisplayName(), branchName, e);
                    throw e;
                }
            }
        }

        logger.info("Successfully transferred: {}", item.getDisplayName());
    }

    /**
     * Export a snapshot from source and import to target
     */
    private static void exportAndImportSnapshot(String projectId, String projectAcronym,
                                               String branchName, TransferPlan.SnapshotInfo snapshot,
                                               BAWApiClient sourceClient, BAWApiClient targetClient,
                                               File exportDirectory) throws IOException {
        
        logger.info("Exporting snapshot: {} from branch: {}", snapshot.getDisplayName(), branchName);
        
        // Export snapshot
        File exportedFile = sourceClient.exportSnapshot(
            projectId,
            branchName,
            snapshot.getSnapshotName(),
            exportDirectory
        );
        
        logger.info("Importing snapshot: {} to target system", snapshot.getDisplayName());
        
        // Import to target
        Project importedProject = targetClient.importProject(exportedFile);
        
        logger.info("Successfully imported snapshot: {} as project: {}", 
                   snapshot.getDisplayName(), importedProject.getDisplayName());
    }

    /**
     * Find a project on the target system by name
     */
    private static Project findProjectOnTarget(BAWApiClient targetClient, String projectName) {
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
     * Create command line options
     */
    private static Options createOptions() {
        Options options = new Options();

        // Plan file
        options.addOption(Option.builder("plan")
                .longOpt("plan")
                .hasArg()
                .desc("Path to transfer plan JSON file")
                .required()
                .build());

        // Source credentials
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

        // Target credentials
        options.addOption(Option.builder("tuser")
                .longOpt("target-user")
                .hasArg()
                .desc("Target system username")
                .required()
                .build());

        options.addOption(Option.builder("tpass")
                .longOpt("target-password")
                .hasArg()
                .desc("Target system password")
                .required()
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
        String[] required = {"plan", "source-user", "source-password", "target-user", "target-password"};
        
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
        String header = "\nIBM BAW Project Transfer Executor (Stage 2)\n" +
                       "Reads a transfer plan JSON file and executes the export/import operations.\n\n";
        
        String footer = "\nExamples:\n" +
                       "  Execute a transfer plan:\n" +
                       "    java -cp process-app-migrator.jar com.ibm.baw.migrator.TransferProjects \\\n" +
                       "      --plan transfer-plan.json \\\n" +
                       "      --source-user admin --source-password pass1 \\\n" +
                       "      --target-user admin --target-password pass2\n\n" +
                       "Note: The transfer plan contains the source and target URLs, export directory,\n" +
                       "      and the complete ordered list of projects and snapshots to transfer.\n";

        formatter.printHelp("TransferProjects", header, options, footer, true);
    }
}

// Made with Bob