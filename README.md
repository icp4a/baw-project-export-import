# IBM BAW/CP4BA Project Export Import

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A Java application that exports IBM Business Automation Workflow (BAW) Workflow Automations (Process Apps and Case) from one system and imports them into another, automatically handling toolkit dependencies. The systems may be BAW or CP4BA environments. The target environment must be at the version of the source or newer. Works for CP4BA Business Applications as well.

## IBM Public Repository Disclosure
All content in this repository including code has been provided by IBM under the associated open source software license and IBM is under no obligation to provide enhancements, updates, or support. IBM developers produced this code as an open source project (not as an IBM product), and IBM makes no assertions as to the level of quality nor security, and will not be maintaining this code going forward.

## Features

- **Two-Stage Approach**: Separate planning and execution phases for better control and review
- **Transfer Plan Generation**: Creates a JSON file with ordered list of all projects and snapshots to migrate
- **Automatic Dependency Resolution**: Identifies and resolves all toolkit dependencies for Project(s)
- **Branch Support**: Exports and imports snapshots from all branches (or just the default branch with `--ignore-branches`)
- **Ordered Migration**: Exports and imports toolkits in the correct order (leaf-first, oldest version first)
- **System Toolkit Filtering**: Automatically skips system toolkits that shouldn't be migrated
- **Version Management**: Handles all versions/snapshots of toolkits and Projects across all branches
- **Batch Migration**: Can migrate all Projects or specific ones
- **Comprehensive Logging**: Detailed logging for troubleshooting and audit trails

## Prerequisites

- Java 11 (JDK 11) or higher
- Access to source and target IBM BAW/CP4BA systems with appropriate credentials
- Network connectivity between the machine running the tool and both BAW/CP4BA systems

## Development

See the Readme file in the projectXFer directory for more details on development.

## Usage

Download the latest version of `baw-project-export-import-#.#.#-jar-with-dependencies.jar`  
The migration process is split into two stages for better control and review:

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
| `--filter-target-environments` | Comma-separated list of target environments to exclude from the plan (values: BAW_tWAS, BAW, BAW_CP4A, BAW_Liberty) | No |
| `--max-versions` | Maximum number of versions (snapshots) to analyze per project (default: unlimited) | No |
| `--only-include-required-toolkits` | Only include toolkit versions that are required by the Process Apps (filters out unused versions) | No |
| `--help` | Print help message | No |

*Either `--project`, `--projects`, or `--all` must be specified.

#### Example Commands

```bash
# Generate plan for a specific Process App
java -cp baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.WriteTransferPlan \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --project "My Process App" \
  --output my-plan.json

# Generate plan for multiple Process Apps by acronym
java -cp baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.WriteTransferPlan \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --projects PA1,PA2,PA3

# Generate plan for all Process Apps
java -cp baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.WriteTransferPlan \
  --source-url https://source:9443 \
  --source-user admin \
  --source-password pass1 \
  --target-url https://target:9443 \
  --all

# Generate plan with target environment filtering
# This will exclude snapshots with target environment set to DEV or TEST
java -cp baw-project-export-import-1.0.0-jar-with-dependencies.jar \
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
java -cp baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  com.ibm.baw.migrator.TransferProjects \
  --plan transfer-plan.json \
  --source-user admin \
  --source-password pass1 \
  --target-user admin \
  --target-password pass2
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
   - Verify target system has sufficient resources
   - Review target system logs for detailed error messages

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

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review the application logs

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
