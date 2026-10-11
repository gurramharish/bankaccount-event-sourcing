# Implementation Plan: Bank Account Event Sourcing & CQRS with Fmodel (Maven)

This implementation plan details the step-by-step phases to build the application following the approved [TECH_SPEC.md](file:///Users/hgurram/bankaccount-event-sourcing/TECH_SPEC.md).

---

## Phase 1: Infrastructure & Maven Project Setup

### Goal
Establish the project skeleton, configure Maven build tooling with Kotlin and Kotlinx Serialization, and provide the containerized runtime infrastructure.

### Tasks
- [x] **1.1 Maven Configuration (`pom.xml`)**:
  - Configure `kotlin-maven-plugin` with Kotlin 2.x and JVM 21 target.
  - Enable `kotlinx-serialization` compiler plugin.
  - Add dependencies:
    - `org.fraktalio.fmodel:fmodel-core:3.0.0`
    - `com.eventstore:eventstoredb-client:5.3.3`
    - `org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3`
    - `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0`
    - `org.postgresql:postgresql:42.7.4`
    - `com.zaxxer:HikariCP:5.1.0`
    - Testing: `org.junit.jupiter:junit-jupiter:5.11.0`, `org.jetbrains.kotlin:kotlin-test-junit5`, `io.kotest:kotest-assertions-core:5.9.1`, `org.testcontainers:testcontainers:1.20.2`, `org.testcontainers:postgresql:1.20.2`.
- [x] **1.2 Docker Infrastructure (`docker-compose.yml`)**:
  - EventStoreDB service: image `eventstore/eventstore:24.10.0-lts-bookworm-slim`, insecure mode, exposed on port `2113`.
  - PostgreSQL service: image `postgres:16-alpine`, exposed on port `5432`, db `bank_account_read_model`.
- [x] **1.3 Sanity Verification**:
  - Run `mvn compile` to verify POM validity and Kotlin compiler plugins.

---

## Phase 2: Domain Layer (Purely Functional Fmodel Decider)

### Goal
Implement the domain logic as pure functions using `fmodel` without side effects, framework couplings, or external I/O.

### Tasks
- [x] **2.1 Domain Models (`com.bank.account.domain.model`)**:
  - `AccountId` (inline value class).
  - `AccountState(accountId, balance, isInitialized)`.
  - `AccountCommand` sealed hierarchy: `Deposit`, `Withdraw`.
  - `AccountEvent` sealed hierarchy: `Deposited`, `Withdrawn`.
  - Custom domain exceptions: `InsufficientFundsException`, `InvalidAmountException`.
- [x] **2.2 The Decider (`com.bank.account.domain.AccountDecider`)**:
  - Define `accountDecider: Decider<AccountCommand, AccountState, AccountEvent>`:
    - `initialState = AccountState()`
    - Pure `decide` lambda: enforces non-negative/positive amounts, checks sufficient balance on `Withdraw`, emits events.
    - Pure `evolve` lambda: deterministic fold computing new `AccountState` given current state and an `AccountEvent`.
- [x] **2.3 Unit Testing (`com.bank.account.domain.AccountDeciderTest`)**:
  - Test initial state.
  - Test valid deposit -> emits `Deposited`, updates balance.
  - Test valid withdrawal -> emits `Withdrawn`, decrements balance.
  - Test overdraft withdrawal -> fails with `InsufficientFundsException`.
  - Test zero and negative amounts -> fails with validation errors.

---

## Phase 3: Serialization & EventStoreDB Adapter Layer

### Goal
Provide robust serialization of domain events and snapshots for EventStoreDB persistence.

### Tasks
- [x] **3.1 Event & Snapshot Serializer (`com.bank.account.infra.serialization`)**:
  - Configure `Json` configuration with polymorphic serialization for `AccountEvent`.
  - Define `AccountSnapshot(state, snapshotRevision, timestamp)` with serialization support.
  - Helper functions to convert between domain objects and EventStoreDB `EventData` / `ResolvedEvent`.
- [x] **3.2 EventStoreDB Client Factory (`com.bank.account.infra.eventstore`)**:
  - Factory to initialize `EventStoreDBClient` targeting `esdb://localhost:2113?tls=false`.

---

## Phase 4: Write-Side Command Handler with Snapshotting (>10 Events)

### Goal
Implement state reconstitution with snapshot replay optimization, command execution, optimistic concurrency, and automatic snapshot persistence.

### Tasks
- [x] **4.1 Snapshot & State Reconstitution Engine (`com.bank.account.application.AccountCommandHandler`)**:
  - Define stream conventions:
    - Primary stream: `account-{accountId}`
    - Snapshot stream: `account-{accountId}-snapshots`
  - Reconstitution logic:
    1. Read the latest event backward (count = 1) from `account-{accountId}-snapshots`.
    2. If found, extract `AccountSnapshot(snapshotState, snapshotRevision)`.
    3. Read forward events from `account-{accountId}` starting from `snapshotRevision + 1`.
    4. If not found, start from revision `0` with `accountDecider.initialState`.
    5. Evolve state using `accountDecider.evolve` with the fetched delta events.
- [x] **4.2 Command Execution & Concurrency**:
  - Invoke `accountDecider.decide(command, currentState)`.
  - Append new events to `account-{accountId}` with `ExpectedRevision.expectedRevision(currentStreamRevision)`.
- [x] **4.3 Snapshot Trigger Logic**:
  - Calculate `eventsSinceLastSnapshot = newStreamRevision - lastSnapshotRevision`.
  - If `eventsSinceLastSnapshot >= 10`:
    - Compute `newSnapshotState = accountDecider.evolve(currentState, newEvents)`.
    - Append `AccountSnapshot(newSnapshotState, newStreamRevision, Instant.now())` to `account-{accountId}-snapshots`.
- [x] **4.4 Integration Tests for Snapshotting (`AccountSnapshotIntegrationTest`)**:
  - Append 9 events -> Verify snapshot stream is empty; reconstitution replays 9 events.
  - Append 10th & 11th event -> Verify snapshot is written with revision.
  - Append 12th event -> Verify reconstitution loads snapshot and replays only delta events.

---

## Phase 5: CQRS Read Model & PostgreSQL Projection Service

### Goal
Implement asynchronous projection from EventStoreDB to PostgreSQL and provide balance queries.

### Tasks
- [ ] **5.1 Database Schema & DataSource (`com.bank.account.read.db`)**:
  - Table initialization script:
    - `current_balance (account_id, balance, last_event_revision, updated_at)`
    - `projection_checkpoint (projection_name, last_position, updated_at)`
  - HikariCP connection pool configuration for PostgreSQL.
- [ ] **5.2 Read Repository (`com.bank.account.read.repository.BalanceRepository`)**:
  - Idempotent upsert of balance updates guarded by `last_event_revision`.
  - Balance query: `fun getBalance(accountId: AccountId): CurrentBalanceView?`.
- [ ] **5.3 EventStoreDB Projection Subscriber (`com.bank.account.read.projection.BalanceProjector`)**:
  - Subscribe to EventStoreDB streams via Catch-Up Subscription (`subscribeToAll` or stream prefix subscription).
  - Coroutine worker processing stream events in order.
  - Update PostgreSQL read model transactionally and persist checkpoint.
- [ ] **5.4 Integration Tests for Read Model (`BalanceProjectorIntegrationTest`)**:
  - Issue write commands.
  - Await projection synchronization.
  - Query PostgreSQL read repository and verify exact balance consistency.

---

## Phase 6: End-to-End Verification & Documentation

### Goal
Validate the full CQRS lifecycle end-to-end and document usage instructions.

### Tasks
- [ ] **6.1 End-to-End Integration Test (`BankCQRSApplicationE2ETest`)**:
  - Start containers (or execute against local docker-compose).
  - Create account, execute batch of 15 deposits and withdrawals.
  - Verify snapshotting triggers at threshold.
  - Verify state reconstitution uses snapshot.
  - Verify PostgreSQL read model is consistent with write-side folded state.
- [ ] **6.2 Application Entrypoint & README**:
  - Provide a runnable demonstration `main()` function.
  - Document docker startup, build (`mvn clean package`), and test execution commands.

---

## Execution Constraints
- Spec-Driven Development: No code will be written until this implementation plan is approved.
- All code changes will strictly follow the Maven and functional architecture rules in [TECH_SPEC.md](file:///Users/hgurram/bankaccount-event-sourcing/TECH_SPEC.md).
