# 00 — Prerequisites Specification

**Scope:** Everything that must be completed before any Enrichment Workspace component can be built.  
**Dependencies:** None — this is the root specification.  
**Estimated effort:** ~8 hours (Priority 1) + ~4 hours (Priority 2) + ~4 hours (Priority 3)  
**Dev Plan tasks:** P1-1 through P1-6, P2-1 through P2-4, P3-1 through P3-4  

---

## 1. Purpose

This document specifies all prerequisite work that must be completed before any Enrichment Workspace development can begin. The prerequisites fall into three priority tiers:

- **Priority 1 (P1)** — Must be done before any code is written. Blocks all tracks.
- **Priority 2 (P2)** — Must be done before Phase 1 testing. Blocks form modifications and pipeline test data.
- **Priority 3 (P3)** — Must be done before Phase 2. Blocks breaking changes and plugin audits.

The system being built is the Enrichment Workspace for the GAM Automated Accounting System, running on Joget DX Enterprise Edition 8.1. It consists of two forms: F01.05 (`trxEnrichment` — the working area for reviewing and adjusting enriched transactions) and F01.06 (`postingOperation` — the posting commitment record created when transactions are confirmed).

---

## 2. Prerequisites Summary

| # | Action | Priority | Effort | Status |
|---|--------|----------|--------|--------|
| P1-1 | Update `gam-framework`: add EntityType, Status values, transition maps | P1 | 2h | Pending |
| P1-2 | Build and deploy `gam-framework` JAR to Joget shared classpath | P1 | 30 min | Pending |
| P1-3 | Verify all 6 master data forms are deployed in Joget | P1 | 30 min | Pending |
| P1-4 | Load minimum master data records | P1 | 1–2h | Pending |
| P1-5 | Verify F01.05 form (`trxEnrichment`) exists; check current field inventory | P1 | 30 min | Pending |
| P1-6 | Create F01.06 form (`postingOperation`) — 30 fields, 6 sections | P1 | 2h | Pending |
| P2-1 | Run full pipeline with sample CSV to populate test data | P2 | 1–2h | Pending |
| P2-2 | Apply Phase 1 (non-breaking) modifications to F01.05 form | P2 | 1–2h | Pending |
| P2-3 | Load asset master data from sample CSV tickers | P2 | 1h | Pending |
| P2-4 | Load customer account records linking customers to LHV counterparty | P2 | 30 min | Pending |
| P3-1 | Audit `rows-enrichment` plugin for hardcoded status values and field writes | P3 | 2–3h | Pending |
| P3-2 | Audit `gl-preparator` plugin for same | P3 | 1–2h | Pending |
| P3-3 | Apply Phase 2 (breaking) modifications to F01.05 form | P3 | 1h | Pending |
| P3-4 | Run status migration SQL on existing F01.05 data | P3 | 15 min | Pending |

---

## 3. Specification: P1-1 — gam-framework Updates

### 3.1 Context

The `gam-framework` module at `/gam-plugins/gam-framework/` contains the centralised status lifecycle management for all GAM entities. The `status` package has 5 classes:

| Class | Location | Current Contents |
|-------|----------|-----------------|
| `EntityType.java` | `status/EntityType.java` | 6 values: STATEMENT, BANK_TRX, SECU_TRX, ENRICHMENT, PAIR, EXCEPTION |
| `Status.java` | `status/Status.java` | 21 values across 5 groups (Universal, Statement, Transaction, Pair, Exception) |
| `StatusManager.java` | `status/StatusManager.java` | Transition maps for all 6 entity types |
| `InvalidTransitionException.java` | `status/InvalidTransitionException.java` | Runtime exception for invalid transitions |
| `TransitionAuditEntry.java` | `status/TransitionAuditEntry.java` | Audit log record builder |

The Enrichment Workspace introduces two new lifecycle requirements:
1. An expanded **ENRICHMENT** transition map (11 from-states, replacing the current 7-state map)
2. A new **POSTING_OPERATION** entity type with its own 5-state lifecycle

