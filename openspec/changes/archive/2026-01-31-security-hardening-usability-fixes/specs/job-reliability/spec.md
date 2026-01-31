## ADDED Requirements

### Requirement: Use a unified background execution model
The system MUST execute background work using a single, configurable executor rather than ad-hoc threads.

#### Scenario: Triggered work is queued to the executor
- **WHEN** a trigger endpoint is called successfully
- **THEN** the system submits the job to the configured executor and returns promptly

### Requirement: Maintain consistent task status transitions
The system MUST update task status transitions deterministically and persist them reliably.

#### Scenario: Successful task reaches terminal success status
- **WHEN** a job completes all required steps without errors
- **THEN** the task status becomes a terminal success status and result fields are persisted

#### Scenario: Failed task reaches terminal failure status
- **WHEN** a job fails at any step
- **THEN** the task status becomes a terminal failure status, an error message is recorded, and no success result is returned

### Requirement: Improve error observability
The system MUST record actionable error information for failures without exposing secrets.

#### Scenario: Failure records stack trace in server logs
- **WHEN** an unexpected exception occurs
- **THEN** the server logs contain the full exception stack trace and correlation identifiers for the task

#### Scenario: API returns safe error response
- **WHEN** a request fails due to internal error
- **THEN** the API returns a safe error message without sensitive details

