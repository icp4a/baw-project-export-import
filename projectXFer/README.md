# IBM BAW Process App Migrator

A Java application that exports IBM Business Automation Workflow (BAW) Workflow Automations (Process Apps and Case) from one system and imports them into another, automatically handling toolkit dependencies. The systems may be BAW or CP4BA environments. The target environment must be at the version of the source or newer. Works for CP4BA Business Applications as well.

## Features

- **Two-Stage Approach**: Separate planning and execution phases for better control and review
- **Transfer Plan Generation**: Creates a JSON file with ordered list of all projects and snapshots to migrate
- **Automatic Dependency Resolution**: Identifies and resolves all toolkit dependencies for Project(s)
- **Branch Support**: Exports and imports snapshots from all branches (or just the default branch with `--ignore-branches`)
- **Ordered Migration**: Exports and imports toolkits in the correct order (leaf-first, oldest version first)
- **System Toolkit Filtering**: Automatically skips system toolkits that shouldn't be migrated
- **Version Management**: Handles all versions/snapshots of toolkits and Projects across all branches
- **Version Limiting**: Optional `--max-versions` flag to limit the number of versions analyzed per project for improved performance
- **API Caching**: Built-in caching of API GET calls with configurable TTL to reduce server load and improve performance
- **Batch Migration**: Can migrate all Projects or specific ones
- **Comprehensive Logging**: Detailed logging for troubleshooting and audit trails

## Prerequisites

- Java 11 (JDK 11) or higher
- Maven 3.6 or higher
- Access to source and target IBM BAW systems with appropriate credentials
- Network connectivity between the machine running the tool and both BAW systems

## Building the Application

```bash
# Clone or download the project
cd baw-project-export-import\projectXFer

# Build with Maven
mvn clean package

# The executable JAR will be created at:
# target/baw-project-export-import-1.0.0-jar-with-dependencies.jar
```

## Usage

The migration process is now split into two stages for better control and review:

### Stage 1: Generate Transfer Plan (WriteTransferPlan)

Analyzes the source system and generates a JSON file containing an ordered list of all projects and snapshots to migrate.

#### Command Line Options