### 3.2 Change 1: Add `POSTING_OPERATION` to EntityType

**File:** `gam-framework/src/main/java/com/fiscaladmin/gam/framework/status/EntityType.java`

Add after the `EXCEPTION` entry:

```java
POSTING_OPERATION("posting_operation");
```

The complete enum becomes:

```java
public enum EntityType {

    STATEMENT("bank_statement"),
    BANK_TRX("bank_total_trx"),
    SECU_TRX("secu_total_trx"),
    ENRICHMENT("trx_enrichment"),
    PAIR("trx_pair"),
    EXCEPTION("exception_queue"),
    POSTING_OPERATION("posting_operation");

    // ... constructor and methods unchanged
}
```

**Rationale:** The F01.06 form (`postingOperation`) writes to table `posting_operation` (MySQL: `app_fd_posting_operation`). This entity has its own status lifecycle separate from ENRICHMENT.

### 3.3 Change 2: Add 7 New Status Values

**File:** `gam-framework/src/main/java/com/fiscaladmin/gam/framework/status/Status.java`

Add the following 7 values. Place them in two new groups after the existing groups:

```java
    // === Enrichment workspace ===
    IN_REVIEW("in_review", "In Review"),
    ADJUSTED("adjusted", "Adjusted"),
    READY("ready", "Ready"),
    SUPERSEDED("superseded", "Superseded"),

    // === Posting operation ===
    PENDING("pending", "Pending"),
    POSTING("posting", "Posting"),
    REVOKED("revoked", "Revoked"),
```

**Place these before the closing semicolon**, after the existing `DISMISSED` entry. The complete enum will have 28 values (21 existing + 7 new).

**Note on CONFIRMED:** `Status.CONFIRMED` already exists in the Pair group. It is reused for the ENRICHMENT lifecycle. The `Status` enum is flat — entity-specificity is enforced by the transition map, not the enum itself.

### 3.4 Change 3: Replace ENRICHMENT Transition Map

**File:** `gam-framework/src/main/java/com/fiscaladmin/gam/framework/status/StatusManager.java`

Replace the entire `// --- ENRICHMENT ---` block in the static initialiser. The current map:

```java
// --- ENRICHMENT --- (CURRENT — TO BE REPLACED)
Map<Status, Set<Status>> enrMap = new EnumMap<>(Status.class);
enrMap.put(Status.NEW,            EnumSet.of(Status.ENRICHED, Status.ERROR, Status.MANUAL_REVIEW));
enrMap.put(Status.ENRICHED,       EnumSet.of(Status.PAIRED, Status.POSTING_READY, Status.UNMATCHED, Status.MANUAL_REVIEW));
enrMap.put(Status.PAIRED,         EnumSet.of(Status.POSTED));
enrMap.put(Status.POSTING_READY,  EnumSet.of(Status.POSTED));
enrMap.put(Status.UNMATCHED,      EnumSet.of(Status.PAIRED, Status.MANUAL_REVIEW));
enrMap.put(Status.ERROR,          EnumSet.of(Status.NEW));
enrMap.put(Status.MANUAL_REVIEW,  EnumSet.of(Status.NEW, Status.ENRICHED, Status.POSTING_READY));
map.put(EntityType.ENRICHMENT, Collections.unmodifiableMap(enrMap));
```

Replace with:

