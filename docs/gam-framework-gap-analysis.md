# GAM Framework — Gap Analysis: Specification vs Implementation

**Date**: 2026-03-03
**Spec Version**: gam-framework-specification.md v1.0
**Source Code**: `/gam-plugins/gam-framework/src/main/java/com/fiscaladmin/gam/framework/status/`
**Files Reviewed**: Status.java, EntityType.java, StatusManager.java, TransitionAuditEntry.java, InvalidTransitionException.java, EntityTypeTest.java, StatusTest.java, StatusManagerTest.java

---

## Executive Summary

The gam-framework plugin is approximately **97% spec-complete**. This is the most faithfully implemented of the three plugins analyzed so far. All 28 status values, all 7 entity types, all transition maps, all 6 public API methods, the audit mechanism, and the exception handling are implemented exactly as specified.

**4 gaps** and **2 spec-internal inconsistencies** were identified. None are HIGH priority. The most notable gap is a semantic mismatch in what gets written to the audit log's `entity_type` column: the code writes the enum name (e.g., `"STATEMENT"`) while the spec describes it as the table name (e.g., `"bank_total_trx"`).

---

## What's Fully Implemented (Matches Spec)

### Status Enum (§3)
- All **28 status values** are defined with correct codes and labels (one minor label discrepancy noted below)
- `fromCode()` is case-insensitive, returns `null` for unknown/null/empty input — matches §3.2
- `getCode()` returns lowercase database value — matches §3.2
- `getLabel()` returns UI display text — matches §3.2
- `toString()` returns the code — matches §3.2
- Status categories match spec §3.1: Universal (2), Statement-Level (4), Transaction-Level (7), Pair-Level (4), Exception-Level (4), Enrichment Workspace (4), Posting Operation (3)

### EntityType Enum (§4)
- All **7 entity types** defined with correct table name mappings — matches §4.1
- `ENRICHMENT("trx_enrichment")` — matches the canonical enum definition per §4.2
- `getTableName()` returns bare table name — matches §4.1
- `toString()` returns the enum constant name — matches implementation

### Transition Maps (§5) — ALL CORRECT
Every transition rule in the code matches the spec exactly:

- **STATEMENT** (§5.1): 7 from-states, all transitions match §5.1.2 ✓
- **BANK_TRX** (§5.2): 7 from-states, all transitions match §5.2.2 ✓
- **SECU_TRX** (§5.3): 7 from-states, all transitions match §5.3.2 ✓
- **ENRICHMENT** (§5.4): 11 from-states, all 26 transitions match §5.4.2 ✓
- **PAIR** (§5.5): 4 from-states, all transitions match §5.5.2 ✓
- **EXCEPTION** (§5.6): 4 from-states, all transitions match §5.6.2 ✓
- **POSTING_OPERATION** (§5.7): 5 from-states, all transitions match §5.7.2 ✓

Terminal states are correctly implemented as entries with `Collections.emptySet()`: CONFIRMED, SUPERSEDED (ENRICHMENT), AUTO_ACCEPTED, CONFIRMED, REJECTED (PAIR), RESOLVED, DISMISSED (EXCEPTION), POSTED, REVOKED (POSTING_OPERATION).

### Initial Status Map (§6.2.6)
All 7 entity types have correct initial statuses matching the spec:
- STATEMENT → {NEW}, BANK_TRX → {NEW}, SECU_TRX → {NEW}, ENRICHMENT → {NEW}
- PAIR → {AUTO_ACCEPTED, PENDING_REVIEW}
- EXCEPTION → {OPEN}
- POSTING_OPERATION → {PENDING}

### StatusManager API (§6.2) — ALL 6 METHODS IMPLEMENTED

1. **`transition(dao, entityType, recordId, targetStatus, triggeredBy, reason)`** — 6-parameter standard overload. Loads record, validates, writes status, creates audit. Matches §6.2.1. ✓
2. **`transition(dao, tableName, entityType, recordId, targetStatus, triggeredBy, reason)`** — 7-parameter custom table name overload for ENRICHMENT. Matches §6.2.2. ✓
3. **`canTransition(entityType, currentStatus, targetStatus)`** — Pure validation, no DB access. Handles null currentStatus by checking initial status. Matches §6.2.3. ✓
4. **`getValidTransitions(entityType, currentStatus)`** — Returns unmodifiable Set<Status>. Returns empty set for terminal/null/unknown. Matches §6.2.4. ✓
5. **`getFormDataDao()`** — Retrieves from Spring ApplicationContext. Matches §6.2.5. ✓
6. **`isInitialStatus(entityType, status)`** — Checks against INITIAL_STATUS_MAP. Matches §6.2.6. ✓

