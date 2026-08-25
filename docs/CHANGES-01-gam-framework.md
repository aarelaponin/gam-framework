# gam-framework — Change Spec

**Version:** 8.1-SNAPSHOT (no version bump needed)
**Status:** NO CODE CHANGES REQUIRED
**Date:** 4 March 2026

---

## 1. Purpose of This Document

This document confirms that gam-framework already supports everything needed for the rows-enrichment Phase 1 fixes and Phase 2 pairing. No code changes are required. It documents the exact transition maps that downstream plugins depend on.

---

## 2. Verification Checklist

Before proceeding with statement-importer or rows-enrichment changes, verify these framework capabilities exist:

### 2.1 ENRICHMENT Entity Transitions

The following transitions are required by rows-enrichment:

| From | To | Used By | Verified |
|------|----|---------|----------|
| NEW | PROCESSING | Phase 1: pipeline start | ☐ |
| PROCESSING | ENRICHED | Phase 1: all steps OK (bank and secu) | ☐ |
| PROCESSING | MANUAL_REVIEW | Phase 1: resolution error | ☐ |
| PROCESSING | ERROR | Phase 1: pipeline error | ☐ |
| ENRICHED | PAIRED | Phase 2: secu↔bank pairing | ☐ |
| MANUAL_REVIEW | ENRICHED | Error fix: operator resolves issue, re-enrichment | ☐ |
| PAIRED | READY | enrichment-workspace: operator marks ready for posting | ☐ |

**Expected result:** All transitions above exist in `StatusManager.TRANSITIONS` for `EntityType.ENRICHMENT`.

### 2.2 PAIR Entity Type

| Check | Expected | Verified |
|-------|----------|----------|
| `EntityType.PAIR` exists | Yes | ☐ |
| `EntityType.PAIR.getTableName()` | `"trx_pair"` | ☐ |
| Initial statuses for PAIR | AUTO_ACCEPTED, PENDING_REVIEW | ☐ |
| PENDING_REVIEW → CONFIRMED | Valid | ☐ |
| PENDING_REVIEW → REJECTED | Valid | ☐ |
| AUTO_ACCEPTED → (terminal) | No outgoing transitions | ☐ |

### 2.3 BANK_TRX and SECU_TRX Source Transaction Transitions

| From | To | Entity | Used By |
|------|----|--------|---------|
| NEW | PROCESSING | BANK_TRX, SECU_TRX | Phase 1: pipeline start |
| PROCESSING | ENRICHED | BANK_TRX, SECU_TRX | Phase 1: all steps OK |
| PROCESSING | MANUAL_REVIEW | BANK_TRX, SECU_TRX | Phase 1: resolution error |
| ENRICHED | PAIRED | BANK_TRX, SECU_TRX | Phase 2: paired with counterpart |

### 2.4 EntityType Table Mappings

| EntityType | Expected Table | Used By |
|------------|---------------|---------|
| ENRICHMENT | `trxEnrichment` | rows-enrichment persister |
| PAIR | `trx_pair` | Phase 2 pairing records |
| BANK_TRX | `bank_total_trx` | Source transaction status updates |
| SECU_TRX | `secu_total_trx` | Source transaction status updates |
| EXCEPTION | `exception_queue` | Exception creation |

---

## 3. Key Constraint: MANUAL_REVIEW → PAIRED Not Allowed for ENRICHMENT

The ENRICHMENT transition map allows:
```
MANUAL_REVIEW → {NEW, ENRICHED, READY}
```

It does NOT allow `MANUAL_REVIEW → PAIRED`. This is correct by design:
- Records with enrichment errors must be fixed first (MANUAL_REVIEW → ENRICHED)
- Only ENRICHED records are eligible for Phase 2 pairing (ENRICHED → PAIRED)
- This constraint drives the decision in rows-enrichment: secu records with all automated dimensions resolved go to ENRICHED (not MANUAL_REVIEW), even though customer is UNKNOWN

---

## 4. Testing

Run existing gam-framework unit tests to confirm no regressions:

```bash
cd gam-plugins/gam-framework
mvn test
```

Expected: 119 tests pass (39 Status + 9 EntityType + 71 StatusManager).

No new tests needed since no code changes are made.
