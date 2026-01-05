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

import com.ibm.baw.migrator.client.BAWApiClient;
import com.ibm.baw.migrator.model.Project;
import com.ibm.baw.migrator.model.ProjectsResponse;
import com.ibm.baw.migrator.service.MigrationService;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Main application for migrating IBM BAW Process Apps between systems
 */
public class ProcessAppMigrator {
    private static final Logger logger = LoggerFactory.getLogger(ProcessAppMigrator.class);

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
            String targetUser = cmd.getOptionValue("target-user");
            String targetPassword = cmd.getOptionValue("target-password");
            
            String exportDir = cmd.getOptionValue("export-dir", "./exports");
            String projectName = cmd.getOptionValue("project");
            String projectsAcronyms = cmd.getOptionValue("projects");
            boolean migrateAll = cmd.hasOption("all");
            boolean ignoreBranches = cmd.hasOption("ignore-branches");

            // Create export directory
            File exportDirectory = new File(exportDir);
            if (!exportDirectory.exists()) {
                exportDirectory.mkdirs();
                logger.info("Created export directory: {}", exportDirectory.getAbsolutePath());
            }

            // Initialize API clients (CSRF tokens are obtained automatically)
            logger.info("Connecting to source system: {}", sourceUrl);
            BAWApiClient sourceClient = new BAWApiClient(sourceUrl, sourceUser, sourcePassword);
            
            logger.info("Connecting to target system: {}", targetUrl);
            BAWApiClient targetClient = new BAWApiClient(targetUrl, targetUser, targetPassword);

            // Create migration service
            MigrationService migrationService = new MigrationService(
                sourceClient,
                targetClient,
                exportDirectory,
                ignoreBranches
            );

            // Perform migration
            if (migrateAll) {
                logger.info("Starting migration of all Process Apps");
                migrationService.migrateAllProcessApps();
            } else if (projectsAcronyms != null) {
                // Migrate multiple projects by acronym
                String[] acronyms = projectsAcronyms.split(",");
                logger.info("Starting migration of {} Process Apps by acronym", acronyms.length);
                
                for (String acronym : acronyms) {
                    acronym = acronym.trim();
                    if (acronym.isEmpty()) {
                        continue;
                    }
                    
                    logger.info("Migrating Process App with acronym: {}", acronym);
                    Project project = findProjectByAcronym(sourceClient, acronym);
                    if (project == null) {
                        logger.error("Process App not found with acronym: {}", acronym);
                        continue; // Continue with next project instead of exiting
                    }
                    migrationService.migrateProcessApp(project);
                }
            } else if (projectName != null) {
                logger.info("Starting migration of Process App: {}", projectName);
                Project project = findProjectByName(sourceClient, projectName);
                if (project == null) {
                    logger.error("Process App not found: {}", projectName);
                    System.exit(1);
                }
                migrationService.migrateProcessApp(project);
            } else {
                logger.error("Either --project, --projects, or --all must be specified");
                printHelp(formatter, options);
                System.exit(1);
            }

            // Close clients
            sourceClient.close();
            targetClient.close();

            logger.info("Migration completed successfully!");

        } catch (ParseException e) {
            logger.error("Error parsing command line arguments: {}", e.getMessage());
            printHelp(formatter, options);
            System.exit(1);
        } catch (IOException e) {
            logger.error("Migration failed with error: {}", e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            logger.error("Unexpected error during migration: {}", e.getMessage(), e);
            System.exit(1);
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

        // Target system options
        options.addOption(Option.builder("tu")
                .longOpt("target-url")
                .hasArg()
                .desc("Target system base URL (e.g., https://target-server:9443)")
                .required()
                .build());

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

        options.addOption(Option.builder("ib")
                .longOpt("ignore-branches")
                .desc("Only export/import snapshots from the default branch (ignore other branches)")
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
        String[] required = {"source-url", "source-user", "source-password", 
                           "target-url", "target-user", "target-password"};
        
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
        String header = "\nIBM BAW Process App Migrator\n" +
                       "Exports Process Apps with their toolkit dependencies from one system and imports to another.\n" +
                       "By default, all snapshots from ALL branches are exported and imported.\n\n";
        
        String footer = "\nExamples:\n" +
                       "  Migrate a specific Process App by name (all branches):\n" +
                       "    java -jar process-app-migrator.jar \\\n" +
                       "      --source-url https://source:9443 --source-user admin --source-password pass1 \\\n" +
                       "      --target-url https://target:9443 --target-user admin --target-password pass2 \\\n" +
                       "      --project \"My Process App\"\n\n" +
                       "  Migrate a Process App with only the default branch:\n" +
                       "    java -jar process-app-migrator.jar \\\n" +
                       "      --source-url https://source:9443 --source-user admin --source-password pass1 \\\n" +
                       "      --target-url https://target:9443 --target-user admin --target-password pass2 \\\n" +
                       "      --project \"My Process App\" --ignore-branches\n\n" +
                       "  Migrate multiple Process Apps by acronym:\n" +
                       "    java -jar process-app-migrator.jar \\\n" +
                       "      --source-url https://source:9443 --source-user admin --source-password pass1 \\\n" +
                       "      --target-url https://target:9443 --target-user admin --target-password pass2 \\\n" +
                       "      --projects PA1,PA2,PA3\n\n" +
                       "  Migrate all Process Apps:\n" +
                       "    java -jar process-app-migrator.jar \\\n" +
                       "      --source-url https://source:9443 --source-user admin --source-password pass1 \\\n" +
                       "      --target-url https://target:9443 --target-user admin --target-password pass2 \\\n" +
                       "      --all\n\n" +
                       "Note: CSRF tokens are automatically obtained from the /system/login API endpoint.\n" +
                       "      By default, all branches are processed. Use --ignore-branches to only process the default branch.\n";

        formatter.printHelp("process-app-migrator", header, options, footer, true);
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
}

// Made with Bob