```java
// --- ENRICHMENT --- (Enrichment Workspace lifecycle — 11 from-states)
Map<Status, Set<Status>> enrMap = new EnumMap<>(Status.class);
// Pipeline creates
enrMap.put(Status.NEW,            EnumSet.of(Status.PROCESSING));
enrMap.put(Status.PROCESSING,     EnumSet.of(Status.ENRICHED, Status.ERROR, Status.MANUAL_REVIEW));
// Customer works
enrMap.put(Status.ENRICHED,       EnumSet.of(Status.IN_REVIEW, Status.ADJUSTED, Status.READY,
                                              Status.PAIRED, Status.MANUAL_REVIEW, Status.SUPERSEDED));
enrMap.put(Status.IN_REVIEW,      EnumSet.of(Status.ADJUSTED, Status.READY, Status.ENRICHED));
enrMap.put(Status.ADJUSTED,       EnumSet.of(Status.READY, Status.IN_REVIEW, Status.ENRICHED));
enrMap.put(Status.READY,          EnumSet.of(Status.CONFIRMED, Status.ENRICHED, Status.IN_REVIEW));
enrMap.put(Status.PAIRED,         EnumSet.of(Status.READY, Status.MANUAL_REVIEW));
// Terminal/special
enrMap.put(Status.CONFIRMED,      Collections.emptySet());  // Terminal — transferred to F01.06
enrMap.put(Status.SUPERSEDED,     Collections.emptySet());  // Terminal — replaced by children
enrMap.put(Status.ERROR,          EnumSet.of(Status.NEW, Status.MANUAL_REVIEW));
enrMap.put(Status.MANUAL_REVIEW,  EnumSet.of(Status.NEW, Status.ENRICHED, Status.READY));
map.put(EntityType.ENRICHMENT, Collections.unmodifiableMap(enrMap));
```

**Key differences from old map:**
- `NEW` now transitions to `PROCESSING` (not directly to `ENRICHED`) — the pipeline has an intermediate processing step
- `ENRICHED` has 6 targets (was 4): adds `IN_REVIEW`, `ADJUSTED`, `READY`, `SUPERSEDED`
- New states `IN_REVIEW`, `ADJUSTED`, `READY` form the customer review cycle
- `CONFIRMED` and `SUPERSEDED` are terminal states
- `POSTING_READY`, `UNMATCHED`, `POSTED` are no longer valid for ENRICHMENT (these belong to the old pre-F01.06 model)

### 3.5 Change 4: Add POSTING_OPERATION Transition Map

**File:** `gam-framework/src/main/java/com/fiscaladmin/gam/framework/status/StatusManager.java`

Add after the `// --- EXCEPTION ---` block, before `TRANSITIONS = Collections.unmodifiableMap(map);`:

```java
// --- POSTING_OPERATION ---
Map<Status, Set<Status>> postOpMap = new EnumMap<>(Status.class);
postOpMap.put(Status.PENDING,  EnumSet.of(Status.POSTING, Status.REVOKED));
postOpMap.put(Status.POSTING,  EnumSet.of(Status.POSTED, Status.ERROR));
postOpMap.put(Status.POSTED,   Collections.emptySet());  // Terminal
postOpMap.put(Status.ERROR,    EnumSet.of(Status.PENDING, Status.REVOKED));
postOpMap.put(Status.REVOKED,  Collections.emptySet());  // Terminal
map.put(EntityType.POSTING_OPERATION, Collections.unmodifiableMap(postOpMap));
```

**Lifecycle description:**
- `PENDING` — Record created by Confirm for Posting action. Waiting for GL engine.
- `POSTING` — GL engine has picked up the record and is processing.
- `POSTED` — GL engine completed successfully. `acc_post_id` is set. Terminal.
- `ERROR` — GL engine encountered a problem. Can be retried (`→ PENDING`) or revoked (`→ REVOKED`).
- `REVOKED` — Customer pulled back before posting. F01.05 records return to editable status. Terminal.

### 3.6 Change 5: Update `isInitialStatus()`

**File:** `gam-framework/src/main/java/com/fiscaladmin/gam/framework/status/StatusManager.java`

Add a new case to the `isInitialStatus()` switch:

```java
case POSTING_OPERATION:
    return targetStatus == Status.PENDING;
```

The complete method becomes:

