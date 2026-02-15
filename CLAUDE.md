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

## Architecture

This is a shared framework module (`gam-framework`) for GAM Joget plugins. It provides centralized status lifecycle management that all GAM plugins depend on.

### Key Design Principles

- **StatusManager is the single source of truth** for all status transitions. No status changes should bypass this class.
- **Status enum is the single source of truth** for status values. No string literals for statuses anywhere in code.
- All entities use Joget's `FormDataDao` for persistence. Tables are referenced by their bare name (e.g., `bank_statement`), not with the `app_fd_` prefix.

### Entity Types and Their Tables

| EntityType | Table Name | Initial Status |
|------------|------------|----------------|
| STATEMENT | bank_statement | NEW |
| BANK_TRX | bank_total_trx | NEW |
| SECU_TRX | secu_total_trx | NEW |
| ENRICHMENT | trx_enrichment | NEW |
| PAIR | trx_pair | AUTO_ACCEPTED or PENDING_REVIEW |
| EXCEPTION | exception_queue | OPEN |

### Status Transition Flow

The `TRANSITIONS` map in `StatusManager` defines all valid state transitions per entity type. Key patterns:
- Most entities start at `NEW` and can recover from `ERROR` back to `NEW`
- `MANUAL_REVIEW` is an escape hatch allowing re-routing to multiple states
- Terminal states (like `POSTED`, `RESOLVED`, `REJECTED`) have empty transition sets

### Audit Logging

Every status transition writes an entry to the `audit_log` table via `TransitionAuditEntry`. Fields include entity type, record ID, from/to status, triggeredBy (plugin name or "OPERATOR"), reason, and ISO 8601 timestamp.

## Dependencies

- **Joget wflow-core 8.1-SNAPSHOT** (provided at runtime by OSGi container)
- **JUnit 4 + Mockito 4** for tests
- Targets **Java 11**
