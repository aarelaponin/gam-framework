# GAM Framework Specification
## Foundational Status Management Library for Joget DX8 Enterprise

**Package:** `com.fiscaladmin.gam.framework.status`
**Version:** 8.1-SNAPSHOT
**Target System:** Small Investment Bank Accounting System on Joget DX8 Enterprise
**Transaction Volume:** ~20-25 transactions/day
**Last Updated:** 2026-03-03

---

## Table of Contents

1. [Purpose and Scope](#purpose-and-scope)
2. [Architecture](#architecture)
3. [Status Enum Reference](#status-enum-reference)
4. [EntityType Enum Reference](#entitytype-enum-reference)
5. [State Machine Specifications](#state-machine-specifications)
6. [StatusManager API](#statusmanager-api)
7. [Audit Logging](#audit-logging)
8. [Integration Rules](#integration-rules)
9. [Cross-Plugin Transition Responsibilities](#cross-plugin-transition-responsibilities)
10. [Configuration and Dependencies](#configuration-and-dependencies)
11. [Testing Requirements](#testing-requirements)

---

## 1. Purpose and Scope

### 1.1 Overview

The GAM Framework (`gam-framework`) is the **single source of truth** for status lifecycle management across the investment bank accounting system. It provides:

- A centralized, immutable definition of all valid status values
- A comprehensive state machine engine that enforces valid status transitions
- An automatic audit trail for all status changes
- A consistent API for all plugins to safely transition entity statuses
- Prevention of invalid state transitions through compile-time type safety and runtime validation

### 1.2 Key Principles

- **No Direct Status Updates:** Plugins MUST NOT write status directly to the database; all transitions MUST go through `StatusManager`
- **Type-Safe Enums:** Status and EntityType are enums, eliminating string-literal errors
- **Fail-Safe Transitions:** Invalid transitions throw `InvalidTransitionException` rather than silently failing
- **Immutable Audit Trail:** Every transition is automatically logged to `audit_log` with full metadata
- **Framework Independence:** The library is pure Java with Joget Spring Context integration; no plugin-specific logic
- **Transaction Volume Appropriate:** Designed for ~20-25 transactions/day with minimal computational overhead

### 1.3 Out of Scope

- Business logic for any specific workflow phase (that belongs to consuming plugins)
- UI/presentation layer details
- Form field validation beyond status transitions
- Authorization/permission checking (delegated to consuming plugins)

---

## 2. Architecture

### 2.1 Package Structure

```
com.fiscaladmin.gam.framework
├── status/
│   ├── Status.java                    (28-value enum)
│   ├── EntityType.java                (7-value enum)
│   ├── StatusManager.java             (Main API class)
│   ├── TransitionAuditEntry.java      (Immutable DTO)
│   └── InvalidTransitionException.java (Exception)
└── [future expansion modules]
```

### 2.2 Class Diagram (Text Representation)

```
┌─────────────────────────────────────────────────────────────────┐
│ StatusManager (Static API)                                       │
├─────────────────────────────────────────────────────────────────┤
│ - TRANSITION_MAP: Map<EntityType, Map<Status, Set<Status>>>     │
│ - INITIAL_STATUS_MAP: Map<EntityType, Set<Status>>              │
├─────────────────────────────────────────────────────────────────┤
│ + transition(dao, entityType, recordId, targetStatus, ...) : v  │
│ + transition(dao, tableName, entityType, recordId, ...) : void  │
│ + canTransition(entityType, current, target) : boolean          │
│ + getValidTransitions(entityType, current) : Set<Status>        │
│ + getFormDataDao() : FormDataDao                                │
│ + isInitialStatus(entityType, status) : boolean                 │
└─────────────────────────────────────────────────────────────────┘
         │ uses           │ creates
         ▼                ▼
    ┌──────────┐    ┌──────────────────────┐
    │ Status   │    │ TransitionAuditEntry │
    │ (28 enum)│    │ (Immutable DTO)      │
    └──────────┘    └──────────────────────┘
         ▲
         │ groups
    ┌──────────────┐
    │ EntityType   │
    │ (7 enum)     │
    └──────────────┘

┌──────────────────────────────────────────┐
│ InvalidTransitionException               │
│ (extends Exception - checked exception)  │
└──────────────────────────────────────────┘
```

### 2.3 Dependencies

**Maven Dependency:**

```xml
<dependency>
    <groupId>com.fiscaladmin.gam</groupId>
    <artifactId>gam-framework</artifactId>
    <version>8.1-SNAPSHOT</version>
</dependency>
```

**Internal Dependencies:**
- JDK 8+
- Joget DX8 FormDataDao (via Spring Context)
- No external database drivers required (uses Joget's abstraction layer)

**Framework Integration Points:**
- Spring ApplicationContext (for FormDataDao lookup)
- Joget's FormDataDao (for database persistence)
- Joget's FormRow (for audit trail persistence)

---

## 3. Status Enum Reference

### 3.1 Complete Status Listing

The `Status` enum contains **28 distinct status values**, each with:
- **Code:** lowercase identifier used in database (case-insensitive lookup via `fromCode()`)
- **Label:** UI-friendly display text
- **Entity Types:** which entity types use this status
- **Category:** functional grouping for documentation

| # | Status Name | Code | Label | Entity Types | Category |
|---|---|---|---|---|---|
| 1 | NEW | `new` | New | STATEMENT, BANK_TRX, SECU_TRX, ENRICHMENT | Universal |
| 2 | ERROR | `error` | Error | STATEMENT, BANK_TRX, SECU_TRX, ENRICHMENT, POSTING_OPERATION | Universal |
| 3 | IMPORTING | `importing` | Importing | STATEMENT | Statement-Level |
| 4 | IMPORTED | `imported` | Imported | STATEMENT | Statement-Level |
| 5 | CONSOLIDATING | `consolidating` | Consolidating | STATEMENT | Statement-Level |
| 6 | CONSOLIDATED | `consolidated` | Consolidated | STATEMENT | Statement-Level |
| 7 | PROCESSING | `processing` | Processing | BANK_TRX, SECU_TRX, ENRICHMENT | Transaction-Level |
| 8 | ENRICHED | `enriched` | Enriched | STATEMENT, BANK_TRX, SECU_TRX, ENRICHMENT | Transaction-Level |
| 9 | PAIRED | `paired` | Paired | BANK_TRX, SECU_TRX, ENRICHMENT | Transaction-Level |
| 10 | POSTING_READY | `posting_ready` | Posting Ready | BANK_TRX | Transaction-Level |
| 11 | POSTED | `posted` | Posted | STATEMENT, BANK_TRX, SECU_TRX, PAIR, POSTING_OPERATION | Transaction-Level |
| 12 | MANUAL_REVIEW | `manual_review` | Manual Review | BANK_TRX, SECU_TRX, ENRICHMENT | Transaction-Level |
| 13 | UNMATCHED | `unmatched` | Unmatched | SECU_TRX | Transaction-Level |
| 14 | AUTO_ACCEPTED | `auto_accepted` | Auto Accepted | PAIR | Pair-Level |
| 15 | PENDING_REVIEW | `pending_review` | Pending Review | PAIR | Pair-Level |
| 16 | CONFIRMED | `confirmed` | Confirmed | PAIR, ENRICHMENT | Pair-Level |
| 17 | REJECTED | `rejected` | Rejected | PAIR | Pair-Level |
| 18 | OPEN | `open` | Open | EXCEPTION | Exception-Level |
| 19 | IN_PROGRESS | `in_progress` | In Progress | EXCEPTION, ENRICHMENT | Exception-Level |
| 20 | RESOLVED | `resolved` | Resolved | EXCEPTION | Exception-Level |
| 21 | DISMISSED | `dismissed` | Dismissed | EXCEPTION | Exception-Level |
| 22 | IN_REVIEW | `in_review` | In Review | ENRICHMENT | Enrichment Workspace |
| 23 | ADJUSTED | `adjusted` | Adjusted | ENRICHMENT | Enrichment Workspace |
| 24 | READY | `ready` | Ready | ENRICHMENT | Enrichment Workspace |
| 25 | SUPERSEDED | `superseded` | Superseded | ENRICHMENT | Enrichment Workspace |
| 26 | PENDING | `pending` | Pending | POSTING_OPERATION | Posting Operation |
| 27 | POSTING | `posting` | Posting | POSTING_OPERATION | Posting Operation |
| 28 | REVOKED | `revoked` | Revoked | POSTING_OPERATION | Posting Operation |

### 3.2 Status Code Resolution

The `Status` enum provides:

```java
// Case-insensitive lookup from database value
Status status = Status.fromCode("NEW");        // Works
Status status = Status.fromCode("new");        // Also works
Status status = Status.fromCode("New");        // Also works
Status status = Status.fromCode("invalid");    // Returns null

// Get code (database representation)
String code = Status.NEW.getCode();            // "new"

// Get label (UI display)
String label = Status.NEW.getLabel();          // "New"
```

---

## 4. EntityType Enum Reference

### 4.1 Entity Type Definitions

The `EntityType` enum defines **7 entity types**, each mapping to a Joget form table:

| # | EntityType | Constant | Table Name (Joget) | Form Name | Purpose |
|---|---|---|---|---|---|
| 1 | STATEMENT | `STATEMENT` | `bank_statement` | Bank Statement | Parent container for imported transactions |
| 2 | BANK_TRX | `BANK_TRX` | `bank_total_trx` | Bank Transaction | Individual bank transactions (cash flows) |
| 3 | SECU_TRX | `SECU_TRX` | `secu_total_trx` | Security Transaction | Individual security transactions (holdings) |
| 4 | ENRICHMENT | `ENRICHMENT` | `trxEnrichment` | Transaction Enrichment | Enrichment workspace for transaction metadata |
| 5 | PAIR | `PAIR` | `trx_pair` | Transaction Pair | Bank-Security transaction pairing records |
| 6 | EXCEPTION | `EXCEPTION` | `exception_queue` | Exception Queue | Exception handling and resolution tracking |
| 7 | POSTING_OPERATION | `POSTING_OPERATION` | `posting_operation` | Posting Operation | GL posting batch records |

### 4.2 ENRICHMENT Table Name

`EntityType.ENRICHMENT.getTableName()` returns `"trxEnrichment"` (camelCase), matching the actual Joget form table name. The standard 6-arg `transition()` overload works for ENRICHMENT like every other entity type. The custom table name overload exists as a general-purpose escape hatch for any future cases where an entity's form table name differs from the enum mapping.

---

## 5. State Machine Specifications

### 5.1 STATEMENT Entity Type

**Entity Type Code:** `STATEMENT`
**Table Name:** `bank_statement`
**Initial Statuses:** `NEW`
**Terminal Statuses:** `POSTED`, `ERROR`

#### 5.1.1 State Diagram

```
    ┌─────────────────────────────────────────┐
    │                   NEW                   │
    │          (Initial Status)               │
    └────────────┬────────────────────────────┘
                 │
                 │ start_import()
                 ▼
         ┌───────────────┐
         │   IMPORTING   │
         │   (in-flight) │
         └────┬──────┬───┘
              │      │
    success   │      │ error
              │      │
              ▼      ▼
        ┌─────────┐ ┌──────────┐
        │IMPORTED │ │  ERROR   │
        └────┬────┘ │(terminal)│
             │      └──────────┘
             │
             │ start_consolidation()
             ▼
    ┌────────────────────┐
    │  CONSOLIDATING     │
    │    (in-flight)     │
    └────┬────────────┬──┘
         │            │
    success│          │ error
         │            │
         ▼            ▼
    ┌──────────────┐ ┌──────────┐
    │CONSOLIDATED │ │  ERROR   │
    └────┬────────┘ │(terminal)│
         │          └──────────┘
         │
         │ start_enrichment()
         ▼
    ┌────────────┐
    │  ENRICHED  │
    └────┬───────┘
         │
         │ post()
         ▼
    ┌──────────────┐
    │   POSTED     │
    │  (terminal)  │
    └──────────────┘

Transition back to NEW from ERROR via reset operation
```

#### 5.1.2 STATEMENT Transition Table

| From | To | Triggering Plugin | Business Rule / Trigger Description |
|---|---|---|---|
| NEW | IMPORTING | statement-importer | Bank statement import begins; file parsing starts |
| IMPORTING | IMPORTED | statement-importer | Import completed successfully; all rows parsed |
| IMPORTING | ERROR | statement-importer | Import failed (file format error, parsing error, connectivity issue) |
| IMPORTED | CONSOLIDATING | statement-importer | Consolidation of imported transactions begins |
| CONSOLIDATING | CONSOLIDATED | statement-importer | Consolidation completed; statement ready for enrichment |
| CONSOLIDATING | ERROR | statement-importer | Consolidation failed (data integrity issue, missing reference data) |
| CONSOLIDATED | ENRICHED | rows-enrichment | All child transactions enriched; statement marked ready for posting |
| CONSOLIDATED | ERROR | rows-enrichment | Enrichment failed at statement level (critical data missing) |
| ENRICHED | POSTED | gl-preparator | GL posting completed for all child transactions |
| ERROR | NEW | statement-importer | Manual reset via admin operation; retry initiated |

#### 5.1.3 Lifecycle Overview

1. **STATEMENT begins in NEW** when created by statement-importer plugin
2. **Transitions to IMPORTING** when import file is provided and parsing begins
3. **Transitions to IMPORTED** on successful row-level parsing
4. **Transitions to CONSOLIDATING** when consolidation logic begins (duplicate detection, totals matching, etc.)
5. **Transitions to CONSOLIDATED** when all consolidation rules pass
6. **Transitions to ENRICHED** when child BANK_TRX and SECU_TRX reach ENRICHED status
7. **Transitions to POSTED** when GL posting is complete
8. **Can revert to NEW from ERROR** for retry scenarios (manual admin operation)
9. **ERROR can occur** at any point before POSTED; indicates the statement cannot progress until root cause is resolved

---

### 5.2 BANK_TRX Entity Type

**Entity Type Code:** `BANK_TRX`
**Table Name:** `bank_total_trx`
**Initial Statuses:** `NEW`
**Terminal Statuses:** `POSTED`, `ERROR`

#### 5.2.1 State Diagram

```
         ┌──────────────┐
         │     NEW      │
         │(Initial Stat)│
         └────┬─────────┘
              │
              │ process()
              ▼
    ┌──────────────────┐
    │   PROCESSING     │
    │   (in-flight)    │
    └──┬─────────┬────┬┘
       │         │    │
       │         │    └─────── error ──→ ┌──────────┐
       │         │                       │  ERROR   │
       │         │                       │(terminal)│
       │         │                       └──────────┘
       │         │
    success     manual_review
       │         │
       ▼         ▼
    ┌────────┐ ┌──────────────┐
    │ENRICHED│ │MANUAL_REVIEW │
    │        │ │              │
    └┬──┬────┘ └┬───┬────┬────┘
     │  │       │   │    │
     │  │      reset enrich posting
     │  │       │   │    │
     │  │       ▼   ▼    ▼
     │  │      NEW ENRICHED POSTING_READY
     │  │                │
     │  │ (re-enter)     │
    pair posting_ready   │
     │  │                │
     │  │                ▼
     │  └────────────────────→ ┌─────────────────┐
     │                         │  POSTING_READY  │
     │                         └────┬────────────┘
     │                              │
     │                              │ post()
     └──────────────────────────────┤
                                    │
                                    ▼
                            ┌──────────────┐
                            │   POSTED     │
                            │ (terminal)   │
                            └──────────────┘
```

#### 5.2.2 BANK_TRX Transition Table

| From | To | Triggering Plugin | Business Rule / Trigger Description |
|---|---|---|---|
| NEW | PROCESSING | rows-enrichment | Enrichment pipeline begins for bank transaction |
| PROCESSING | ENRICHED | rows-enrichment | Transaction successfully enriched (counterparty added, GL codes resolved, etc.) |
| PROCESSING | MANUAL_REVIEW | rows-enrichment | Enrichment blocked; transaction requires human review (ambiguous counterparty, validation failure) |
| PROCESSING | ERROR | rows-enrichment | Processing failed (data corruption, system failure) |
| ENRICHED | PAIRED | rows-enrichment or enrichment-api | Transaction paired with matching SECU_TRX (or marked as unpaired) |
| ENRICHED | POSTING_READY | rows-enrichment | Transaction ready for GL posting without pairing |
| ENRICHED | MANUAL_REVIEW | enrichment-api | User flags transaction in enrichment workspace for review |
| POSTING_READY | POSTED | gl-preparator | GL posting completed for this transaction |
| PAIRED | POSTED | gl-preparator | GL posting completed for paired transaction |
| MANUAL_REVIEW | NEW | enrichment-workspace | User discards changes; transaction reverted to NEW for re-enrichment |
| MANUAL_REVIEW | ENRICHED | enrichment-workspace | User confirms enrichment changes; transaction re-enters ENRICHED |
| MANUAL_REVIEW | POSTING_READY | enrichment-workspace | User confirms transaction ready for posting without pairing |
| ERROR | NEW | statement-importer | Manual reset; transaction re-processed |

#### 5.2.3 Lifecycle Overview

1. **BANK_TRX begins in NEW** when created by statement-importer plugin from bank statement import
2. **Transitions to PROCESSING** when rows-enrichment plugin begins enrichment (lookup of counterparties, GL code resolution, etc.)
3. **Can transition to:**
   - **ENRICHED** on successful enrichment completion
   - **MANUAL_REVIEW** if enrichment rules identify ambiguity or validation failures requiring human judgment
   - **ERROR** if unrecoverable processing failure occurs
4. **From ENRICHED:**
   - Transitions to **PAIRED** if matched with SECU_TRX by enrichment-api
   - Transitions to **POSTING_READY** if enrichment complete and no pairing needed
   - Transitions to **MANUAL_REVIEW** if user flags in enrichment workspace
5. **From MANUAL_REVIEW:**
   - Reverts to **NEW** (user discards all changes)
   - Transitions to **ENRICHED** (user confirms changes)
   - Transitions to **POSTING_READY** (user confirms ready without pairing)
6. **From POSTING_READY or PAIRED:**
   - Transitions to **POSTED** when gl-preparator executes posting
7. **ERROR → NEW** allows manual reset for retry

---

### 5.3 SECU_TRX Entity Type

**Entity Type Code:** `SECU_TRX`
**Table Name:** `secu_total_trx`
**Initial Statuses:** `NEW`
**Terminal Statuses:** `POSTED`, `ERROR`

#### 5.3.1 State Diagram

```
         ┌──────────────┐
         │     NEW      │
         │(Initial Stat)│
         └────┬─────────┘
              │
              │ process()
              ▼
    ┌──────────────────┐
    │   PROCESSING     │
    │   (in-flight)    │
    └──┬─────────┬────┬┘
       │         │    │
       │         │    └─────── error ──→ ┌──────────┐
       │         │                       │  ERROR   │
       │         │                       │(terminal)│
       │         │                       └──────────┘
       │         │
    success     manual_review
       │         │
       ▼         ▼
    ┌────────┐ ┌──────────────┐
    │ENRICHED│ │MANUAL_REVIEW │
    │        │ │              │
    └┬──┬────┘ └┬───┬────┬────┘
     │  │       │   │    │
     │  │      reset enrich pairing
     │  │       │   │    │
     │  │       ▼   ▼    ▼
     │  │      NEW ENRICHED PAIRED
     │  │                │
    pair unmatched       │
     │  │     │          │
     │  │     └─────┐    │
     │  │           │    │
     │  │    ┌──────────┐│
     │  │    │UNMATCHED ││
     │  │    └┬─────┬───┘│
     │  │      │    │    │
     │  │   pair review  │
     │  │      │    │    │
     │  │      ▼    ▼    │
     │  │    PAIRED MANUAL_REVIEW
     │  │      │         │
     │  └──────┼─────────┘
     │         │
     └────────→┤
               │ post()
               ▼
        ┌──────────────┐
        │   POSTED     │
        │ (terminal)   │
        └──────────────┘
```

#### 5.3.2 SECU_TRX Transition Table

| From | To | Triggering Plugin | Business Rule / Trigger Description |
|---|---|---|---|
| NEW | PROCESSING | rows-enrichment | Enrichment pipeline begins for security transaction |
| PROCESSING | ENRICHED | rows-enrichment | Transaction successfully enriched (instrument code verified, quantity validated, etc.) |
| PROCESSING | MANUAL_REVIEW | rows-enrichment | Enrichment blocked; requires human review (instrument lookup failed, quantity anomaly) |
| PROCESSING | ERROR | rows-enrichment | Processing failed (system error) |
| ENRICHED | PAIRED | enrichment-api | Successfully paired with matching BANK_TRX |
| ENRICHED | UNMATCHED | enrichment-api | No matching BANK_TRX found after pairing rules applied |
| ENRICHED | MANUAL_REVIEW | enrichment-api | User flags for review in enrichment workspace |
| UNMATCHED | PAIRED | enrichment-api | User manually created pair in enrichment workspace |
| UNMATCHED | MANUAL_REVIEW | enrichment-api | User marked unmatched transaction for review |
| PAIRED | POSTED | gl-preparator | GL posting completed for paired transaction |
| MANUAL_REVIEW | NEW | enrichment-workspace | User discards changes; reverted for re-enrichment |
| MANUAL_REVIEW | ENRICHED | enrichment-workspace | User confirms enrichment; transitions to ENRICHED |
| MANUAL_REVIEW | PAIRED | enrichment-workspace | User manually created pair or confirmed manual pairing |
| ERROR | NEW | statement-importer | Manual reset; transaction re-processed |

#### 5.3.3 Lifecycle Overview

1. **SECU_TRX begins in NEW** when created by statement-importer plugin from bank statement import
2. **Transitions to PROCESSING** when rows-enrichment begins enrichment (instrument code lookup, quantity validation, GL code resolution)
3. **Can transition to:**
   - **ENRICHED** on successful enrichment
   - **MANUAL_REVIEW** if enrichment rules require human judgment
   - **ERROR** if unrecoverable failure occurs
4. **From ENRICHED:**
   - Transitions to **PAIRED** if matched with BANK_TRX by enrichment-api
   - Transitions to **UNMATCHED** if no matching BANK_TRX found (securities holdings are unmatched cash flows)
   - Transitions to **MANUAL_REVIEW** if user flags in workspace
5. **From UNMATCHED:**
   - Transitions to **PAIRED** if user manually creates pairing
   - Transitions to **MANUAL_REVIEW** if user flags for review
6. **From MANUAL_REVIEW:**
   - Reverts to **NEW** (discard all changes)
   - Transitions to **ENRICHED** (confirm changes)
   - Transitions to **PAIRED** (manual pairing created by user)
7. **From PAIRED:**
   - Transitions to **POSTED** when gl-preparator executes posting
8. **ERROR → NEW** allows manual reset for retry

---

### 5.4 ENRICHMENT Entity Type (Most Complex)

**Entity Type Code:** `ENRICHMENT`
**Table Name:** `trxEnrichment`
**Initial Statuses:** `NEW`
**Terminal Statuses:** `CONFIRMED`, `SUPERSEDED`, `ERROR` (when no recovery path available)

#### 5.4.1 State Diagram (11 from-states, most complex)

```
Create enrichment workspace (split/merge)
         │
         ▼
    ┌──────────┐
    │   NEW    │
    │(Initial) │
    └────┬─────┘
         │
         │ process()
         ▼
    ┌──────────────┐
    │  PROCESSING  │
    └──┬──────┬────┬─┘
       │      │    │
    succ man  error│
       │      │    │
       ▼      ▼    ▼
    ┌────┐ ┌──────────┐ ┌──────────┐
    │ENRI│ │MANUAL_REV│ │  ERROR   │
    │CHED│ │IEW       │ │(terminal)│
    └────┘ └──────────┘ └──────────┘
     ▲│▼      ▲│▼
     ││└──────→│└──────→ NEW (reset)
     ││       │└────────→ ENRICHED (confirm)
     ││       └────────→ READY (confirm ready)
     │└──────────────────────┘
     │
     │ From ENRICHED:
     │ - submit_to_review()
     │ - request_adjustment()
     │ - mark_ready()
     │ - mark_as_pair()
     │ - supersede()
     │
     ├──────────→ IN_REVIEW
     │           (user workspace review)
     │
     ├──────────→ ADJUSTED
     │           (workspace adjustments)
     │
     ├──────────→ READY
     │           (approval ready)
     │
     ├──────────→ PAIRED
     │           (if matched)
     │
     └──────────→ SUPERSEDED
                 (if replaced)

IN_REVIEW state:
    ┌──────────────┐
    │  IN_REVIEW   │
    └──┬────┬──┬──┬┘
       │    │  │  │
    adjust ready revert supersede
       │    │  │  │
       ▼    ▼  ▼  ▼
    ADJ READY ENRI SUPERSEDED

ADJUSTED state:
    ┌──────────────┐
    │  ADJUSTED    │
    └──┬────┬──┬──┬┘
       │    │  │  │
    ready review revert supersede
       │    │  │  │
       ▼    ▼  ▼  ▼
    READY IN_REV ENRI SUPERSEDED

READY state:
    ┌──────────────┐
    │   READY      │
    └──┬─┬──┬──┬──┬┘
       │ │  │  │  │
   confirm review in_rev adjust supersede
       │ │  │  │  │
       ▼ ▼  ▼  ▼  ▼
    CONF IN_ ENRI  SUPERSEDED
              REV

PAIRED state:
    ┌──────────────┐
    │   PAIRED     │
    └──┬─┬─────────┘
       │ │
     ready manual_review
       │ │
       ▼ ▼
    READY MANUAL_REVIEW

CONFIRMED state:
    ┌──────────────┐
    │ CONFIRMED    │
    │  (terminal)  │
    └──────────────┘

SUPERSEDED state:
    ┌──────────────┐
    │ SUPERSEDED   │
    │  (terminal)  │
    └──────────────┘
```

#### 5.4.2 ENRICHMENT Transition Table (11 from-states)

| From | To | Triggering Plugin | Business Rule / Trigger Description |
|---|---|---|---|
| NEW | PROCESSING | rows-enrichment | Enrichment processing begins |
| PROCESSING | ENRICHED | rows-enrichment | Initial enrichment rules applied successfully |
| PROCESSING | MANUAL_REVIEW | rows-enrichment | Initial enrichment requires manual review |
| PROCESSING | ERROR | rows-enrichment | Processing failed; unrecoverable error |
| ENRICHED | IN_REVIEW | enrichment-api | User submits enrichment record to workspace for review |
| ENRICHED | ADJUSTED | enrichment-api | User adjusts enrichment data (split/merge/custom fields) |
| ENRICHED | READY | enrichment-api | Enrichment complete; marked ready for confirmation |
| ENRICHED | PAIRED | enrichment-api | Enrichment paired with bank transaction |
| ENRICHED | MANUAL_REVIEW | enrichment-api | User flags for manual review in workspace |
| ENRICHED | SUPERSEDED | enrichment-api | Enrichment replaced by newer version (split/merge creates new record) |
| IN_REVIEW | ADJUSTED | enrichment-api | User adjusts enrichment while in review |
| IN_REVIEW | READY | enrichment-api | User approves enrichment from review state |
| IN_REVIEW | ENRICHED | enrichment-api | User reverts to previous enriched state |
| IN_REVIEW | SUPERSEDED | enrichment-api | Record replaced while in review (split/merge creates new record) |
| ADJUSTED | READY | enrichment-api | User confirms adjustments; ready for approval |
| ADJUSTED | IN_REVIEW | enrichment-api | User re-submits adjusted record to review |
| ADJUSTED | ENRICHED | enrichment-api | User discards adjustments; reverts to previous state |
| ADJUSTED | SUPERSEDED | enrichment-api | Record replaced after adjustment (split/merge creates new record) |
| READY | CONFIRMED | enrichment-api | Workflow approves enrichment; locks for posting |
| READY | ENRICHED | enrichment-api | User reverts from ready state |
| READY | IN_REVIEW | enrichment-api | User re-submits ready record to review |
| READY | SUPERSEDED | enrichment-api | Record replaced before confirmation (split/merge creates new record) |
| PAIRED | READY | enrichment-api | Paired transaction marked ready for confirmation |
| PAIRED | MANUAL_REVIEW | enrichment-api | User flags paired transaction for additional review |
| CONFIRMED | (none) | (none) | Terminal state; no further transitions |
| SUPERSEDED | (none) | (none) | Terminal state; replaced by new enrichment record |
| ERROR | NEW | enrichment-api or admin | Manual reset; enrichment re-processed from beginning |
| ERROR | MANUAL_REVIEW | enrichment-api | Error resolved; enrichment moved to manual review queue |
| MANUAL_REVIEW | NEW | enrichment-workspace | User discards; restart from NEW |
| MANUAL_REVIEW | ENRICHED | enrichment-workspace | User confirms; transition to ENRICHED |
| MANUAL_REVIEW | READY | enrichment-workspace | User confirms ready; transition to READY |

#### 5.4.3 Lifecycle Overview

The ENRICHMENT entity type is the most complex because it represents the enrichment workspace where users review, adjust, and approve enrichment metadata before posting. Key characteristics:

1. **Creation:** Automatically created by rows-enrichment when processing a BANK_TRX or SECU_TRX
2. **Initial State:** Enters as NEW
3. **Processing Phase:** Transitions to ENRICHED after rows-enrichment completes initial enrichment
4. **Workspace Transitions:** In ENRICHED state, users can:
   - Submit to IN_REVIEW for user review
   - Transition to ADJUSTED for data modifications
   - Transition to READY for approval-ready state
   - Transition to PAIRED if matched to counterparty transaction
5. **Review Loop:** IN_REVIEW ↔ ADJUSTED ↔ ENRICHED provides iterative refinement
6. **Terminal States:**
   - **CONFIRMED:** Enrichment locked; ready for posting. No further changes allowed.
   - **SUPERSEDED:** Enrichment replaced by new record (e.g., via split/merge operation). Old record archived.
7. **Error Handling:**
   - **ERROR → NEW:** Restart enrichment from beginning
   - **ERROR → MANUAL_REVIEW:** Error resolved; requires human judgment
8. **Manual Review Queue:** MANUAL_REVIEW state can revert to NEW (discard), transition to ENRICHED (accept), or to READY (expedited approval)

---

### 5.5 PAIR Entity Type

**Entity Type Code:** `PAIR`
**Table Name:** `trx_pair`
**Initial Statuses:** `AUTO_ACCEPTED`, `PENDING_REVIEW`
**Terminal Statuses:** `CONFIRMED`, `REJECTED`

#### 5.5.1 State Diagram

```
Create pair record (manual or auto-match)
         │
         ├─→ Auto-matched pair confidence ≥ threshold
         │   │
         │   ▼
         │ ┌──────────────┐
         │ │AUTO_ACCEPTED │
         │ │(terminal)    │
         │ └──────────────┘
         │
         └─→ Manual pair or low confidence auto-match
             │
             ▼
        ┌────────────────┐
        │PENDING_REVIEW  │
        │ (User review)  │
        └────┬────────┬──┘
             │        │
         confirm   reject
             │        │
             ▼        ▼
        ┌────────┐ ┌────────┐
        │CONFIRM │ │REJECTED│
        │ED      │ │        │
        │(term)  │ │(term)  │
        └────────┘ └────────┘
```

#### 5.5.2 PAIR Transition Table

| From | To | Triggering Plugin | Business Rule / Trigger Description |
|---|---|---|---|
| AUTO_ACCEPTED | (none) | (none) | Terminal state; auto-matched pair above confidence threshold; no manual review needed |
| PENDING_REVIEW | CONFIRMED | enrichment-api | User confirms pair is correct; locks pairing |
| PENDING_REVIEW | REJECTED | enrichment-api | User rejects pair; marks as incorrect match; unlinks transactions |
| CONFIRMED | (none) | (none) | Terminal state; pairing locked; both linked transactions marked POSTED |
| REJECTED | (none) | (none) | Terminal state; pairing rejected; transactions revert to ENRICHED or UNMATCHED state |

#### 5.5.3 Lifecycle Overview

1. **PAIR created in two scenarios:**
   - **AUTO_ACCEPTED:** enrichment-api auto-matches BANK_TRX to SECU_TRX with confidence ≥ threshold (no further action required)
   - **PENDING_REVIEW:** Manual pair created by user or confidence < threshold (requires user confirmation)

2. **From PENDING_REVIEW:**
   - **CONFIRMED** if user approves the pairing → both linked transactions transition to POSTED
   - **REJECTED** if user disapproves → unlinks transactions; they revert to previous states

3. **Terminal States:**
   - **AUTO_ACCEPTED:** Final; pair valid without review
   - **CONFIRMED:** Final; user-approved pair valid
   - **REJECTED:** Final; pairing invalid; transactions unlinked and re-queued

---

### 5.6 EXCEPTION Entity Type

**Entity Type Code:** `EXCEPTION`
**Table Name:** `exception_queue`
**Initial Statuses:** `OPEN`
**Terminal Statuses:** `RESOLVED`, `DISMISSED`

#### 5.6.1 State Diagram

```
Create exception record
         │
         ▼
    ┌──────────┐
    │   OPEN   │
    │(Initial) │
    └────┬──┬──┘
         │  │
    assign progress
         │  │
         ▼  ▼
    ┌──────────────┐
    │IN_PROGRESS   │
    │(assignment)  │
    └────┬──────┬──┘
         │      │
     resolve dismiss
         │      │
         ▼      ▼
    ┌────────┐ ┌────────┐
    │RESOLVED│ │DISMISSED
    │(term)  │ │(term)
    └────────┘ └────────┘

Alternative path from OPEN:
    OPEN → DISMISSED (without action)
```

#### 5.6.2 EXCEPTION Transition Table

| From | To | Triggering Plugin | Business Rule / Trigger Description |
|---|---|---|---|
| OPEN | IN_PROGRESS | enrichment-api or admin | Exception assigned to user for investigation |
| OPEN | DISMISSED | enrichment-api or admin | Exception reviewed and dismissed (not requiring action) |
| IN_PROGRESS | RESOLVED | enrichment-api or admin | Exception investigated and corrected; issue resolved |
| IN_PROGRESS | DISMISSED | enrichment-api or admin | Exception investigated; determined to not require action |
| RESOLVED | (none) | (none) | Terminal state; exception resolved and archived |
| DISMISSED | (none) | (none) | Terminal state; exception not actionable; archived |

#### 5.6.3 Lifecycle Overview

1. **EXCEPTION created in OPEN state** when rows-enrichment or enrichment-api detects exception condition
2. **From OPEN:**
   - **IN_PROGRESS** when admin assigns exception to user for investigation
   - **DISMISSED** if reviewed and determined not to require action
3. **From IN_PROGRESS:**
   - **RESOLVED** when root cause fixed and exception cleared
   - **DISMISSED** when determined not actionable
4. **Terminal States:**
   - **RESOLVED:** Exception root cause fixed; archived
   - **DISMISSED:** Exception reviewed; not actionable; archived

---

### 5.7 POSTING_OPERATION Entity Type

**Entity Type Code:** `POSTING_OPERATION`
**Table Name:** `posting_operation`
**Initial Statuses:** `PENDING`
**Terminal Statuses:** `POSTED`, `REVOKED`, `ERROR` (with no recovery path)

#### 5.7.1 State Diagram

```
Create posting batch
         │
         ▼
    ┌──────────┐
    │ PENDING  │
    │(Initial) │
    └────┬───┬┘
         │   │
    submit revoke
         │   │
         ▼   ▼
    ┌────────┐ ┌────────┐
    │POSTING │ │REVOKED │
    │(in-flt)│ │(term)  │
    └───┬──┬─┘ └────────┘
        │  │
    succe err
        │  │
        ▼  ▼
    ┌──────┐ ┌──────────┐
    │POSTED│ │  ERROR   │
    │(term)│ │(customa.)│
    └──────┘ └──┬────┬──┘
              retry revoke
               │    │
               ▼    ▼
            PENDING REVOKED
```

#### 5.7.2 POSTING_OPERATION Transition Table

| From | To | Triggering Plugin | Business Rule / Trigger Description |
|---|---|---|---|
| PENDING | POSTING | gl-preparator | GL posting batch submitted to GL system for posting |
| PENDING | REVOKED | gl-preparator or admin | Posting batch revoked before submission |
| POSTING | POSTED | gl-preparator | GL posting completed successfully; batch recorded in GL |
| POSTING | ERROR | gl-preparator | GL posting failed (GL system error, validation error, network error) |
| ERROR | PENDING | gl-preparator or admin | Posting retry initiated; batch reverted to PENDING for re-submission |
| ERROR | REVOKED | admin | Posting abandoned; batch revoked due to unrecoverable error |
| POSTED | (none) | (none) | Terminal state; posting complete and archived |
| REVOKED | (none) | (none) | Terminal state; posting revoked and archived |

#### 5.7.3 Lifecycle Overview

1. **POSTING_OPERATION created in PENDING state** by gl-preparator when ready to submit to GL
2. **From PENDING:**
   - **POSTING** when batch submitted to GL system
   - **REVOKED** if admin/user withdraws posting before submission
3. **From POSTING:**
   - **POSTED** on successful GL posting
   - **ERROR** if GL posting fails
4. **From ERROR:**
   - **PENDING** for retry (admin initiates re-submission)
   - **REVOKED** if unrecoverable error; batch abandoned
5. **Terminal States:**
   - **POSTED:** Batch successfully posted to GL; archived
   - **REVOKED:** Batch posting withdrawn; archived

---

## 6. StatusManager API

### 6.1 Overview

`StatusManager` is the **single point of entry** for all status transitions in the system. It provides:

- Type-safe transition methods with full validation
- Automatic audit logging
- Immutable audit trail persistence
- Exception-based error handling for invalid transitions
- Spring context integration for FormDataDao access

### 6.2 Method Signatures and Behavior

#### 6.2.1 Standard Transition Method

```java
public static void transition(
    FormDataDao dao,
    EntityType entityType,
    String recordId,
    Status targetStatus,
    String triggeredBy,
    String reason
) throws InvalidTransitionException
```

**Purpose:** Execute a status transition with automatic table name resolution from EntityType enum.

**Parameters:**
- `dao` (FormDataDao): Database access object from Joget context
- `entityType` (EntityType): Type of entity being transitioned (STATEMENT, BANK_TRX, SECU_TRX, ENRICHMENT, PAIR, EXCEPTION, POSTING_OPERATION)
- `recordId` (String): Unique identifier of the record being transitioned (form data ID)
- `targetStatus` (Status): Target status (must be valid transition from current status)
- `triggeredBy` (String): Name/identifier of plugin triggering the transition (e.g., "statement-importer", "rows-enrichment", "enrichment-api")
- `reason` (String): Human-readable reason for transition (e.g., "Import completed successfully", "User confirmation from enrichment workspace")

**Returns:** `void`

**Throws:**
- `InvalidTransitionException` (checked) if current status cannot transition to targetStatus (business rule violation)
- `IllegalStateException` (unchecked) if record not found or unrecognized status code in database (infrastructure/data corruption error)

**Example:**

```java
try {
    StatusManager.transition(
        dao,
        EntityType.STATEMENT,
        "statement-2026-03-03-001",
        Status.IMPORTING,
        "statement-importer",
        "Bank statement import initiated for file ABC123.csv"
    );
} catch (InvalidTransitionException e) {
    logger.error("Transition failed: {}", e.getMessage());
    // Handle error: log to exception queue, notify admin, etc.
}
```

#### 6.2.2 Custom Table Name Transition Method (ENRICHMENT Only)

```java
public static void transition(
    FormDataDao dao,
    String tableName,
    EntityType entityType,
    String recordId,
    Status targetStatus,
    String triggeredBy,
    String reason
) throws InvalidTransitionException
```

**Purpose:** Execute a status transition with an explicit table name. Use when the actual Joget form table name differs from `EntityType.getTableName()`.

**Parameters:**
- `dao` (FormDataDao): Database access object
- `tableName` (String): Explicit Joget form table name to use for DB operations
- `entityType` (EntityType): Type of entity (for transition-map lookup)
- `recordId` (String): Record identifier
- `targetStatus` (Status): Target status
- `triggeredBy` (String): Plugin identifier
- `reason` (String): Transition reason

**Returns:** `void`

**Throws:**
- `InvalidTransitionException` (checked) if current status cannot transition to targetStatus (business rule violation)
- `IllegalStateException` (unchecked) if record not found or unrecognized status code in database (infrastructure/data corruption error)

**Example:**

```java
try {
    StatusManager.transition(
        dao,
        "customTableName",  // explicit table name for DB operations
        EntityType.ENRICHMENT,
        "enrich-txn-2026-03-03-042",
        Status.PROCESSING,
        "rows-enrichment",
        "Beginning automatic enrichment for bank transaction TXN-001"
    );
} catch (InvalidTransitionException e) {
    logger.error("Enrichment transition failed: {} -> {}: {}",
        currentStatus, Status.PROCESSING, e.getMessage());
}
```

**Why Two Overloads?**

The custom table name overload is a general-purpose escape hatch for cases where the actual Joget form table name differs from the `EntityType.getTableName()` mapping. The transition map still uses the `entityType` parameter for validation; only DB I/O uses the provided `tableName`.

#### 6.2.3 Validation Method: canTransition()

```java
public static boolean canTransition(
    EntityType entityType,
    Status currentStatus,
    Status targetStatus
)
```

**Purpose:** Pure validation method that checks if a transition is allowed without accessing the database.

**Parameters:**
- `entityType` (EntityType): Type of entity
- `currentStatus` (Status): Current status (use null to check initial status)
- `targetStatus` (Status): Proposed target status

**Returns:** `boolean`
- `true` if transition is valid and allowed
- `false` if transition is invalid

**Throws:** None (no exceptions thrown)

**Note:** This method does NOT access the database; it only checks the static transition map. Use this for **validation before attempting transition**.

**Example:**

```java
if (StatusManager.canTransition(EntityType.BANK_TRX, currentStatus, Status.POSTING_READY)) {
    // Safe to proceed with transition
    StatusManager.transition(dao, EntityType.BANK_TRX, recordId, Status.POSTING_READY, ...);
} else {
    // Transition not allowed
    logger.warn("Cannot transition {} from {} to {}",
        recordId, currentStatus, Status.POSTING_READY);
}
```

#### 6.2.4 Get Valid Transitions: getValidTransitions()

```java
public static Set<Status> getValidTransitions(
    EntityType entityType,
    Status currentStatus
)
```

**Purpose:** Get all valid target statuses from a given current status.

**Parameters:**
- `entityType` (EntityType): Type of entity
- `currentStatus` (Status): Current status

**Returns:** `Set<Status>` containing all statuses reachable from currentStatus
- **Empty set** if currentStatus is terminal or invalid for entityType
- **Non-empty set** if valid transitions exist

**Throws:** None

**Example:**

```java
Set<Status> validNextStates = StatusManager.getValidTransitions(
    EntityType.ENRICHMENT, Status.ENRICHED
);

// Returns: {IN_REVIEW, ADJUSTED, READY, PAIRED, MANUAL_REVIEW, SUPERSEDED}

if (validNextStates.contains(Status.READY)) {
    // User can mark as ready
}
```

#### 6.2.5 Get FormDataDao: getFormDataDao()

```java
public static FormDataDao getFormDataDao()
```

**Purpose:** Convenience method to retrieve FormDataDao from Spring application context.

**Returns:** `FormDataDao` instance from current Joget context

**Throws:** `RuntimeException` if Spring context not available or FormDataDao not registered

**Use Case:** For plugin code that doesn't have direct access to FormDataDao.

**Example:**

```java
FormDataDao dao = StatusManager.getFormDataDao();
StatusManager.transition(dao, EntityType.BANK_TRX, recordId, Status.ENRICHED, ...);
```

#### 6.2.6 Check Initial Status: isInitialStatus()

```java
public static boolean isInitialStatus(
    EntityType entityType,
    Status status
)
```

**Purpose:** Determine if a status is a valid initial status for an entity type (i.e., what status should a newly-created record have).

**Parameters:**
- `entityType` (EntityType): Type of entity
- `status` (Status): Status to check

**Returns:** `boolean`
- `true` if status is a valid initial status for entityType
- `false` otherwise

**Initial Status Map:**
| EntityType | Initial Status(es) |
|---|---|
| STATEMENT | NEW |
| BANK_TRX | NEW |
| SECU_TRX | NEW |
| ENRICHMENT | NEW |
| PAIR | AUTO_ACCEPTED, PENDING_REVIEW |
| EXCEPTION | OPEN |
| POSTING_OPERATION | PENDING |

**Example:**

```java
if (!StatusManager.isInitialStatus(EntityType.PAIR, currentStatus)) {
    // Pair record should begin in AUTO_ACCEPTED or PENDING_REVIEW
    logger.warn("Pair record {} has unexpected initial status: {}",
        recordId, currentStatus);
}
```

### 6.3 Error Handling: InvalidTransitionException

```java
public class InvalidTransitionException extends Exception {
    private EntityType entityType;
    private String recordId;
    private Status fromStatus;
    private Status toStatus;

    // Constructors and getters...
}
```

**Purpose:** Checked exception thrown when a status transition is not allowed.

**Fields:**
- `entityType` (EntityType): Type of entity attempted to transition
- `recordId` (String): ID of record that failed to transition
- `fromStatus` (Status): Current status
- `toStatus` (Status): Attempted target status

**When Thrown:**
- Calling `transition()` with an invalid state transition (business rule violation)

**Note:** Infrastructure errors (record not found, unrecognized status code in database) throw `IllegalStateException` (unchecked) instead. This separates business rule violations (checked, recoverable) from infrastructure/data corruption errors (unchecked, typically fatal).

**Handling Strategy:**

```java
try {
    StatusManager.transition(dao, entityType, recordId, targetStatus, triggeredBy, reason);
} catch (InvalidTransitionException e) {
    // Log the error
    logger.error("Failed to transition {} record {} from {} to {}. Reason: {}",
        e.getEntityType(), e.getRecordId(), e.getFromStatus(), e.getToStatus(),
        e.getMessage());

    // Create exception queue entry
    createException(
        recordId,
        "Invalid status transition: " + e.getFromStatus() + " → " + e.getToStatus(),
        "The system attempted an invalid state transition. Please review the enrichment rules."
    );

    // Notify admin
    notifyAdministrators("Status transition failure for " + recordId);
}
```

### 6.4 Automatic Behavior

All `transition()` calls automatically:

1. **Validate the transition** against the static transition map
2. **Fetch current status** from database (if not provided)
3. **Perform the transition** by updating the status field in the appropriate form table
4. **Create an audit entry** in the `audit_log` table (see section 7)
5. **Return successfully** or throw `InvalidTransitionException` on failure

---

## 7. Audit Logging

### 7.1 Audit Mechanism

Every status transition is automatically recorded in the `audit_log` table via the `TransitionAuditEntry` immutable DTO.

### 7.2 TransitionAuditEntry DTO

```java
public final class TransitionAuditEntry {
    private final EntityType entityType;
    private final String entityId;
    private final Status fromStatus;
    private final Status toStatus;
    private final String triggeredBy;
    private final String reason;
    private final String timestamp;  // ISO 8601 format

    // Immutable: no setters
    // Constructor: TransitionAuditEntry(EntityType, String, Status, Status, String, String, String)

    // Conversion method for persistence
    public FormRow toFormRow() { ... }
}
```

**Fields:**
- `entityType` (EntityType): Type of entity transitioned (e.g., BANK_TRX)
- `entityId` (String): ID of transitioned record
- `fromStatus` (Status): Status before transition
- `toStatus` (Status): Status after transition
- `triggeredBy` (String): Plugin/user that triggered the transition
- `reason` (String): Human-readable reason for transition
- `timestamp` (String): ISO 8601 formatted timestamp of transition (e.g., `2026-03-03T14:32:17Z`)

**Immutability:** `TransitionAuditEntry` is immutable (all fields private final, no setters). Once created, it cannot be modified.

### 7.3 Audit Log Table Schema

**Table Name:** `audit_log`

| Column Name | Data Type | Nullable | Purpose |
|---|---|---|---|
| audit_id | BIGINT | NO | Primary key (auto-increment) |
| entity_type | VARCHAR(50) | NO | EntityType enum name (e.g., "STATEMENT", "BANK_TRX") |
| entity_id | VARCHAR(255) | NO | ID of transitioned record |
| from_status | VARCHAR(50) | NO | Previous status code |
| to_status | VARCHAR(50) | NO | New status code |
| triggered_by | VARCHAR(255) | NO | Plugin/user identifier |
| reason | TEXT | YES | Reason for transition |
| timestamp | TIMESTAMP | NO | ISO 8601 timestamp; default CURRENT_TIMESTAMP |

**Indexes:**
- Primary key on `audit_id`
- Composite index on `(entity_type, entity_id, timestamp)` for efficient querying by record and date range
- Index on `timestamp` for timeline queries

### 7.4 Automatic Audit Behavior

**When:** Every successful `StatusManager.transition()` call

**What:** A new row is inserted into `audit_log` with:
- entityType from the transition parameters
- entityId (recordId) from the transition parameters
- fromStatus (fetched from database or provided)
- toStatus from the transition parameters
- triggeredBy from the transition parameters
- reason from the transition parameters
- timestamp automatically set to current UTC time

**No Manual Audit Calls Required:** Consuming plugins do NOT need to manually create audit entries. The framework handles this automatically.

**Audit Trail Immutability:** Once an audit entry is created, it cannot be deleted or modified (application-level constraint; enforce at database level via audit table permissions).

### 7.5 Audit Trail Queries

Common audit trail queries for reporting:

```sql
-- Get all transitions for a specific record
SELECT * FROM audit_log
WHERE entity_type = 'BANK_TRX' AND entity_id = 'TXN-2026-03-03-001'
ORDER BY timestamp DESC;

-- Get transitions by date range
SELECT * FROM audit_log
WHERE entity_type = 'STATEMENT'
  AND timestamp >= '2026-03-01' AND timestamp < '2026-03-02'
ORDER BY timestamp DESC;

-- Get transitions triggered by a specific plugin
SELECT * FROM audit_log
WHERE triggered_by = 'rows-enrichment'
ORDER BY timestamp DESC
LIMIT 100;

-- Get transitions to a specific status
SELECT * FROM audit_log
WHERE to_status = 'manual_review'
ORDER BY timestamp DESC;

-- Count transitions by from/to status
SELECT from_status, to_status, COUNT(*) as count
FROM audit_log
WHERE entity_type = 'ENRICHMENT'
GROUP BY from_status, to_status;
```

---

## 8. Integration Rules

### 8.1 Golden Rules for Consuming Plugins

Adhering to these rules ensures safe, consistent status management across all plugins:

#### Rule 1: Never Write Status Directly

**WRONG:**
```java
// DANGEROUS! Bypasses validation and audit logging
FormRow row = dao.loadData("bank_total_trx", recordId);
row.setProperty("status", "enriched");
dao.saveData("bank_total_trx", row);
```

**RIGHT:**
```java
try {
    StatusManager.transition(
        dao,
        EntityType.BANK_TRX,
        recordId,
        Status.ENRICHED,
        "rows-enrichment",
        "Transaction enrichment completed"
    );
} catch (InvalidTransitionException e) {
    // Handle error appropriately
}
```

#### Rule 2: Never Use String Literals for Statuses

**WRONG:**
```java
// FRAGILE! String literals are error-prone
String statusString = "processing";
// ... later ...
if (statusString.equals("enriched")) { ... }
```

**RIGHT:**
```java
// Type-safe enum usage
Status currentStatus = Status.valueOf(row.getProperty("status").toUpperCase());
if (currentStatus == Status.ENRICHED) { ... }
if (StatusManager.canTransition(entityType, currentStatus, Status.READY)) { ... }
```

#### Rule 3: Use Custom Table Name Overload Only When Needed

The custom table name overload exists for cases where the actual Joget form table name differs from `EntityType.getTableName()`. For all current entity types, the standard 6-arg overload works correctly:

```java
// Standard overload — works for all entity types including ENRICHMENT
StatusManager.transition(
    dao,
    EntityType.ENRICHMENT,
    recordId,
    Status.PROCESSING,
    "rows-enrichment",
    "Starting enrichment"
);
```

#### Rule 4: Handle InvalidTransitionException

**WRONG:**
```java
// Ignoring exceptions leads to silent failures
try {
    StatusManager.transition(...);
} catch (InvalidTransitionException e) {
    // WRONG: Suppress and continue
}
```

**RIGHT:**
```java
try {
    StatusManager.transition(
        dao,
        entityType,
        recordId,
        targetStatus,
        "plugin-name",
        "Transition reason"
    );
} catch (InvalidTransitionException e) {
    logger.error("Transition failed: {} from {} to {}: {}",
        recordId, e.getFromStatus(), e.getToStatus(), e.getMessage());

    // Take corrective action:
    // - Log to exception queue
    // - Notify user/admin
    // - Trigger compensating workflow
    createException(recordId, "Status transition failed", e.getMessage());
}
```

#### Rule 5: Audit is Automatic; No Manual Logging Required

**WRONG:**
```java
// Manual audit entry creation is unnecessary and redundant
StatusManager.transition(dao, entityType, recordId, targetStatus, triggeredBy, reason);
manuallyCreateAuditEntry(recordId, oldStatus, targetStatus); // REDUNDANT!
```

**RIGHT:**
```java
// StatusManager handles audit automatically
StatusManager.transition(dao, entityType, recordId, targetStatus, triggeredBy, reason);
// No manual audit logging needed
```

### 8.2 Plugin Integration Checklist

When integrating a plugin with the GAM Framework, verify:

- [ ] All status transitions go through `StatusManager.transition()` (never direct writes)
- [ ] All Status references use the `Status` enum (never string literals)
- [ ] All `transition()` calls are wrapped in try-catch for `InvalidTransitionException`
- [ ] `triggeredBy` parameter clearly identifies the plugin (e.g., "statement-importer")
- [ ] `reason` parameter provides business context for audit trail
- [ ] No manual audit logging code; audit is automatic
- [ ] Plugin validates using `canTransition()` before attempting transition
- [ ] Plugin uses `getValidTransitions()` if offering user UI choices of next states
- [ ] Plugin handles `InvalidTransitionException` appropriately (log, create exception entry, notify admin)
- [ ] Plugin respects terminal states (CONFIRMED, SUPERSEDED, POSTED, RESOLVED, DISMISSED, etc.)

---

## 9. Cross-Plugin Transition Responsibilities

### 9.1 Transition Responsibility Matrix

This matrix shows which plugin is responsible for triggering each transition. Understanding this ensures plugins work cohesively without stepping on each other's toes.

| EntityType | From → To Transition | Responsible Plugin | Plugin Purpose |
|---|---|---|---|
| **STATEMENT** | NEW → IMPORTING | statement-importer | Initiate bank statement import |
| STATEMENT | IMPORTING → IMPORTED | statement-importer | Complete import parsing |
| STATEMENT | IMPORTING → ERROR | statement-importer | Handle import failure |
| STATEMENT | IMPORTED → CONSOLIDATING | statement-importer | Start consolidation logic |
| STATEMENT | CONSOLIDATING → CONSOLIDATED | statement-importer | Complete consolidation (match totals, detect dupes, etc.) |
| STATEMENT | CONSOLIDATING → ERROR | statement-importer | Handle consolidation failure |
| STATEMENT | CONSOLIDATED → ENRICHED | rows-enrichment | All child TRXs enriched |
| STATEMENT | CONSOLIDATED → ERROR | rows-enrichment | Critical enrichment failure |
| STATEMENT | ENRICHED → POSTED | gl-preparator | GL posting complete |
| STATEMENT | ERROR → NEW | admin/statement-importer | Manual reset for retry |
| **BANK_TRX** | NEW → PROCESSING | rows-enrichment | Begin enrichment pipeline |
| BANK_TRX | PROCESSING → ENRICHED | rows-enrichment | Enrichment rules applied successfully |
| BANK_TRX | PROCESSING → MANUAL_REVIEW | rows-enrichment | Enrichment requires human judgment |
| BANK_TRX | PROCESSING → ERROR | rows-enrichment | Enrichment processing failed |
| BANK_TRX | ENRICHED → PAIRED | enrichment-api | Transaction paired with SECU_TRX |
| BANK_TRX | ENRICHED → POSTING_READY | enrichment-api | Ready for posting without pairing |
| BANK_TRX | ENRICHED → MANUAL_REVIEW | enrichment-api | User flags in workspace for review |
| BANK_TRX | POSTING_READY → POSTED | gl-preparator | GL posting completed |
| BANK_TRX | PAIRED → POSTED | gl-preparator | GL posting completed for pair |
| BANK_TRX | MANUAL_REVIEW → NEW | enrichment-workspace | User discards changes |
| BANK_TRX | MANUAL_REVIEW → ENRICHED | enrichment-workspace | User confirms changes |
| BANK_TRX | MANUAL_REVIEW → POSTING_READY | enrichment-workspace | User confirms ready without pairing |
| BANK_TRX | ERROR → NEW | admin/statement-importer | Manual reset |
| **SECU_TRX** | NEW → PROCESSING | rows-enrichment | Begin enrichment pipeline |
| SECU_TRX | PROCESSING → ENRICHED | rows-enrichment | Enrichment rules applied |
| SECU_TRX | PROCESSING → MANUAL_REVIEW | rows-enrichment | Enrichment requires human judgment |
| SECU_TRX | PROCESSING → ERROR | rows-enrichment | Enrichment failed |
| SECU_TRX | ENRICHED → PAIRED | enrichment-api | Paired with BANK_TRX |
| SECU_TRX | ENRICHED → UNMATCHED | enrichment-api | No matching BANK_TRX found |
| SECU_TRX | ENRICHED → MANUAL_REVIEW | enrichment-api | User flags in workspace |
| SECU_TRX | UNMATCHED → PAIRED | enrichment-api | User created manual pairing |
| SECU_TRX | UNMATCHED → MANUAL_REVIEW | enrichment-api | User flags for review |
| SECU_TRX | PAIRED → POSTED | gl-preparator | GL posting completed |
| SECU_TRX | MANUAL_REVIEW → NEW | enrichment-workspace | User discards changes |
| SECU_TRX | MANUAL_REVIEW → ENRICHED | enrichment-workspace | User confirms changes |
| SECU_TRX | MANUAL_REVIEW → PAIRED | enrichment-workspace | User manually paired |
| SECU_TRX | ERROR → NEW | admin/statement-importer | Manual reset |
| **ENRICHMENT** | NEW → PROCESSING | rows-enrichment | Start enrichment |
| ENRICHMENT | PROCESSING → ENRICHED | rows-enrichment | Enrichment complete |
| ENRICHMENT | PROCESSING → MANUAL_REVIEW | rows-enrichment | Enrichment requires review |
| ENRICHMENT | PROCESSING → ERROR | rows-enrichment | Processing failed |
| ENRICHMENT | ENRICHED → IN_REVIEW | enrichment-api | User submits to workspace |
| ENRICHMENT | ENRICHED → ADJUSTED | enrichment-api | User adjusts data |
| ENRICHMENT | ENRICHED → READY | enrichment-api | User marks ready |
| ENRICHMENT | ENRICHED → PAIRED | enrichment-api | User paired transactions |
| ENRICHMENT | ENRICHED → MANUAL_REVIEW | enrichment-api | User flags for review |
| ENRICHMENT | ENRICHED → SUPERSEDED | enrichment-api | Record replaced (split/merge) |
| ENRICHMENT | IN_REVIEW → ADJUSTED | enrichment-api | User adjusts from review |
| ENRICHMENT | IN_REVIEW → READY | enrichment-api | User approves from review |
| ENRICHMENT | IN_REVIEW → ENRICHED | enrichment-api | User reverts to previous |
| ENRICHMENT | IN_REVIEW → SUPERSEDED | enrichment-api | Record replaced while in review |
| ENRICHMENT | ADJUSTED → READY | enrichment-api | User confirms adjustments |
| ENRICHMENT | ADJUSTED → IN_REVIEW | enrichment-api | User re-submits |
| ENRICHMENT | ADJUSTED → ENRICHED | enrichment-api | User discards adjustments |
| ENRICHMENT | ADJUSTED → SUPERSEDED | enrichment-api | Record replaced after adjustment |
| ENRICHMENT | READY → CONFIRMED | enrichment-api | Workflow approves |
| ENRICHMENT | READY → ENRICHED | enrichment-api | User reverts |
| ENRICHMENT | READY → IN_REVIEW | enrichment-api | User re-submits |
| ENRICHMENT | READY → SUPERSEDED | enrichment-api | Record replaced before confirmation |
| ENRICHMENT | PAIRED → READY | enrichment-api | Pair ready for posting |
| ENRICHMENT | PAIRED → MANUAL_REVIEW | enrichment-api | Pair flagged |
| ENRICHMENT | ERROR → NEW | enrichment-api/admin | Reset for retry |
| ENRICHMENT | ERROR → MANUAL_REVIEW | enrichment-api | Error resolved; review needed |
| ENRICHMENT | MANUAL_REVIEW → NEW | enrichment-workspace | User discards |
| ENRICHMENT | MANUAL_REVIEW → ENRICHED | enrichment-workspace | User confirms |
| ENRICHMENT | MANUAL_REVIEW → READY | enrichment-workspace | User confirms ready |
| **PAIR** | AUTO_ACCEPTED | enrichment-api | Auto-matched; confidence ≥ threshold |
| PAIR | PENDING_REVIEW → CONFIRMED | enrichment-api | User confirms pair |
| PAIR | PENDING_REVIEW → REJECTED | enrichment-api | User rejects pair |
| **EXCEPTION** | OPEN → IN_PROGRESS | enrichment-api/admin | Assign for investigation |
| EXCEPTION | OPEN → DISMISSED | enrichment-api/admin | Reviewed; not actionable |
| EXCEPTION | IN_PROGRESS → RESOLVED | enrichment-api/admin | Issue fixed |
| EXCEPTION | IN_PROGRESS → DISMISSED | enrichment-api/admin | Not actionable |
| **POSTING_OPERATION** | PENDING → POSTING | gl-preparator | Submit to GL |
| POSTING_OPERATION | PENDING → REVOKED | gl-preparator/admin | Revoke before submission |
| POSTING_OPERATION | POSTING → POSTED | gl-preparator | GL posting succeeded |
| POSTING_OPERATION | POSTING → ERROR | gl-preparator | GL posting failed |
| POSTING_OPERATION | ERROR → PENDING | gl-preparator/admin | Retry |
| POSTING_OPERATION | ERROR → REVOKED | admin | Abandon posting |

### 9.2 Plugin Collaboration Pattern

```
┌──────────────────┐         ┌──────────────────┐
│ statement-       │         │ rows-enrichment  │
│ importer         │         │                  │
│                  │         │                  │
│ • STATEMENT      │         │ • BANK_TRX       │
│   NEW→IMPORTING  │         │   NEW→PROCESSING │
│   IMPORTING→     │         │   PROCESSING→    │
│   IMPORTED       │         │   ENRICHED/      │
│   IMPORTED→      │         │   MANUAL_REVIEW  │
│   CONSOLIDATING  │         │ • SECU_TRX       │
│   CONSOL→        │         │   (same)         │
│   CONSOLD        │         │ • ENRICHMENT     │
│                  │         │   (same)         │
│ • Creates BANK   │────────→│ • STATEMENT      │
│   _TRX, SECU_TRX │ Records │   CONSOLD→       │
│   records        │         │   ENRICHED       │
└──────────────────┘         └──────────────────┘
                                     │
                                     ▼
                            ┌──────────────────┐
                            │ enrichment-api   │
                            │                  │
                            │ • BANK_TRX       │
                            │   ENRICHED→      │
                            │   PAIRED/        │
                            │   POSTING_READY  │
                            │ • SECU_TRX       │
                            │   (complex state)│
                            │ • ENRICHMENT     │
                            │   (workspace ops)│
                            │ • PAIR           │
                            │   AUTO_ACCEPTED/ │
                            │   PENDING_REVIEW │
                            │ • EXCEPTION      │
                            │   (create/update)│
                            └──────────────────┘
                                     │
                                     ▼
                            ┌──────────────────┐
                            │enrichment-       │
                            │workspace (UI)    │
                            │                  │
                            │ • User actions   │
                            │   in enrichment  │
                            │   workspace      │
                            │   delegate to    │
                            │ • enrichment-api │
                            │   for status     │
                            │   transitions    │
                            └──────────────────┘
                                     │
                                     ▼
                            ┌──────────────────┐
                            │ gl-preparator    │
                            │ (future)         │
                            │                  │
                            │ • POSTING_OPER   │
                            │   PENDING→       │
                            │   POSTING→POSTED │
                            │ • BANK_TRX       │
                            │   POSTING_READY/ │
                            │   PAIRED→POSTED  │
                            │ • SECU_TRX       │
                            │   PAIRED→POSTED  │
                            │ • STATEMENT      │
                            │   ENRICHED→      │
                            │   POSTED         │
                            └──────────────────┘
```

---

## 10. Configuration and Dependencies

### 10.1 Maven Dependency

Add to consuming plugin's `pom.xml`:

```xml
<dependency>
    <groupId>com.fiscaladmin.gam</groupId>
    <artifactId>gam-framework</artifactId>
    <version>8.1-SNAPSHOT</version>
    <scope>provided</scope>  <!-- Provided by Joget DX8 -->
</dependency>
```

**Scope:** `provided` (assumes gam-framework is deployed to Joget's plugin repository)

### 10.2 Spring Context Integration

The `StatusManager` requires access to Spring's `ApplicationContext` to retrieve `FormDataDao`. In a Joget plugin environment:

```java
// Automatically available via Joget's plugin infrastructure
FormDataDao dao = StatusManager.getFormDataDao();
```

For custom environments or unit tests, ensure `FormDataDao` is registered in Spring context:

```java
@Configuration
public class GamFrameworkConfig {

    @Bean
    public FormDataDao formDataDao() {
        // Return Joget's FormDataDao instance
        return LogUtil.getDataSource().getFormDataDao();
    }
}
```

### 10.3 No Plugin Properties

The GAM Framework is a **pure library with no plugin configuration properties**. All behavior is defined by:

- The static `Status` and `EntityType` enums (immutable)
- The static `StatusManager` transition map (immutable)
- Method parameters passed by consuming plugins

**No joget-plugin.xml configuration required** beyond the standard Maven dependency.

### 10.4 Database Requirements

Ensure the following tables exist in the Joget database:

| Table Name | Purpose | Created By |
|---|---|---|
| bank_statement | STATEMENT entity persistence | statement-importer plugin |
| bank_total_trx | BANK_TRX entity persistence | statement-importer plugin |
| secu_total_trx | SECU_TRX entity persistence | statement-importer plugin |
| trxEnrichment | ENRICHMENT entity persistence (camelCase!) | rows-enrichment plugin |
| trx_pair | PAIR entity persistence | enrichment-api plugin |
| exception_queue | EXCEPTION entity persistence | enrichment-api plugin |
| posting_operation | POSTING_OPERATION entity persistence | gl-preparator plugin |
| audit_log | Status transition audit trail | GAM Framework initialization |

**audit_log table creation script:**

```sql
CREATE TABLE audit_log (
    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    from_status VARCHAR(50) NOT NULL,
    to_status VARCHAR(50) NOT NULL,
    triggered_by VARCHAR(255) NOT NULL,
    reason TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    KEY idx_entity_timestamp (entity_type, entity_id, timestamp),
    KEY idx_timestamp (timestamp)
);
```

---

## 11. Testing Requirements

### 11.1 Test Coverage Overview

The framework contains **119 test methods** across three test classes (`StatusTest`: 39, `EntityTypeTest`: 9, `StatusManagerTest`: 71) covering:

- All 28 status values
- All 7 entity types
- All valid transitions (positive tests)
- All invalid transitions (negative tests)
- Transition validation methods
- Audit logging behavior
- Exception handling

### 11.2 Test Categories

#### 11.2.1 Status Enum Tests (4 tests)

- `testStatusCodeResolution()` - Case-insensitive code lookup
- `testStatusLabelDisplay()` - Label values
- `testStatusToString()` - String representation
- `testStatusCompleteness()` - All 28 statuses defined

#### 11.2.2 EntityType Enum Tests (3 tests)

- `testEntityTypeTableMapping()` - Table name resolution
- `testEntityTypeEnrichmentTableName()` - trxEnrichment table mapping
- `testEntityTypeCompleteness()` - All 7 types defined

#### 11.2.3 STATEMENT Transition Tests (6 tests)

- `testStatementNewToImporting()` - Initial transition
- `testStatementImportingToImported()` - Successful import
- `testStatementImportingToError()` - Import failure
- `testStatementConsolidationPath()` - IMPORTED → CONSOLIDATING → CONSOLIDATED
- `testStatementEnrichedToPosted()` - Final posting transition
- `testStatementErrorReset()` - ERROR → NEW reset

#### 11.2.4 BANK_TRX Transition Tests (8 tests)

- `testBankTrxNewToProcessing()` - Initial transition
- `testBankTrxProcessingToEnriched()` - Successful enrichment
- `testBankTrxProcessingToManualReview()` - Ambiguous enrichment
- `testBankTrxEnrichedToPaired()` - Pairing transition
- `testBankTrxEnrichedToPostingReady()` - Direct posting readiness
- `testBankTrxManualReviewPaths()` - All MANUAL_REVIEW → {NEW, ENRICHED, POSTING_READY}
- `testBankTrxPostingReadyToPosted()` - Posting completion
- `testBankTrxPairedToPosted()` - Paired posting completion

#### 11.2.5 SECU_TRX Transition Tests (7 tests)

- `testSecuTrxNewToProcessing()` - Initial transition
- `testSecuTrxProcessingToEnriched()` - Successful enrichment
- `testSecuTrxEnrichedToPaired()` - Pairing transition
- `testSecuTrxEnrichedToUnmatched()` - Unmatched securities
- `testSecuTrxUnmatchedToPaired()` - Manual pairing
- `testSecuTrxManualReviewPaths()` - All manual review transitions
- `testSecuTrxErrorReset()` - Error reset

#### 11.2.6 ENRICHMENT Transition Tests (11 tests)

- `testEnrichmentNewToProcessing()` - Initial transition
- `testEnrichmentProcessingToEnriched()` - Successful enrichment
- `testEnrichmentEnrichedToInReview()` - Workspace submission
- `testEnrichmentEnrichedToAdjusted()` - User adjustments
- `testEnrichmentEnrichedToReady()` - Ready for confirmation
- `testEnrichmentInReviewPath()` - IN_REVIEW → {ADJUSTED, READY, ENRICHED, SUPERSEDED}
- `testEnrichmentAdjustedPath()` - ADJUSTED → {READY, IN_REVIEW, ENRICHED, SUPERSEDED}
- `testEnrichmentReadyPath()` - READY → {CONFIRMED, ENRICHED, IN_REVIEW, SUPERSEDED}
- `testEnrichmentPairedTransitions()` - PAIRED → {READY, MANUAL_REVIEW}
- `testEnrichmentTerminalStates()` - CONFIRMED and SUPERSEDED (no transitions)
- `testEnrichmentComplexWorkspaceFlow()` - Full workspace review → adjust → approve flow

#### 11.2.7 PAIR Transition Tests (3 tests)

- `testPairAutoAccepted()` - Terminal auto-accepted state
- `testPairPendingReviewConfirm()` - User confirmation
- `testPairPendingReviewReject()` - User rejection

#### 11.2.8 EXCEPTION Transition Tests (3 tests)

- `testExceptionOpenToInProgress()` - Assignment
- `testExceptionInProgressToResolved()` - Resolution path
- `testExceptionOpenToDismissed()` - Direct dismissal

#### 11.2.9 POSTING_OPERATION Transition Tests (4 tests)

- `testPostingOperationPendingToPosting()` - Submission
- `testPostingOperationPostingToPosted()` - Success
- `testPostingOperationPostingToError()` - Failure
- `testPostingOperationErrorRetry()` - Retry path

#### 11.2.10 Validation Method Tests (3 tests)

- `testCanTransitionValid()` - Valid transitions return true
- `testCanTransitionInvalid()` - Invalid transitions return false
- `testGetValidTransitions()` - Set of valid next states

#### 11.2.11 Audit Logging Tests (2 tests)

- `testTransitionCreatesAuditEntry()` - Audit entry persisted
- `testAuditEntryImmutability()` - Audit entry fields immutable

### 11.3 Test Execution

Run tests with Maven:

```bash
mvn test -Dtest=StatusManagerTest
```

Expected result: All 119 tests pass

### 11.4 Code Coverage Requirements

- **Line coverage:** ≥ 95% of StatusManager.java
- **Branch coverage:** ≥ 90% (all transition paths tested)
- **Excluded:** Enum static definitions (standard practice)

### 11.5 Testing Best Practices for Consuming Plugins

When writing plugin code that uses StatusManager, follow these testing patterns:

```java
@Test
public void testEnrichmentTransition_Success() {
    // Arrange
    FormDataDao dao = mockFormDataDao();
    String recordId = "enrich-001";
    Status currentStatus = Status.ENRICHED;
    Status targetStatus = Status.READY;

    // Act & Assert (no exception)
    StatusManager.transition(
        dao,
        EntityType.ENRICHMENT,
        recordId,
        targetStatus,
        "test-plugin",
        "Testing transition"
    );

    // Verify audit entry created
    verify(dao).saveData("audit_log", any(FormRow.class));
}

@Test
public void testEnrichmentTransition_InvalidTransition() {
    // Arrange
    FormDataDao dao = mockFormDataDao();
    Status currentStatus = Status.CONFIRMED; // Terminal state
    Status targetStatus = Status.ENRICHED;   // Invalid transition

    // Act & Assert
    assertThrows(InvalidTransitionException.class, () -> {
        StatusManager.transition(
            dao,
            EntityType.ENRICHMENT,
            "enrich-001",
            targetStatus,
            "test-plugin",
            "Should fail"
        );
    });
}

@Test
public void testCanTransition_BeforeAttempting() {
    // Good practice: validate before attempting
    if (StatusManager.canTransition(EntityType.BANK_TRX, currentStatus, Status.READY)) {
        StatusManager.transition(...);
    } else {
        // Handle invalid transition case
        fail("Unexpected transition state");
    }
}
```

---

## 12. Version History and Future Roadmap

### 12.1 Current Version (8.1-SNAPSHOT)

**Release Date:** 2026-03-03
**Status:** Stable, in production with statement-importer, rows-enrichment, and enrichment-api plugins

**Key Features:**
- 28 status values across 7 entity types
- Comprehensive state machine validation
- Automatic audit logging
- Spring context integration
- InvalidTransitionException for error handling

### 12.2 Planned Enhancements (Future Versions)

- **Status Webhooks:** Plugin registration for status change events (e.g., notify admin when ENRICHMENT → CONFIRMED)
- **Transition Metadata:** Additional fields for tracking retry counts, approval chains, SLAs
- **Batch Transitions:** Efficient bulk status updates for statement-level operations
- **Status History API:** Query historical statuses beyond audit_log (state rewind capability for admin operations)
- **Event-Driven Architecture:** Integration with event streaming (Kafka, etc.) for cross-service notifications

---

## Appendix A: Complete Status Transition Map (Reference)

This appendix provides the complete transition map as implemented in `StatusManager.TRANSITION_MAP` for reference during design and troubleshooting.

### Appendix A.1: By EntityType

See sections 5.1 through 5.7 for detailed transition tables for each EntityType.

### Appendix A.2: By Status Code

**NEW:** Can transition to IMPORTING (STATEMENT), PROCESSING (BANK_TRX, SECU_TRX, ENRICHMENT)

**IMPORTING:** Can transition to IMPORTED, ERROR

**IMPORTED:** Can transition to CONSOLIDATING

**CONSOLIDATING:** Can transition to CONSOLIDATED, ERROR

**CONSOLIDATED:** Can transition to ENRICHED, ERROR

**PROCESSING:** Can transition to ENRICHED, MANUAL_REVIEW, ERROR

**ENRICHED:** Can transition to:
- BANK_TRX: PAIRED, POSTING_READY, MANUAL_REVIEW
- SECU_TRX: PAIRED, UNMATCHED, MANUAL_REVIEW
- ENRICHMENT: IN_REVIEW, ADJUSTED, READY, PAIRED, MANUAL_REVIEW, SUPERSEDED
- STATEMENT: POSTED

**POSTING_READY:** Can transition to POSTED

**PAIRED:** Can transition to POSTED (BANK_TRX, SECU_TRX), READY/MANUAL_REVIEW (ENRICHMENT)

**UNMATCHED:** Can transition to PAIRED, MANUAL_REVIEW

**MANUAL_REVIEW:** Can transition to:
- BANK_TRX: NEW, ENRICHED, POSTING_READY
- SECU_TRX: NEW, ENRICHED, PAIRED
- ENRICHMENT: NEW, ENRICHED, READY

**IN_REVIEW:** Can transition to ADJUSTED, READY, ENRICHED

**ADJUSTED:** Can transition to READY, IN_REVIEW, ENRICHED

**READY:** Can transition to CONFIRMED (PAIR), ENRICHED/IN_REVIEW (ENRICHMENT), POSTED (STATEMENT)

**CONFIRMED:** Terminal (ENRICHMENT, PAIR)

**SUPERSEDED:** Terminal

**AUTO_ACCEPTED:** Terminal

**PENDING_REVIEW:** Can transition to CONFIRMED, REJECTED

**REJECTED:** Terminal

**OPEN:** Can transition to IN_PROGRESS, DISMISSED

**IN_PROGRESS:** Can transition to RESOLVED, DISMISSED

**RESOLVED:** Terminal

**DISMISSED:** Terminal

**PENDING:** Can transition to POSTING, REVOKED

**POSTING:** Can transition to POSTED, ERROR

**POSTED:** Terminal

**REVOKED:** Terminal

**ERROR:** Can transition to NEW or (for POSTING_OPERATION) PENDING; can also transition to MANUAL_REVIEW (for ENRICHMENT)

---

## Appendix B: Glossary

**Audit Trail:** Immutable log of all status transitions stored in the `audit_log` table with full metadata (entityType, recordId, fromStatus, toStatus, triggeredBy, reason, timestamp).

**Canonical Table Name:** The table name stored in the EntityType enum (e.g., `"trxEnrichment"` for ENRICHMENT, `"bank_statement"` for STATEMENT).

**Custom Table Name Overload:** The second `transition()` method that accepts an explicit `tableName` parameter for cases where the actual Joget form table name differs from `EntityType.getTableName()`.

**Entity:** A record in one of the 7 GAM system tables (STATEMENT, BANK_TRX, SECU_TRX, ENRICHMENT, PAIR, EXCEPTION, POSTING_OPERATION).

**EntityType:** Java enum (7 values) identifying the type of entity being transitioned.

**Enrichment Workspace:** The user-facing interface where enrichment records are reviewed, adjusted, and approved before posting. Changes in the workspace are reflected as status transitions (ENRICHED ↔ IN_REVIEW ↔ ADJUSTED → READY → CONFIRMED).

**FormDataDao:** Joget's data access object for reading/writing form table data. Retrieved via `StatusManager.getFormDataDao()`.

**FormRow:** Joget's representation of a single form data record (database row).

**GL:** General Ledger; the enterprise financial reporting system. Posting operations transfer transaction data to GL for accounting purposes.

**InvalidTransitionException:** Checked exception thrown when a requested status transition is not allowed for the given EntityType and current status.

**Joget DX8 Enterprise:** The workflow and business process management platform hosting the GAM accounting system.

**Pairing:** The process of matching a BANK_TRX (cash transaction) with a SECU_TRX (security transaction) to represent a complete investment transaction.

**Posting:** The GL operation of recording transaction data in the general ledger; final step in the accounting workflow.

**Status:** Java enum (28 values) identifying the lifecycle state of an entity.

**StatusManager:** Static utility class providing the API for all status transitions and validation.

**Terminal State:** A status from which no further transitions are possible (e.g., CONFIRMED, POSTED, RESOLVED).

**TransitionAuditEntry:** Immutable DTO capturing all metadata of a single status transition for persistence to audit_log.

---

**Document Version:** 1.0
**Last Updated:** 2026-03-03
**Maintainers:** GAM Framework Development Team
**Status:** Production Specification