```java
private boolean isInitialStatus(EntityType entityType, Status targetStatus) {
    switch (entityType) {
        case STATEMENT:
        case BANK_TRX:
        case SECU_TRX:
            return targetStatus == Status.NEW;
        case ENRICHMENT:
            return targetStatus == Status.NEW;
        case PAIR:
            return targetStatus == Status.AUTO_ACCEPTED
                    || targetStatus == Status.PENDING_REVIEW;
        case EXCEPTION:
            return targetStatus == Status.OPEN;
        case POSTING_OPERATION:
            return targetStatus == Status.PENDING;
        default:
            return false;
    }
}
```

### 3.7 Backwards Compatibility

**Impact analysis on existing plugins:**

| Plugin | Maven dependency on gam-framework? | Impact |
|--------|------------------------------------|--------|
| `rows-enrichment` | ❌ No | **Zero impact** — does not import `com.fiscaladmin.gam.framework.status.*`, uses hardcoded string literals |
| `gl-preparator` | ❌ No (likely) | **Zero impact** — same pattern as `rows-enrichment` |
| `statement-importer` | ❌ No (likely) | **Zero impact** — same pattern |
| `gam-enrichment-workspace` (new) | ✅ Yes (by design) | N/A — will be built against updated framework |

The existing plugins do not use `gam-framework` at all. They are independent OSGi bundles with inline status logic. Adding new enum values and changing transition maps has zero risk of breaking deployed plugins.

**Data compatibility risk:** When the new `gam-enrichment-workspace` plugin starts using `StatusManager.transition()`, any existing F01.05 records with old status values (`posting_ready`, `unmatched`, `posted`) will not be valid starting states in the new transition map. This is handled by P3-4 (status migration SQL).

### 3.8 Unit Tests

Add or update tests in `gam-framework/src/test/java/com/fiscaladmin/gam/framework/status/`:

**Test: EntityType has POSTING_OPERATION**
```java
@Test
public void testPostingOperationEntityType() {
    EntityType po = EntityType.POSTING_OPERATION;
    assertEquals("posting_operation", po.getTableName());
}
```

**Test: New Status values exist**
```java
@Test
public void testNewStatusValues() {
    assertEquals("in_review", Status.IN_REVIEW.getCode());
    assertEquals("adjusted", Status.ADJUSTED.getCode());
    assertEquals("ready", Status.READY.getCode());
    assertEquals("superseded", Status.SUPERSEDED.getCode());
    assertEquals("pending", Status.PENDING.getCode());
    assertEquals("posting", Status.POSTING.getCode());
    assertEquals("revoked", Status.REVOKED.getCode());
}

@Test
public void testFromCodeRoundTrip() {
    for (Status s : new Status[]{Status.IN_REVIEW, Status.ADJUSTED, Status.READY,
            Status.SUPERSEDED, Status.PENDING, Status.POSTING, Status.REVOKED}) {
        assertEquals(s, Status.fromCode(s.getCode()));
    }
}
```

**Test: ENRICHMENT transition map**
```java
@Test
public void testEnrichmentTransitions() {
    StatusManager mgr = new StatusManager();
    // Pipeline path
    assertTrue(mgr.canTransition(EntityType.ENRICHMENT, Status.NEW, Status.PROCESSING));
    assertTrue(mgr.canTransition(EntityType.ENRICHMENT, Status.PROCESSING, Status.ENRICHED));
    assertTrue(mgr.canTransition(EntityType.ENRICHMENT, Status.PROCESSING, Status.ERROR));
    // Customer review cycle
    assertTrue(mgr.canTransition(EntityType.ENRICHMENT, Status.ENRICHED, Status.IN_REVIEW));
    assertTrue(mgr.canTransition(EntityType.ENRICHMENT, Status.IN_REVIEW, Status.ADJUSTED));
    assertTrue(mgr.canTransition(EntityType.ENRICHMENT, Status.ADJUSTED, Status.READY));
    assertTrue(mgr.canTransition(EntityType.ENRICHMENT, Status.READY, Status.CONFIRMED));
    // Terminal states
    assertFalse(mgr.canTransition(EntityType.ENRICHMENT, Status.CONFIRMED, Status.ENRICHED));
    assertFalse(mgr.canTransition(EntityType.ENRICHMENT, Status.SUPERSEDED, Status.NEW));
    // Old states should NOT be valid
    assertFalse(mgr.canTransition(EntityType.ENRICHMENT, Status.ENRICHED, Status.POSTING_READY));
    assertFalse(mgr.canTransition(EntityType.ENRICHMENT, Status.ENRICHED, Status.UNMATCHED));
}

@Test
public void testEnrichmentInitialStatus() {
    StatusManager mgr = new StatusManager();
    assertTrue(mgr.canTransition(EntityType.ENRICHMENT, null, Status.NEW));
    assertFalse(mgr.canTransition(EntityType.ENRICHMENT, null, Status.ENRICHED));
}
```