### TransitionAuditEntry (§7.2)
- Immutable (`final` class, all fields `private final`, no setters) — matches §7.2
- Fields: entityType, entityId, fromStatus, toStatus, triggeredBy, reason, timestamp — matches §7.2
- Timestamp auto-generated via `Instant.now().toString()` (ISO 8601) — matches §7.2
- `toFormRow()` converts to Joget FormRow with UUID ID — matches §7.2
- Handles null fromStatus by writing "null" string — correct for initial transitions

### InvalidTransitionException (§6.3)
- Extends `Exception` (checked) — matches §6.3
- Fields: entityType, recordId, fromStatus, toStatus with getters — matches §6.3
- Descriptive message format: "Invalid transition for {entityType} record {recordId}: {from} → {to}" — matches §6.3

### Automatic Audit Behavior (§6.4, §7.4)
- Every successful `transition()` call creates a TransitionAuditEntry and persists it via FormDataDao — matches §6.4
- Audit written to table `"audit_log"` — matches §7.3
- No manual audit calls required — matches §8.1 Rule 5

### Integration Patterns
- Static methods (no instance required) — matches §2.2
- Immutable transition map (EnumMap + Collections.unmodifiableMap) — matches §1.2
- Spring context integration via AppUtil.getApplicationContext() — matches §10.2
- No plugin properties required — matches §10.3

### Test Coverage
- **StatusTest.java**: 35 tests — covers all 28 fromCode() lookups, case insensitivity, null/edge cases, labels, getCode(), toString(), enum cardinality
- **EntityTypeTest.java**: 9 tests — covers all 7 table name mappings, cardinality, toString()
- **StatusManagerTest.java**: 60 tests — covers valid transitions, invalid transitions, terminal states, canTransition(), getValidTransitions(), isInitialStatus(), audit trail, transition map completeness, error recovery, record-not-found, data corruption
- **Total**: 104 test methods (spec §11.1 estimated 52 for StatusManagerTest — actual count exceeds this)

---

## Gaps

### GAP-1: Audit entity_type Column Stores Enum Name Instead of Table Name (MEDIUM)

**Spec Reference**: §7.3

**Spec says**: `entity_type VARCHAR(50)` described as "EntityType code (e.g., 'bank_total_trx')" — implying the Joget table name should be stored.

**Code does** (TransitionAuditEntry.java line 80):
```java
row.setProperty("entity_type", entityType.toString());
```

`EntityType.toString()` returns the enum constant name (e.g., `"STATEMENT"`, `"BANK_TRX"`, `"ENRICHMENT"`), NOT the table name (e.g., `"bank_statement"`, `"bank_total_trx"`, `"trx_enrichment"`).

**Impact**: Audit queries that reference entity_type by table name (as shown in spec §7.5 example: `WHERE entity_type = 'bank_total_trx'`) will return zero results. Instead, queries must use `WHERE entity_type = 'BANK_TRX'`. Any downstream reporting or monitoring tools must use the enum names, not table names.

**Recommended Fix**: Either change to `entityType.getTableName()` in TransitionAuditEntry, or update the spec §7.3 and §7.5 to use enum names. Using enum names is arguably better (more stable — table names could change).

---

### GAP-2: AUTO_ACCEPTED Label Has Hyphen — Spec Says No Hyphen (LOW)

**Spec Reference**: §3.1

**Spec says**: AUTO_ACCEPTED label = "Auto Accepted"

**Code does** (Status.java line 37):
```java
AUTO_ACCEPTED("auto_accepted", "Auto-Accepted"),
```

The label uses "Auto-Accepted" (hyphenated) instead of "Auto Accepted" (space-separated).

**Impact**: UI dropdowns and labels will show "Auto-Accepted" instead of "Auto Accepted". Purely cosmetic.

**Recommended Fix**: Align code to spec or update spec to match code. Either is fine.

---

### GAP-3: Record Not Found Throws IllegalStateException, Not InvalidTransitionException (LOW)

**Spec Reference**: §6.3

**Spec says**: InvalidTransitionException is thrown for "Record not found" and "Database write failure".

**Code does** (StatusManager.java lines 157-159):
```java
if (row == null) {
    throw new IllegalStateException(
            "Record not found: " + entityType + " / " + recordId);
}
```

