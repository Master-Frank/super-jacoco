## ADDED Requirements

### Requirement: Execute external commands without shell interpretation
The system MUST execute external commands without invoking a shell interpreter (e.g., must not rely on `bash -c`).

#### Scenario: Command is executed via parameterized invocation
- **WHEN** the system runs Maven, JaCoCo CLI, or file operations
- **THEN** it uses a parameterized process invocation where each argument is a distinct element

### Requirement: Validate command inputs with strict allowlists
The system MUST validate all inputs that may influence command execution or filesystem access.

#### Scenario: Reject unsafe input
- **WHEN** an input contains disallowed characters or violates an allowlist rule
- **THEN** the system rejects the request and records a validation error

#### Scenario: Restrict filesystem access to approved roots
- **WHEN** a path is derived from request or persisted data
- **THEN** the system ensures the resolved path is within configured working/report roots

### Requirement: Enforce timeouts and capture exit status
The system MUST enforce configurable timeouts for external commands and MUST capture the exit code.

#### Scenario: Command timeout is handled
- **WHEN** an external command exceeds its configured timeout
- **THEN** the system terminates the process, records a timeout failure, and updates task status accordingly

#### Scenario: Non-zero exit code is handled
- **WHEN** an external command returns a non-zero exit code
- **THEN** the system records the failure with the command context and does not report a successful coverage result

