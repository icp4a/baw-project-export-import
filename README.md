# IBM BAW Process App Migrator

A Java application that exports IBM Business Automation Workflow (BAW) Process Apps from one system and imports them into another, automatically handling toolkit dependencies.

## Features

- **Automatic Dependency Resolution**: Identifies and resolves all toolkit dependencies for Process Apps
- **Branch Support**: Exports and imports snapshots from all branches (or just the default branch with `--ignore-branches`)
- **Ordered Migration**: Exports and imports toolkits in the correct order (leaf-first, oldest version first)
- **System Toolkit Filtering**: Automatically skips system toolkits that shouldn't be migrated
- **Version Management**: Handles all versions/snapshots of toolkits and Process Apps across all branches
- **Batch Migration**: Can migrate all Process Apps or specific ones
- **Comprehensive Logging**: Detailed logging for troubleshooting and audit trails

## Prerequisites

- Java 8 (JDK 1.8) or higher
- Access to source and target IBM BAW systems with appropriate credentials
- Network connectivity between the machine running the tool and both BAW systems

## Usage

- Download process-app-migrator-1.0.0-jar-with-dependencies.jar

### Command Line Options

| Option | Description | Required |
|--------|-------------|----------|
| `--source-url` | Source system base URL (e.g., https://source-server:9443) | Yes |
| `--source-user` | Source system username | Yes |
| `--source-password` | Source system password | Yes |
| `--target-url` | Target system base URL (e.g., https://target-server:9443) | Yes |
| `--target-user` | Target system username | Yes |
| `--target-password` | Target system password | Yes |
| `--project` | Name of specific Process App to migrate | No* |
| `--projects` | Comma-separated list of Process App acronyms to migrate | No* |
| `--all` | Migrate all Process Apps | No* |
| `--export-dir` | Directory for exported files (default: ./exports) | No |
| `--ignore-branches` | Only export/import snapshots from the default branch (ignore other branches) | No |
| `--help` | Print help message | No |

*Either `--project`, `--projects`, or `--all` must be specified.

**Note**:
- CSRF tokens are automatically obtained from the `/system/login` API endpoint when the application connects to each system.
- By default, all branches are processed. Use `--ignore-branches` to only process the default branch.

### Examples

#### Migrate a Specific Process App by Name (All Branches)

```bash
java -jar process-app-migrator-1.0.0-jar-with-dependencies.jar \
  --source-url https://source-baw.company.com:9443 \
  --source-user admin \
  --source-password sourcePassword123 \
  --target-url https://target-baw.company.com:9443 \
  --target-user admin \
  --target-password targetPassword456 \
  --project "Customer Onboarding Process" \
  --export-dir /tmp/baw-exports
```

#### Migrate a Process App (Default Branch Only)

```bash
java -jar process-app-migrator-1.0.0-jar-with-dependencies.jar \
  --source-url https://source-baw.company.com:9443 \
  --source-user admin \
  --source-password sourcePassword123 \
  --target-url https://target-baw.company.com:9443 \
  --target-user admin \
  --target-password targetPassword456 \
  --project "Customer Onboarding Process" \
  --ignore-branches
```

#### Migrate Multiple Process Apps by Acronym

```bash
java -jar process-app-migrator-1.0.0-jar-with-dependencies.jar \
  --source-url https://source-baw.company.com:9443 \
  --source-user admin \
  --source-password sourcePassword123 \
  --target-url https://target-baw.company.com:9443 \
  --target-user admin \
  --target-password targetPassword456 \
  --projects COP,IRP,OMS
```

#### Migrate All Process Apps

```bash
java -jar process-app-migrator-1.0.0-jar-with-dependencies.jar \
  --source-url https://source-baw.company.com:9443 \
  --source-user admin \
  --source-password sourcePassword123 \
  --target-url https://target-baw.company.com:9443 \
  --target-user admin \
  --target-password targetPassword456 \
  --all
```

## Development
- For modifying and building the tool, see the projectXFer folder.

## Limitations

- Mainly tested with Process Apps (type: "processapp"), however should also work with Business Applications
- Requires both systems to be accessible simultaneously
- Does not migrate user permissions of project(s)
- System toolkits are automatically excluded from migration

## Security Considerations

- **SSL Certificate Validation**: The application is configured to trust all SSL certificates, including self-signed certificates. This is necessary for many development and test environments but should be used with caution in production.
- **Passwords**: Passwords are passed as command-line arguments (visible in process lists). Consider using environment variables or a secure configuration file for production use.
- **CSRF Tokens**: CSRF tokens are automatically obtained via the `/system/login` API and are managed internally by the application. Tokens are session-specific and time-limited (default 2 hours).
- **Exported Files**: Exported .twx files may contain sensitive business logic. Ensure the export directory has appropriate access controls.
- **Network Security**: Ensure secure network connections between the machine running the tool and both BAW systems.

## License

This tool is provided as-is for use with IBM Business Automation Workflow systems.
