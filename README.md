# IBM BAW/CP4BA Project Export Import

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A Java application that exports IBM Business Automation Workflow (BAW) Workflow Automations (Process Apps and Case) from one system and imports them into another, automatically handling toolkit dependencies. The systems can be BAW or CP4BA environments. The target environment must be at the version of the source or newer. Works for CP4BA Business Applications as well.

## IBM Public Repository Disclosure
All content in this repository including code has been provided by IBM under the associated open source software license and IBM is under no obligation to provide enhancements, updates, or support. IBM developers produced this code as an open source project (not as an IBM product), and IBM makes no assertions as to the level of quality nor security, and will not be maintaining this code going forward.

## Features

- **Automatic dependency resolution**: Identifies and resolves all toolkit dependencies for projects
- **Branch support**: Exports and imports snapshots from all branches (or just the default branch with `--ignore-branches`)
- **Ordered migration**: Exports and imports toolkits in the correct order (leaf-first, oldest version first)
- **System toolkit filtering**: Automatically skips system toolkits that should not be migrated
- **Version management**: Handles all versions/snapshots of toolkits and projects across all branches
- **Batch migration**: Can migrate all projects or specific ones
- **Comprehensive logging**: Detailed logging for troubleshooting and audit trails

## Prerequisites

- Java 11 (JDK 11) or higher
- Access to source and target IBM BAW/CP4BA systems with appropriate credentials
- Network connectivity between the machine running the tool and both BAW/CP4BA systems

## Usage

- Download the latest version of `baw-project-export-import-#.#.#-jar-with-dependencies.jar`

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

> \* Either `--project`, `--projects`, or `--all` must be specified.

**Note**:
- The application automatically obtains CSRF tokens from the `/system/login` API endpoint when the application connects to each system.
- By default, the application processes all branches. Use `--ignore-branches` to only process the default branch.

### Examples

#### Migrate a Specific Project by Name (All Branches)

```bash
java -jar baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  --source-url https://source-baw.company.com:9443 \
  --source-user admin \
  --source-password sourcePassword123 \
  --target-url https://target-baw.company.com:9443 \
  --target-user admin \
  --target-password targetPassword456 \
  --project "Customer Onboarding Process" \
  --export-dir /tmp/baw-exports
```

#### Migrate a Project (Default Branch Only)

```bash
java -jar baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  --source-url https://source-baw.company.com:9443 \
  --source-user admin \
  --source-password sourcePassword123 \
  --target-url https://target-baw.company.com:9443 \
  --target-user admin \
  --target-password targetPassword456 \
  --project "Customer Onboarding Process" \
  --ignore-branches
```

#### Migrate Multiple Projects by Acronym

```bash
java -jar baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  --source-url https://source-baw.company.com:9443 \
  --source-user admin \
  --source-password sourcePassword123 \
  --target-url https://target-baw.company.com:9443 \
  --target-user admin \
  --target-password targetPassword456 \
  --projects COP,IRP,OMS
```

#### Migrate All Projects

```bash
java -jar baw-project-export-import-1.0.0-jar-with-dependencies.jar \
  --source-url https://source-baw.company.com:9443 \
  --source-user admin \
  --source-password sourcePassword123 \
  --target-url https://target-baw.company.com:9443 \
  --target-user admin \
  --target-password targetPassword456 \
  --all
```

## Development
- To modify and build the tool, see the projectXFer folder.

## Limitations
- Requires both systems to be accessible simultaneously
- Does not migrate user permissions of projects
- System toolkits are automatically excluded from migration

## Security Considerations

- **SSL certificate validation**: The application is configured to trust all SSL certificates, including self-signed certificates. This is necessary for many development and test environments but should be used with caution in production.
- **Passwords**: Passwords are passed as command-line arguments (visible in process lists). Consider using environment variables or a secure configuration file for production use.
- **CSRF tokens**: CSRF tokens are automatically obtained via the `/system/login` API and are managed internally by the application. Tokens are session-specific and time-limited (default 2 hours).
- **Exported files**: Exported .twx files may contain sensitive business logic. Ensure the export directory has appropriate access controls.
- **Network security**: Ensure secure network connections between the machine running the tool and both BAW systems.

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
