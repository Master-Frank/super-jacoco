## ADDED Requirements

### Requirement: Protect coverage APIs with authentication
The system MUST require authentication for all endpoints under `/cov/**` by default.

#### Scenario: Unauthenticated request is rejected
- **WHEN** a request is made to any `/cov/**` endpoint without valid credentials
- **THEN** the system returns an authentication error and does not start any background task

#### Scenario: Authenticated request is allowed
- **WHEN** a request is made to any `/cov/**` endpoint with valid credentials
- **THEN** the system processes the request normally

### Requirement: Support configurable IP allowlist
The system MUST support an optional IP allowlist for `/cov/**` endpoints.

#### Scenario: Request from non-allowlisted IP is rejected
- **WHEN** IP allowlist is enabled and a request originates from a non-allowlisted IP
- **THEN** the system rejects the request and does not start any background task

#### Scenario: Request from allowlisted IP is accepted
- **WHEN** IP allowlist is enabled and a request originates from an allowlisted IP
- **THEN** the system evaluates authentication and continues processing

### Requirement: Provide basic request throttling for high-cost actions
The system MUST provide a configurable concurrency guard for endpoints that trigger clone/compile/dump/report actions.

#### Scenario: Concurrency limit prevents overload
- **WHEN** the number of in-flight background tasks exceeds the configured limit
- **THEN** the system rejects new trigger requests with a clear error response

