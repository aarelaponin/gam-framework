# GAM Framework

Shared framework module for GAM Joget plugins — centralized status lifecycle management, constants, and utilities.

## Overview

This module provides a **single source of truth** for status transitions across all GAM entities. All GAM plugins depend on this framework to ensure consistent state management and audit logging.

## Installation

Add as a dependency in your plugin's `pom.xml`:

```xml
<dependency>
    <groupId>com.fiscaladmin.gam</groupId>
    <artifactId>gam-framework</artifactId>
    <version>8.1-SNAPSHOT</version>
</dependency>
```

## Usage

### Transitioning Status

```java
import com.fiscaladmin.gam.framework.status.*;

// Get the DAO from Joget's Spring context
FormDataDao dao = StatusManager.getFormDataDao();

// Create manager and transition
StatusManager manager = new StatusManager();
manager.transition(
    dao,
    EntityType.BANK_TRX,
    recordId,
    Status.ENRICHED,
    "my-plugin-name",    // triggeredBy
    "Enrichment complete" // reason for audit log
);
```

### Validating Before Transition

```java
StatusManager manager = new StatusManager();

// Check if transition is allowed (no DB access)
if (manager.canTransition(EntityType.BANK_TRX, Status.PROCESSING, Status.ENRICHED)) {
    // proceed
}

// Get all valid next states
Set<Status> validTargets = manager.getValidTransitions(EntityType.BANK_TRX, Status.PROCESSING);
```

### Using Status Enum

```java
// Always use Status enum, never string literals
Status current = Status.fromCode(row.getProperty("status"));
row.setProperty("status", Status.ENRICHED.getCode());

// For UI dropdowns
String label = Status.ENRICHED.getLabel(); // "Enriched"
```

## Architecture

### State Machine Diagrams

#### Statement Lifecycle

```mermaid
stateDiagram-v2
    [*] --> NEW
    NEW --> IMPORTING
    IMPORTING --> IMPORTED
    IMPORTING --> ERROR
    IMPORTED --> CONSOLIDATING
    CONSOLIDATING --> CONSOLIDATED
    CONSOLIDATING --> ERROR
    CONSOLIDATED --> ENRICHED
    CONSOLIDATED --> ERROR
    ENRICHED --> POSTED
    ERROR --> NEW
    POSTED --> [*]
```

#### Bank Transaction Lifecycle

```mermaid
stateDiagram-v2
    [*] --> NEW
    NEW --> PROCESSING
    PROCESSING --> ENRICHED
    PROCESSING --> ERROR
    PROCESSING --> MANUAL_REVIEW
    ENRICHED --> PAIRED
    ENRICHED --> POSTING_READY
    ENRICHED --> MANUAL_REVIEW
    POSTING_READY --> POSTED
    PAIRED --> POSTED
    ERROR --> NEW
    MANUAL_REVIEW --> NEW
    MANUAL_REVIEW --> ENRICHED
    MANUAL_REVIEW --> POSTING_READY
    POSTED --> [*]
```

#### Security Transaction Lifecycle

```mermaid
stateDiagram-v2
    [*] --> NEW
    NEW --> PROCESSING
    PROCESSING --> ENRICHED
    PROCESSING --> ERROR
    PROCESSING --> MANUAL_REVIEW
    ENRICHED --> PAIRED
    ENRICHED --> UNMATCHED
    ENRICHED --> MANUAL_REVIEW
    PAIRED --> POSTED
    UNMATCHED --> PAIRED
    UNMATCHED --> MANUAL_REVIEW
    ERROR --> NEW
    MANUAL_REVIEW --> NEW
    MANUAL_REVIEW --> ENRICHED
    MANUAL_REVIEW --> PAIRED
    POSTED --> [*]
```

#### Enrichment Lifecycle

```mermaid
stateDiagram-v2
    [*] --> NEW
    NEW --> ENRICHED
    NEW --> ERROR
    NEW --> MANUAL_REVIEW
    ENRICHED --> PAIRED
    ENRICHED --> POSTING_READY
    ENRICHED --> UNMATCHED
    ENRICHED --> MANUAL_REVIEW
    PAIRED --> POSTED
    POSTING_READY --> POSTED
    UNMATCHED --> PAIRED
    UNMATCHED --> MANUAL_REVIEW
    ERROR --> NEW
    MANUAL_REVIEW --> NEW
    MANUAL_REVIEW --> ENRICHED
    MANUAL_REVIEW --> POSTING_READY
    POSTED --> [*]
```

#### Pair Lifecycle

```mermaid
stateDiagram-v2
    [*] --> AUTO_ACCEPTED
    [*] --> PENDING_REVIEW
    PENDING_REVIEW --> CONFIRMED
    PENDING_REVIEW --> REJECTED
    AUTO_ACCEPTED --> [*]
    CONFIRMED --> [*]
    REJECTED --> [*]
```

#### Exception Lifecycle

```mermaid
stateDiagram-v2
    [*] --> OPEN
    OPEN --> IN_PROGRESS
    OPEN --> DISMISSED
    IN_PROGRESS --> RESOLVED
    IN_PROGRESS --> DISMISSED
    RESOLVED --> [*]
    DISMISSED --> [*]
```

### Entity Types

| Entity | Table Name | Description |
|--------|------------|-------------|
| `STATEMENT` | `bank_statement` | Bank statement files |
| `BANK_TRX` | `bank_total_trx` | Bank transactions |
| `SECU_TRX` | `secu_total_trx` | Security transactions |
| `ENRICHMENT` | `trx_enrichment` | Transaction enrichment records |
| `PAIR` | `trx_pair` | Transaction pairing records |
| `EXCEPTION` | `exception_queue` | Exception queue items |

### Audit Logging

Every transition automatically writes to the `audit_log` table:

| Field | Description |
|-------|-------------|
| `entity_type` | STATEMENT, BANK_TRX, etc. |
| `entity_id` | Record primary key |
| `from_status` | Previous status code |
| `to_status` | New status code |
| `triggered_by` | Plugin name or "OPERATOR" |
| `reason` | Human-readable explanation |
| `timestamp` | ISO 8601 timestamp |

## Building

```bash
mvn clean package
```

## Testing

```bash
# All tests
mvn test

# Single test class
mvn test -Dtest=StatusManagerTest

# Single test method
mvn test -Dtest=StatusManagerTest#testValidTransition
```

## Requirements

- Java 11+
- Joget DX 8.1
