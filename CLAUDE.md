# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project
mvn clean package

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=StatusManagerTest

# Run a single test method
mvn test -Dtest=StatusManagerTest#testValidTransition
```

## Project Overview

Shared framework module (`gam-framework`) for GAM Joget plugins. Provides centralized status lifecycle management that all GAM plugins depend on. Deployed as a JAR on Joget's shared classpath (`{JOGET_HOME}/wflow/lib/`), not as an OSGi bundle.

**Coordinates:** `com.fiscaladmin.gam:gam-framework:8.1-SNAPSHOT`

## Architecture

All production code is in a single package: `com.fiscaladmin.gam.framework.status`

### Classes

| Class | Role |
|-------|------|
| `Status` | Enum — 28 status values with `code` (DB) and `label` (UI). `fromCode()` for reverse lookup (case-insensitive, returns `null` for unknown codes). |
| `EntityType` | Enum — 7 entity types, each mapped to a bare Joget table name. |
| `StatusManager` | Gatekeeper for all status changes. All methods are `static`. Validates against `TRANSITIONS` map, writes to entity table, writes audit log. |
| `InvalidTransitionException` | Checked exception with full context (entityType, recordId, from/to status). |
| `TransitionAuditEntry` | Immutable `final` DTO for audit records with typed fields (`EntityType`, `Status`). `toFormRow()` converts to Joget `FormRow`. |

### Key Design Principles

- **StatusManager is the single source of truth** for all status transitions. No status changes should bypass this class.
- **Status enum is the single source of truth** for status values. No string literals for statuses anywhere in code.
- All entities use Joget's `FormDataDao` for persistence. Tables are referenced by their bare name (e.g., `bank_statement`), not with the `app_fd_` prefix.
- The `TRANSITIONS` map is fully immutable (`Collections.unmodifiableMap` + `EnumMap`/`EnumSet`).
- The `Status` enum is flat — the same constant (e.g., `CONFIRMED`) can be reused across entity types; entity-specificity is enforced by the transition map.

### Entity Types and Their Tables

| EntityType | Table Name | Initial Status |
|------------|------------|----------------|
| STATEMENT | bank_statement | NEW |
| BANK_TRX | bank_total_trx | NEW |
| SECU_TRX | secu_total_trx | NEW |
| ENRICHMENT | trxEnrichment | NEW |
| PAIR | trx_pair | AUTO_ACCEPTED or PENDING_REVIEW |
| EXCEPTION | exception_queue | OPEN |
| POSTING_OPERATION | posting_operation | PENDING |

### StatusManager API (all methods `static`)

- `transition(dao, entityType, recordId, targetStatus, triggeredBy, reason)` — standard transition using `entityType.getTableName()`
- `transition(dao, tableName, entityType, recordId, targetStatus, triggeredBy, reason)` — custom table name overload (e.g., when Joget form table name differs from EntityType mapping)
- `canTransition(entityType, currentStatus, targetStatus)` — pure validation, no DB access. Handles `null` currentStatus via `isInitialStatus()`.
- `getValidTransitions(entityType, currentStatus)` — returns set of allowed next statuses
- `isInitialStatus(entityType, targetStatus)` — returns `true` if the status is a valid initial status for the entity type, backed by `INITIAL_STATUS_MAP`
- `getFormDataDao()` — convenience to get `FormDataDao` from Joget Spring context

### Status Transitions (Complete)

**STATEMENT:** NEW -> IMPORTING -> IMPORTED -> CONSOLIDATING -> CONSOLIDATED -> ENRICHED -> POSTED. ERROR recovers to NEW.

**BANK_TRX:** NEW -> PROCESSING -> ENRICHED -> {PAIRED, POSTING_READY} -> POSTED. MANUAL_REVIEW escapes to {NEW, ENRICHED, POSTING_READY}. ERROR recovers to NEW.

**SECU_TRX:** NEW -> PROCESSING -> ENRICHED -> {PAIRED, UNMATCHED}. PAIRED -> POSTED. UNMATCHED -> {PAIRED, MANUAL_REVIEW}. MANUAL_REVIEW -> {NEW, ENRICHED, PAIRED}. ERROR recovers to NEW.

**ENRICHMENT (11 from-states):** NEW -> PROCESSING -> ENRICHED -> {IN_REVIEW, ADJUSTED, READY, PAIRED, MANUAL_REVIEW, SUPERSEDED}. Customer review cycle: IN_REVIEW <-> ADJUSTED <-> READY (each can also -> SUPERSEDED). READY -> CONFIRMED (terminal). SUPERSEDED is terminal. PAIRED -> {READY, MANUAL_REVIEW}. ERROR -> {NEW, MANUAL_REVIEW}. MANUAL_REVIEW -> {NEW, ENRICHED, READY}.

**PAIR:** AUTO_ACCEPTED (terminal). PENDING_REVIEW -> {CONFIRMED, REJECTED}. CONFIRMED, REJECTED are terminal.

**EXCEPTION:** OPEN -> {IN_PROGRESS, DISMISSED}. IN_PROGRESS -> {RESOLVED, DISMISSED}. RESOLVED, DISMISSED are terminal.

**POSTING_OPERATION:** PENDING -> {POSTING, REVOKED}. POSTING -> {POSTED, ERROR}. ERROR -> {PENDING, REVOKED}. POSTED, REVOKED are terminal.

### Audit Logging

Every successful transition writes to the `audit_log` table. Fields: `entity_type`, `entity_id`, `from_status`, `to_status`, `triggered_by`, `reason`, `timestamp` (ISO 8601). Audit is NOT written if transition validation fails.

## Dependencies

- **Joget wflow-core 8.1-SNAPSHOT** (provided at runtime by OSGi container)
- **javax.servlet-api 4.0.1** (provided)
- **JUnit 4.13.2 + Mockito 4.11.0** for tests
- Targets **Java 11** (maven-compiler-plugin `<release>11</release>`)

## Testing Patterns

Tests use **Mockito** to mock `FormDataDao`. Pattern:
- Stub `dao.load(null, table, recordId)` to return a `FormRow` with desired status
- Use `ArgumentCaptor` to verify table name and status code in `saveOrUpdate()` calls
- `StatusManager.getTransitionMap()` (package-private) exposes the static map for direct inspection

Test classes: `StatusTest` (39 tests), `EntityTypeTest` (9 tests), `StatusManagerTest` (80 tests).

## Adding a New Entity Type

1. Add constant to `EntityType` with its table name
2. Add any new status values to `Status` enum
3. Add transition map block in `StatusManager` static initializer
4. Add entry to `INITIAL_STATUS_MAP` in `StatusManager`
5. Update `CLAUDE.md` and `README.md`
6. Add tests in all three test classes
