# IBM BAW Process App Migrator

A Java application that exports IBM Business Automation Workflow (BAW) Workflow Automations (Process Apps and Case) from one system and imports them into another, automatically handling toolkit dependencies. The systems may be BAW or CP4BA environments. The target environment must be at the version of the source or newer. Works for CP4BA Business Applications as well.

## Features

- **Automatic Dependency Resolution**: Identifies and resolves all toolkit dependencies for Project(s)
- **Branch Support**: Exports and imports snapshots from all branches (or just the default branch with `--ignore-branches`)
- **Ordered Migration**: Exports and imports toolkits in the correct order (leaf-first, oldest version first)
- **System Toolkit Filtering**: Automatically skips system toolkits that shouldn't be migrated
- **Version Management**: Handles all versions/snapshots of toolkits and Projects across all branches
- **Batch Migration**: Can migrate all Projects or specific ones
- **Comprehensive Logging**: Detailed logging for troubleshooting and audit trails

## Prerequisites

- Java 8 (JDK 1.8) or higher
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

### Command Line Options

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
| `--ignore-branches` | Only export/import snapshots from the default branch (ignore other branches) | No |
| `--help` | Print help message | No |

*Either `--project`, `--projects`, or `--all` must be specified.

**Note**:
- CSRF tokens are automatically obtained from the `/system/login` API endpoint when the application connects to each system.
- By default, all branches are processed. Use `--ignore-branches` to only process the default branch.


## How It Works

### Migration Process

1. **Authentication**:
   - Connects to both source and target systems
   - Automatically obtains CSRF tokens via the `/system/login` API endpoint
   - Uses Basic Authentication with provided credentials

2. **Project Discovery**: Retrieves the specified Project(s) from the source system

3. **Branch Discovery**:
   - Retrieves all branches for the Project (or just the default branch if `--ignore-branches` is specified)
   - Processes each branch independently

4. **Dependency Analysis**: For each Project and branch:
   - Retrieves all snapshots/versions from the branch
   - Uses the `/what_used` API endpoint to get the complete dependency tree in a single call
   - Recursively processes dependencies (toolkits may depend on other toolkits)
   - For each toolkit dependency, retrieves snapshots from all branches (or just default branch)
   - Filters out system toolkits

5. **Dependency Ordering**:
   - Calculates dependency depth for each toolkit
   - Orders toolkits so leaf nodes (no dependencies) are processed first
   - Within each toolkit and branch, orders snapshots by creation date (oldest first)

6. **Export Phase**:
   - Exports each toolkit snapshot from all branches from the source system as .twx files
   - Stores exported files in the specified export directory

7. **Import Phase**:
   - Imports toolkits in the calculated order to the target system
   - Checks if toolkits already exist on target to avoid duplicates
   - Imports all versions of each toolkit from all branches in chronological order
   - Finally imports the top level project snapshots from all branches

### Dependency Resolution Algorithm

The application uses a depth-first search algorithm to resolve dependencies:

```
For each Process App:
  For each branch (or just default branch if --ignore-branches):
    For each snapshot in branch:
      Extract toolkit dependencies
      For each toolkit dependency:
        If not system toolkit:
          Add to dependency tree
          For each branch of toolkit (or just default):
            Collect all snapshots from branch
          Recursively resolve toolkit's dependencies
          Calculate depth (leaf nodes have highest depth)
        
Sort all dependencies by depth (descending)
Export and import in sorted order (all branches, all snapshots)
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
    ├── ProcessAppMigrator.java               # Main application entry point
    ├── client/
    │   └── BAWApiClient.java                 # REST API client
    ├── model/
    │   ├── Project.java                      # Project model
    │   ├── Snapshot.java                     # Snapshot model
    │   ├── Branch.java                       # Branch model
    │   ├── Property.java                     # Property model
    │   ├── BooleanProperty.java              # Boolean property model
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
- Dry-run mode to preview migration without executing
- Support for other project types (case solutions, decision services)
- Migration validation and verification
- Rollback capability

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

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review the application logs
3. Consult IBM BAW documentation for API details

## Version History

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