A `IllegalStateException` (unchecked) is thrown instead of `InvalidTransitionException` (checked).

**Impact**: Consuming plugins that only catch `InvalidTransitionException` will not catch record-not-found errors — they will propagate as unchecked exceptions. The enrichment-api plugin already handles this by catching `IllegalStateException` separately in its transitionStatus() method (lines 212-214).

**Recommended Fix**: Either throw `InvalidTransitionException` for record-not-found as the spec describes, or update the spec to document that `IllegalStateException` is thrown for infrastructure errors (record not found, unrecognized status) while `InvalidTransitionException` is thrown for business rule violations (invalid transitions).

---

### GAP-4: Standard Transition Overload Uses tableName as formDefId (LOW)

**Spec Reference**: §6.2.1, §6.2.2

**Spec says**: Both overloads should use FormDataDao consistently.

**Code does**:
- Standard overload (line 156): `dao.load(tableName, tableName, recordId)` and `dao.saveOrUpdate(tableName, tableName, rowSet)` — passes tableName as both formDefId and tableName
- Custom overload (line 225): `dao.load(null, tableName, recordId)` and `dao.saveOrUpdate(null, tableName, rowSet)` — passes `null` as formDefId

The two overloads handle the `formDefId` parameter differently. The standard overload passes the table name as both parameters, while the custom overload passes `null`.

**Impact**: In Joget, the `formDefId` parameter affects form definition resolution. Using `null` is the more portable approach (lets Joget resolve the form from the table name alone). The standard overload's approach of using tableName as formDefId may cause issues if the form definition ID differs from the table name.

**Recommended Fix**: Consider making the standard overload also use `null` for formDefId, or document the difference. This is Joget-internal behavior and unlikely to cause problems in practice.

---

## Spec-Internal Inconsistencies

### INCONSISTENCY-1: §5.4 ENRICHMENT Initial Statuses Are Wrong

**Spec §5.4 header says**: "Initial Statuses: `NEW`, `AUTO_ACCEPTED`, `PENDING_REVIEW`"

**Spec §6.2.6 table says**: ENRICHMENT initial status = `NEW` (only)

**Code says**: `initMap.put(EntityType.ENRICHMENT, EnumSet.of(Status.NEW))` — only NEW

The §5.4 header appears to have been copy-pasted from the PAIR entity type. ENRICHMENT should only have `NEW` as its initial status. AUTO_ACCEPTED and PENDING_REVIEW belong to PAIR.

**Recommendation**: Fix §5.4 header to say "Initial Statuses: `NEW`".

---

### INCONSISTENCY-2: §11.1 Says "52 Test Methods" — Actual Count Is 60+ for StatusManagerTest Alone

**Spec §11.1 says**: "52 comprehensive test methods covering..."

**Actual**: StatusManagerTest has 60 test methods. Plus EntityTypeTest (9) and StatusTest (35) = 104 total. Even if spec §11.1 only means StatusManagerTest, the count is wrong.

**Recommendation**: Update §11.1 to reflect the actual test count, or remove the specific number since it drifts as tests are added.

---

## Overall Assessment

| Category | Status |
|---|---|
| **Status enum** | 28/28 values, correct codes, labels (1 minor label discrepancy) |
| **EntityType enum** | 7/7 values, correct table mappings |
| **Transition maps** | All 7 entity types match spec exactly — 0 transition discrepancies |
| **Initial status map** | All 7 entity types correct |
| **StatusManager API** | 6/6 methods implemented with correct signatures and behavior |
| **Audit logging** | Complete — automatic, immutable, FormRow-based (1 format discrepancy) |
| **InvalidTransitionException** | Complete — checked exception with full context |
| **TransitionAuditEntry** | Complete — immutable DTO with toFormRow() |
| **Test coverage** | 104 tests across 3 files — exceeds spec requirements |

### Recommended Fix Priority

1. **GAP-1** (MEDIUM): Decide on audit entity_type format — enum name vs table name — and align spec + code
2. **GAP-3** (LOW): Clarify exception types for record-not-found vs invalid-transition
3. **GAP-2** (LOW): Align AUTO_ACCEPTED label
4. **GAP-4** (LOW): Standardize formDefId handling across overloads

### Spec Fixes Needed

1. **INCONSISTENCY-1**: Fix §5.4 ENRICHMENT initial statuses (remove AUTO_ACCEPTED, PENDING_REVIEW)
2. **INCONSISTENCY-2**: Update §11.1 test method count