| Option | Description | Required |
|--------|-------------|----------|
| `--source-url` | Source system base URL (e.g., https://source-server:9443) | Yes |
| `--source-user` | Source system username | Yes |
| `--source-password` | Source system password | Yes |
| `--target-url` | Target system base URL (for documentation in plan) | Yes |
| `--project` | Name of specific project to migrate | No* |
| `--projects` | Comma-separated list of project acronyms to migrate | No* |
| `--all` | Migrate all projects | No* |
| `--export-dir` | Directory for exported files (default: ./exports) | No |
| `--output` | Output JSON file path (default: transfer-plan.json) | No |
| `--ignore-branches` | Only include snapshots from the default branch | No |
| `--filter-target-environments` | Comma-separated list of target environments to exclude from the plan (values: BAW_tWAS,BAW,BAW_CP4A,BAW_Liberty) | No |
| `--max-versions` | Maximum number of versions (snapshots) to analyze per project (default: unlimited) | No |
| `--only-include-required-toolkits` | Only include toolkit versions that are required by the Process Apps (filters out unused versions) | No |
| `--help` | Print help message | No |

*Either `--project`, `--projects`, or `--all` must be specified.

#### Example Commands

```bash
# Generate plan for a specific Process App
java -cp target/baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.WriteTransferPlan \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --project "My Process App" \
  --output my-plan.json

# Generate plan for multiple Process Apps by acronym
java -cp target/baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.WriteTransferPlan \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --projects PA1,PA2,PA3

# Generate plan for all Process Apps
java -cp target/baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.WriteTransferPlan \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --all

# Generate plan with target environment filtering
# This will exclude snapshots with target environment set to DEV or TEST
java -cp target/baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.WriteTransferPlan \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --project "My Process App" \
  --filter-target-environments BAW_tWAS

# Generate plan with version limiting (analyze only 5 most recent versions per project)
# This significantly improves performance for projects with many versions
java -cp target/baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.WriteTransferPlan \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --project "My Process App" \
  --max-versions 5

# Generate plan with only required toolkit versions
# This includes only the specific toolkit snapshots that are actually used by the Process Apps
java -cp target/baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.WriteTransferPlan \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --project "My Process App" \
  --only-include-required-toolkits

# Combine version limiting with required toolkit filtering
# Limits Process Apps to 5 versions, includes only required toolkit versions (no limit on toolkit versions)
java -cp target/baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.WriteTransferPlan \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --project "My Process App" \
  --max-versions 5 \
  --only-include-required-toolkits
```

### Stage 2: Execute Transfer (TransferProjects)

Reads the transfer plan JSON file and executes the export/import operations.

#### Command Line Options

| Option | Description | Required |
|--------|-------------|----------|
| `--plan` | Path to transfer plan JSON file | Yes |
| `--source-user` | Source system username | Yes |
| `--source-password` | Source system password | Yes |
| `--target-user` | Target system username | Yes |
| `--target-password` | Target system password | Yes |
| `--help` | Print help message | No |

#### Example Commands

```bash
# Execute a transfer plan
java -cp target/baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.TransferProjects \
  --plan transfer-plan.json \
  --source-user admin \
  --source-password pass1 \
  --target-user admin \
  --target-password pass2
```

### Legacy Single-Stage Approach (ProcessAppMigrator)

The original single-stage approach is still available for backward compatibility:

#### Command Line Options

| Option | Description | Required |
|--------|-------------|----------|
| `--source-url` | Source system base URL (e.g., https://source-server:9443) | Yes |
| `--source-user` | Source system username | Yes |
| `--source-password` | Source system password | Yes |
| `--target-url` | Target system base URL (e.g., https://target-server:9443) | Yes |
| `--target-user` | Target system username | Yes |
| `--target-password` | Target system password | Yes |
| `--project` | Name of specific project to export and import | No* |
| `--projects` | Comma-separated list of project acronyms to migrate | No* |
| `--all` | Migrate all projects | No* |
| `--export-dir` | Directory for exported files (default: ./exports) | No |
| `--ignore-branches` | Only export/import snapshots from the default branch | No |
| `--max-versions` | Maximum number of versions (snapshots) to analyze per project (default: unlimited) | No |
| `--help` | Print help message | No |

*Either `--project`, `--projects`, or `--all` must be specified.

#### Example Commands

```bash
# Migrate a specific Process App (all branches)
java -jar target/baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --target-user admin \
  --target-password pass2 \
  --project "My Process App"

# Migrate with only default branch
java -jar target/baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --target-user admin \
  --target-password pass2 \
  --project "My Process App" \
  --ignore-branches

# Migrate with version limiting (analyze only 5 most recent versions per project)
java -jar target/baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --target-user admin \
  --target-password pass2 \
  --project "My Process App" \
  --max-versions 5
```

**Note**:
- CSRF tokens are automatically obtained from the `/system/login` API endpoint when the application connects to each system.
- By default, all branches are processed. Use `--ignore-branches` to only process the default branch.


## How It Works

### Two-Stage Migration Process

#### Stage 1: Transfer Plan Generation (WriteTransferPlan)

1. **Authentication**:
   - Connects to the source system
   - Automatically obtains CSRF token via the `/system/login` API endpoint
   - Uses Basic Authentication with provided credentials

2. **Project Discovery**: Retrieves the specified Project(s) from the source system

3. **Branch Discovery**:
   - Retrieves all branches for each Project (or just the default branch if `--ignore-branches` is specified)
   - Processes each branch independently

4. **Dependency Analysis**: For each Project and branch:
   - Retrieves all snapshots/versions from the branch
   - Uses the `/what_used` API endpoint to get the complete dependency tree in a single call
   - Recursively processes dependencies (toolkits may depend on other toolkits)
   - For each toolkit dependency, retrieves snapshots from all branches (or just default branch)
   - Filters out system toolkits
   - If `--only-include-required-toolkits` is specified, tracks the specific toolkit versions used by each Process App snapshot

5. **Dependency Ordering**:
   - Calculates dependency depth for each toolkit
   - Orders toolkits so leaf nodes (no dependencies) are processed first
   - Within each toolkit and branch, orders snapshots by creation date (oldest first)

6. **Snapshot Filtering** (Optional):
   - If `--filter-target-environments` is specified, fetches target environment for each snapshot
   - Excludes snapshots whose target environment matches any in the filter list
   - Target environment information is included in the generated JSON for all remaining snapshots

7. **Toolkit Filtering** (Optional):
   - If `--only-include-required-toolkits` is specified, filters the toolkit list
   - Only includes toolkit snapshots that are actually referenced by the Process Apps
   - Maintains oldest-to-newest ordering within each toolkit

8. **Plan Generation**:
   - Creates a JSON file containing:
     - Source and target URLs
     - Export directory configuration
     - Ordered list of toolkits with their snapshots (filtered if `--only-include-required-toolkits` was used)
     - Ordered list of all Process Apps with their snapshots (including target environment)
   - The plan can be reviewed and modified before execution

#### Stage 2: Transfer Execution (TransferProjects)

1. **Plan Loading**:
   - Reads the transfer plan JSON file
   - Validates the plan structure

2. **Authentication**:
   - Connects to both source and target systems using the URLs from the plan
   - Automatically obtains CSRF tokens via the `/system/login` API endpoint
   - Uses Basic Authentication with provided credentials

3. **Export Phase**:
   - Exports each toolkit snapshot from all branches from the source system as .twx files
   - Follows the exact order specified in the transfer plan
   - Stores exported files in the directory specified in the plan

4. **Import Phase**:
   - Imports toolkits in the order specified in the plan to the target system
   - Checks if toolkits already exist on target to avoid duplicates
   - Imports all versions of each toolkit from all branches in chronological order
   - Finally imports the Process App snapshots from all branches

### Legacy Single-Stage Process (ProcessAppMigrator)

The original approach combines all steps above into a single execution, performing analysis, export, and import in one run without generating an intermediate plan file.

### Performance Optimization

#### API Caching

The application includes built-in caching for all API GET calls to improve performance:

- **Automatic Caching**: All GET requests are cached with a default TTL of 5 minutes
- **Thread-Safe**: Uses ConcurrentHashMap for thread-safe caching
- **Automatic Expiration**: Cached entries automatically expire after the TTL period
- **Reduced Server Load**: Significantly reduces the number of API calls to the BAW server

Cached API calls include:
- Project lists and details
- Branch information
- Snapshot lists and details
- Dependency information

#### Version Limiting

For projects with many versions, use the `--max-versions` flag to limit the number of versions analyzed:

```bash
# Analyze only the 5 most recent versions per project
--max-versions 5
```

**Benefits:**
- **Faster Analysis**: Reduces the number of API calls and processing time
- **Reduced Memory Usage**: Processes fewer snapshots
- **Focused Migration**: Migrates only the most recent versions

**How it works:**
- Limits the number of snapshots analyzed per project/branch
- Selects the N most recent snapshots (by creation date)
- Applies to both Process Apps and toolkits (unless `--only-include-required-toolkits` is also specified)
- Does not affect the total number of projects analyzed

**When to use:**
- Projects with many historical versions that don't need to be migrated
- Performance issues with large-scale migrations
- Testing migrations with a subset of versions

#### Required Toolkit Filtering

Use the `--only-include-required-toolkits` flag to include only the specific toolkit versions that are actually used by the Process Apps:

```bash
# Include only required toolkit versions
--only-include-required-toolkits
```

**Benefits:**
- **Reduced Migration Size**: Only migrates toolkit versions that are actually needed
- **Faster Migration**: Fewer toolkit snapshots to export and import
- **Cleaner Target Environment**: Avoids migrating unused toolkit versions

**How it works:**
- Uses the `what_used` API to analyze each Process App snapshot's dependencies
- Recursively collects all required toolkit versions (including nested dependencies)
- Filters the toolkit list to only include those specific versions
- Maintains oldest-to-newest ordering within each toolkit

**When to use:**
- When toolkits have many versions but only some are used by the Process Apps
- To minimize the migration footprint
- When you want to avoid migrating unused toolkit versions

#### Flag Interactions with --only-include-required-toolkits

When `--only-include-required-toolkits` is used, it changes how other flags behave:

**With --max-versions:**

| Flag Combination | Process Apps | Toolkits |
|------------------|--------------|----------|
| `--max-versions` only | Limited to N versions | Limited to N versions |
| `--only-include-required-toolkits` only | All versions | Only required versions |
| Both flags together | Limited to N versions | Only required versions (no limit) |

**With --filter-target-environments:**

| Flag Combination | Process Apps | Toolkits |
|------------------|--------------|----------|
| `--filter-target-environments` only | Filtered by target env | Filtered by target env |
| `--only-include-required-toolkits` only | All snapshots | Only required versions |
| Both flags together | Filtered by target env | Only required versions (not filtered by target env) |

**Rationale:**
- When `--only-include-required-toolkits` is specified, required toolkits are included regardless of their target environment
- This ensures all necessary toolkit dependencies are migrated, even if they have target environments in the filter list
- The `--filter-target-environments` flag still applies to Process App snapshots

**Examples:**
```bash
# Limit Process Apps to 5 versions, include all required toolkit versions
--max-versions 5 --only-include-required-toolkits

# Filter Process App snapshots by target environment, include all required toolkit versions
--filter-target-environments BAW_tWAS --only-include-required-toolkits

# Combine all three flags
--max-versions 5 --filter-target-environments BAW_tWAS --only-include-required-toolkits
```

These combinations are useful when:
- You want to migrate only recent Process App versions
- But need all toolkit versions that those Process Apps depend on
- The `--max-versions` limit ensures fast analysis of Process Apps
- The `--only-include-required-toolkits` ensures you get all necessary toolkit versions
- Target environment filtering applies only to Process Apps, not required toolkits

### Dependency Resolution Algorithm

The application uses a depth-first search algorithm to resolve dependencies:

```
For each Process App:
  For each branch (or just default branch if --ignore-branches):
    For each snapshot in branch (limited by --max-versions if specified):
      Extract toolkit dependencies using what_used API
      
      If --only-include-required-toolkits is specified:
        Track specific toolkit versions (container + snapshot) used by this Process App snapshot
        Recursively track nested toolkit dependencies
      
      For each toolkit dependency:
        If not system toolkit:
          Add to dependency tree
          For each branch of toolkit (or just default):
            Collect snapshots from branch (limited by --max-versions if NOT using --only-include-required-toolkits)
          Recursively resolve toolkit's dependencies
          Calculate depth (leaf nodes have highest depth)

Sort all dependencies by depth (descending)

If --only-include-required-toolkits is specified:
  Filter toolkit list to only include required versions
  Maintain oldest-to-newest ordering within each toolkit

Export and import in sorted order
```

### API Endpoints Used

The application uses the following IBM BAW/CP4BA REST APIs:

**Authentication:**
- `POST /bas/bpm/system/login` - Obtain CSRF token

**Repository APIs:**
- `GET /dba/studio/repo/projects` - List all projects
- `GET /dba/studio/repo/projects/{project_id}` - Get project details
- `GET /dba/studio/repo/projects/{project_id}/branches` - List all branches
- `GET /dba/studio/repo/projects/{project_id}/branches/{branch_name}/snapshots` - List snapshots
- `GET /dba/studio/repo/projects/{project_id}/branches/{branch_name}/snapshots/{snapshot_name}` - Get snapshot details (including target environment)
- `GET /dba/studio/repo/projects/{project_id}/branches/{branch_name}/snapshots/{snapshot_name}/export` - Export snapshot
- `POST /dba/studio/repo/projects/import` - Import project

**Artifact Management APIs:**
- `GET /bas/artmgt/std/bpm/containers/{container}/versions/{version}/what_used` - Get complete dependency tree

## Project Structure

```
process-app-migrator/
├── pom.xml                                    # Maven configuration
├── README.md                                  # This file
└── src/main/java/com/ibm/baw/migrator/
    ├── WriteTransferPlan.java                # Stage 1: Generate transfer plan
    ├── TransferProjects.java                 # Stage 2: Execute transfer
    ├── ProcessAppMigrator.java               # Legacy: Single-stage migration
    ├── client/
    │   └── BAWApiClient.java                 # REST API client
    ├── model/
    │   ├── Project.java                      # Project model
    │   ├── Snapshot.java                     # Snapshot model
    │   ├── Branch.java                       # Branch model
    │   ├── Property.java                     # Property model
    │   ├── BooleanProperty.java              # Boolean property model
    │   ├── TransferPlan.java                 # Transfer plan model
    │   ├── ProjectsResponse.java             # API response wrapper
    │   ├── SnapshotsResponse.java            # API response wrapper
    │   ├── BranchesResponse.java             # API response wrapper
    │   └── ToolkitDependency.java            # Dependency tree node
    └── service/
        ├── DependencyResolver.java           # Dependency resolution logic
        └── MigrationService.java             # Migration orchestration
```

## Configuration

### Logging

The application uses SLF4J with Simple Logger. To configure logging, create a `simplelogger.properties` file:

```properties
# Set root logger level
org.slf4j.simpleLogger.defaultLogLevel=info

# Set specific logger levels
org.slf4j.simpleLogger.log.com.ibm.baw.migrator=debug

# Log file (optional)
org.slf4j.simpleLogger.logFile=migration.log

# Show date/time
org.slf4j.simpleLogger.showDateTime=true
org.slf4j.simpleLogger.dateTimeFormat=yyyy-MM-dd HH:mm:ss
```

Place this file in the classpath or specify it with:
```bash
java -Dorg.slf4j.simpleLogger.logFile=migration.log -jar process-app-migrator.jar ...
```

## Troubleshooting

### Common Issues

1. **Authentication Failures**
   - Verify credentials are correct
   - Ensure user has appropriate permissions on both systems
   - Check if the user account is locked or expired

3. **Connection Timeouts**
   - Verify network connectivity to both systems
   - Check firewall rules
   - Ensure the BAW servers are running

4. **Import Failures**
   - Check if toolkit dependencies exist on target
   - Verify target system has sufficient resources
   - Review target system logs for detailed error messages

5. **Missing Dependencies**
   - The dependency resolution relies on snapshot properties
   - If dependencies are not detected, check the property names in the API response
   - You may need to adjust the `extractToolkitDependencies` method in `DependencyResolver.java`

### Debug Mode

Enable debug logging to see detailed information:

```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug \
     -jar process-app-migrator.jar ...
```

## Limitations

- Requires both systems to be accessible simultaneously
- Does not migrate user permissions of projects
- System toolkits are automatically excluded from migration

## Security Considerations

- **SSL Certificate Validation**: The application is configured to trust all SSL certificates, including self-signed certificates. This is necessary for many development and test environments but should be used with caution in production.
- **Passwords**: Passwords are passed as command-line arguments (visible in process lists). Consider using environment variables or a secure configuration file for production use.
- **CSRF Tokens**: CSRF tokens are automatically obtained via the `/system/login` API and are managed internally by the application. Tokens are session-specific and time-limited (default 2 hours).
- **Exported Files**: Exported .twx files may contain sensitive business logic. Ensure the export directory has appropriate access controls.
- **Network Security**: Ensure secure network connections between the machine running the tool and both BAW systems.

## Future Enhancements

Potential improvements for future versions:

- Support for configuration files instead of command-line arguments
- Parallel export/import for faster migration
- Resume capability for interrupted migrations
- Dry-run mode to preview migration without executing (partially addressed by two-stage approach)
- Support for other project types (case solutions, decision services)
- Migration validation and verification
- Rollback capability
- Transfer plan editing UI
- Progress tracking and reporting

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review the application logs
3. Consult IBM BAW documentation for API details

## Version History

### 2.2.0 (Toolkit Filtering)
- **NEW**: `--only-include-required-toolkits` flag to include only toolkit versions required by Process Apps
- **NEW**: Uses `what_used` API to precisely track toolkit dependencies with specific versions
- **NEW**: Recursive dependency tracking for nested toolkit dependencies
- **IMPROVED**: Smart interaction between `--max-versions` and `--only-include-required-toolkits` flags
- **IMPROVED**: Reduced migration footprint by filtering out unused toolkit versions
- When both flags are used: `--max-versions` applies only to Process Apps, not toolkits
- Maintains oldest-to-newest ordering of toolkit versions

### 2.1.0 (Performance Enhancements)
- **NEW**: API caching for all GET calls with configurable TTL (default: 500 minutes)
- **NEW**: `--max-versions` flag to limit the number of versions analyzed per project
- **IMPROVED**: Significantly better performance for projects with many versions
- **IMPROVED**: Reduced server load through intelligent caching
- Cache management methods: clearCache(), invalidateCache(), getCacheStats()
- Thread-safe caching implementation using ConcurrentHashMap

### 2.0.0 (Two-Stage Approach)
- **NEW**: Two-stage migration process for better control
- **NEW**: `WriteTransferPlan` - Stage 1 app to generate transfer plan JSON
- **NEW**: `TransferProjects` - Stage 2 app to execute transfer from plan
- **NEW**: `TransferPlan` model for JSON serialization
- Transfer plans can be reviewed and modified before execution
- Original `ProcessAppMigrator` retained for backward compatibility
- Enhanced documentation with examples for both approaches

### 1.1.0 (Branch Support)
- Added support for migrating snapshots from all branches
- New `--ignore-branches` option to maintain backward compatibility
- Enhanced dependency resolution to process all branches
- Updated toolkit migration to handle multi-branch snapshots

### 1.0.0 (Initial Release)
- Basic Project migration functionality
- Automatic toolkit dependency resolution
- Ordered export and import
- Command-line interface
- Comprehensive logging

## License

 Copyright contributors to the IBM BAW Project Export Import project

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