**Test: POSTING_OPERATION transition map**
```java
@Test
public void testPostingOperationTransitions() {
    StatusManager mgr = new StatusManager();
    // Happy path
    assertTrue(mgr.canTransition(EntityType.POSTING_OPERATION, Status.PENDING, Status.POSTING));
    assertTrue(mgr.canTransition(EntityType.POSTING_OPERATION, Status.POSTING, Status.POSTED));
    // Error recovery
    assertTrue(mgr.canTransition(EntityType.POSTING_OPERATION, Status.ERROR, Status.PENDING));
    assertTrue(mgr.canTransition(EntityType.POSTING_OPERATION, Status.ERROR, Status.REVOKED));
    // Revoke
    assertTrue(mgr.canTransition(EntityType.POSTING_OPERATION, Status.PENDING, Status.REVOKED));
    // Terminal states
    assertFalse(mgr.canTransition(EntityType.POSTING_OPERATION, Status.POSTED, Status.PENDING));
    assertFalse(mgr.canTransition(EntityType.POSTING_OPERATION, Status.REVOKED, Status.PENDING));
}

@Test
public void testPostingOperationInitialStatus() {
    StatusManager mgr = new StatusManager();
    assertTrue(mgr.canTransition(EntityType.POSTING_OPERATION, null, Status.PENDING));
    assertFalse(mgr.canTransition(EntityType.POSTING_OPERATION, null, Status.NEW));
}
```

### 3.9 Verification

After making changes:

1. Run `cd /Users/aarelaponin/IdeaProjects/gam-plugins/gam-framework && mvn clean install`
2. All existing tests must pass
3. All new tests must pass
4. Verify the JAR is created at `target/gam-framework-8.1-SNAPSHOT.jar`

---

## 4. Specification: P1-2 — Build and Deploy gam-framework

### 4.1 Build

```bash
cd /Users/aarelaponin/IdeaProjects/gam-plugins/gam-framework
mvn clean install
```

Expected: `BUILD SUCCESS`, JAR at `target/gam-framework-8.1-SNAPSHOT.jar`.

### 4.2 Deploy to Joget Shared Classpath

The `gam-framework` JAR is a shared library (not an OSGi bundle). It must be placed on the Joget classpath so that OSGi plugins can import its packages.

**Deployment location:** `{JOGET_HOME}/wflow/lib/` (or equivalent shared classpath directory in your Joget installation).

```bash
cp target/gam-framework-8.1-SNAPSHOT.jar {JOGET_HOME}/wflow/lib/
```

**Restart Joget** after copying the JAR. The shared classpath is loaded at startup.

### 4.3 Verification

After deployment, verify:

1. Check JAR exists: `ls -la {JOGET_HOME}/wflow/lib/gam-framework-8.1-SNAPSHOT.jar`
2. Restart Joget and check for errors in Joget server logs
3. (Optional) Deploy a minimal test OSGi bundle that imports `com.fiscaladmin.gam.framework.status.StatusManager` and calls `StatusManager.getFormDataDao()` to verify classloading works

---
