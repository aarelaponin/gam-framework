package com.fiscaladmin.gam.framework.status;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.*;

/**
 * Centralised status lifecycle management for all GAM entities.
 * <p>
 * Responsibilities:
 * <ol>
 *   <li>Define all valid status transitions per entity type</li>
 *   <li>Validate transitions before they execute</li>
 *   <li>Write the new status to the entity's Joget form table</li>
 *   <li>Write an audit log entry for every transition</li>
 * </ol>
 * <p>
 * The transition map is the <b>single source of truth</b> for allowed transitions.
 * No status changes should bypass this class.
 */
public class StatusManager {

    private static final String CLASS_NAME = StatusManager.class.getName();
    private static final String AUDIT_TABLE = "audit_log";

    // ──────────────────────────────────────────────────────────────────
    //  Transition Map — single source of truth
    // ──────────────────────────────────────────────────────────────────

    private static final Map<EntityType, Map<Status, Set<Status>>> TRANSITIONS;

    static {
        Map<EntityType, Map<Status, Set<Status>>> map = new EnumMap<>(EntityType.class);

        // --- STATEMENT ---
        Map<Status, Set<Status>> stmtMap = new EnumMap<>(Status.class);
        stmtMap.put(Status.NEW,           EnumSet.of(Status.IMPORTING));
        stmtMap.put(Status.IMPORTING,     EnumSet.of(Status.IMPORTED, Status.ERROR));
        stmtMap.put(Status.IMPORTED,      EnumSet.of(Status.CONSOLIDATING));
        stmtMap.put(Status.CONSOLIDATING, EnumSet.of(Status.CONSOLIDATED, Status.ERROR));
        stmtMap.put(Status.CONSOLIDATED,  EnumSet.of(Status.ENRICHED, Status.ERROR));
        stmtMap.put(Status.ENRICHED,      EnumSet.of(Status.POSTED));
        stmtMap.put(Status.ERROR,         EnumSet.of(Status.NEW));
        map.put(EntityType.STATEMENT, Collections.unmodifiableMap(stmtMap));

        // --- BANK_TRX ---
        Map<Status, Set<Status>> bankMap = new EnumMap<>(Status.class);
        bankMap.put(Status.NEW,            EnumSet.of(Status.PROCESSING));
        bankMap.put(Status.PROCESSING,     EnumSet.of(Status.ENRICHED, Status.ERROR, Status.MANUAL_REVIEW));
        bankMap.put(Status.ENRICHED,       EnumSet.of(Status.PAIRED, Status.POSTING_READY, Status.MANUAL_REVIEW));
        bankMap.put(Status.POSTING_READY,  EnumSet.of(Status.POSTED));
        bankMap.put(Status.PAIRED,         EnumSet.of(Status.POSTED));
        bankMap.put(Status.ERROR,          EnumSet.of(Status.NEW));
        bankMap.put(Status.MANUAL_REVIEW,  EnumSet.of(Status.NEW, Status.ENRICHED, Status.POSTING_READY));
        map.put(EntityType.BANK_TRX, Collections.unmodifiableMap(bankMap));

        // --- SECU_TRX ---
        Map<Status, Set<Status>> secuMap = new EnumMap<>(Status.class);
        secuMap.put(Status.NEW,            EnumSet.of(Status.PROCESSING));
        secuMap.put(Status.PROCESSING,     EnumSet.of(Status.ENRICHED, Status.ERROR, Status.MANUAL_REVIEW));
        secuMap.put(Status.ENRICHED,       EnumSet.of(Status.PAIRED, Status.UNMATCHED, Status.MANUAL_REVIEW));
        secuMap.put(Status.PAIRED,         EnumSet.of(Status.POSTED));
        secuMap.put(Status.UNMATCHED,      EnumSet.of(Status.PAIRED, Status.MANUAL_REVIEW));
        secuMap.put(Status.ERROR,          EnumSet.of(Status.NEW));
        secuMap.put(Status.MANUAL_REVIEW,  EnumSet.of(Status.NEW, Status.ENRICHED, Status.PAIRED));
        map.put(EntityType.SECU_TRX, Collections.unmodifiableMap(secuMap));

        // --- ENRICHMENT ---
        Map<Status, Set<Status>> enrMap = new EnumMap<>(Status.class);
        enrMap.put(Status.NEW,            EnumSet.of(Status.ENRICHED, Status.ERROR, Status.MANUAL_REVIEW));
        enrMap.put(Status.ENRICHED,       EnumSet.of(Status.PAIRED, Status.POSTING_READY, Status.UNMATCHED, Status.MANUAL_REVIEW));
        enrMap.put(Status.PAIRED,         EnumSet.of(Status.POSTED));
        enrMap.put(Status.POSTING_READY,  EnumSet.of(Status.POSTED));
        enrMap.put(Status.UNMATCHED,      EnumSet.of(Status.PAIRED, Status.MANUAL_REVIEW));
        enrMap.put(Status.ERROR,          EnumSet.of(Status.NEW));
        enrMap.put(Status.MANUAL_REVIEW,  EnumSet.of(Status.NEW, Status.ENRICHED, Status.POSTING_READY));
        map.put(EntityType.ENRICHMENT, Collections.unmodifiableMap(enrMap));

        // --- PAIR ---
        Map<Status, Set<Status>> pairMap = new EnumMap<>(Status.class);
        pairMap.put(Status.AUTO_ACCEPTED,  Collections.emptySet());
        pairMap.put(Status.PENDING_REVIEW, EnumSet.of(Status.CONFIRMED, Status.REJECTED));
        pairMap.put(Status.CONFIRMED,      Collections.emptySet());
        pairMap.put(Status.REJECTED,       Collections.emptySet());
        map.put(EntityType.PAIR, Collections.unmodifiableMap(pairMap));

        // --- EXCEPTION ---
        Map<Status, Set<Status>> excMap = new EnumMap<>(Status.class);
        excMap.put(Status.OPEN,        EnumSet.of(Status.IN_PROGRESS, Status.DISMISSED));
        excMap.put(Status.IN_PROGRESS, EnumSet.of(Status.RESOLVED, Status.DISMISSED));
        excMap.put(Status.RESOLVED,    Collections.emptySet());
        excMap.put(Status.DISMISSED,   Collections.emptySet());
        map.put(EntityType.EXCEPTION, Collections.unmodifiableMap(excMap));

        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────

    /**
     * Transition an entity's status. Validates the transition, writes the new
     * status to the entity's form table, and creates an audit log entry.
     *
     * @param dao          Joget FormDataDao (pass in, or use {@link #getFormDataDao()})
     * @param entityType   the entity being transitioned
     * @param recordId     the primary key of the record
     * @param targetStatus the desired new status
     * @param triggeredBy  plugin name (e.g., "statement-importer") or "OPERATOR"
     * @param reason       human-readable explanation
     * @throws InvalidTransitionException if the transition is not allowed
     */
    public void transition(FormDataDao dao, EntityType entityType, String recordId,
                           Status targetStatus, String triggeredBy, String reason)
            throws InvalidTransitionException {

        String tableName = entityType.getTableName();

        // 1. Load current record
        FormRow row = dao.load(tableName, tableName, recordId);
        if (row == null) {
            throw new IllegalStateException(
                    "Record not found: " + entityType + " / " + recordId);
        }

        // 2. Read current status
        String currentStatusCode = row.getProperty("status");
        Status currentStatus = null;
        if (currentStatusCode != null && !currentStatusCode.isEmpty()) {
            currentStatus = Status.fromCode(currentStatusCode);
        }

        // 3. Validate
        if (!canTransition(entityType, currentStatus, targetStatus)) {
            throw new InvalidTransitionException(entityType, recordId,
                    currentStatus, targetStatus);
        }

        // 4. Write new status
        row.setProperty("status", targetStatus.getCode());
        FormRowSet rowSet = new FormRowSet();
        rowSet.add(row);
        dao.saveOrUpdate(tableName, tableName, rowSet);

        // 5. Write audit
        String fromCode = currentStatus != null ? currentStatus.getCode() : "null";
        TransitionAuditEntry audit = new TransitionAuditEntry(
                entityType.toString(), recordId,
                fromCode, targetStatus.getCode(),
                triggeredBy, reason);
        FormRowSet auditRowSet = new FormRowSet();
        auditRowSet.add(audit.toFormRow());
        dao.saveOrUpdate(AUDIT_TABLE, AUDIT_TABLE, auditRowSet);

        // 6. Log
        LogUtil.info(CLASS_NAME, "Status transition: " + entityType
                + " " + recordId + " " + fromCode + " → " + targetStatus.getCode());
    }

    /**
     * Pure validation — no database access.
     * Returns {@code true} if the transition is allowed by the transition map.
     * <p>
     * When {@code currentStatus} is {@code null}, only "initial" statuses are
     * allowed: NEW for most entities, OPEN for exceptions, AUTO_ACCEPTED or
     * PENDING_REVIEW for pairs.
     */
    public boolean canTransition(EntityType entityType, Status currentStatus,
                                 Status targetStatus) {
        if (entityType == null || targetStatus == null) {
            return false;
        }

        Map<Status, Set<Status>> entityMap = TRANSITIONS.get(entityType);
        if (entityMap == null) {
            return false;
        }

        // Handle null current status (new record without status yet)
        if (currentStatus == null) {
            return isInitialStatus(entityType, targetStatus);
        }

        Set<Status> validTargets = entityMap.get(currentStatus);
        if (validTargets == null) {
            return false;
        }
        return validTargets.contains(targetStatus);
    }

    /**
     * Returns the set of valid target statuses for the given entity and
     * current status. Returns an empty set if the current status is terminal
     * or if the entity/status combination is not found.
     */
    public Set<Status> getValidTransitions(EntityType entityType, Status currentStatus) {
        if (entityType == null || currentStatus == null) {
            return Collections.emptySet();
        }
        Map<Status, Set<Status>> entityMap = TRANSITIONS.get(entityType);
        if (entityMap == null) {
            return Collections.emptySet();
        }
        Set<Status> targets = entityMap.get(currentStatus);
        if (targets == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(targets);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Convenience
    // ──────────────────────────────────────────────────────────────────

    /**
     * Convenience method for callers who don't already hold a {@link FormDataDao}.
     * Retrieves it from the Joget Spring application context.
     */
    public static FormDataDao getFormDataDao() {
        return (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
    }

    // ──────────────────────────────────────────────────────────────────
    //  Package-private for testing
    // ──────────────────────────────────────────────────────────────────

    /**
     * Returns the transition map for testing purposes.
     */
    static Map<EntityType, Map<Status, Set<Status>>> getTransitionMap() {
        return TRANSITIONS;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Private helpers
    // ──────────────────────────────────────────────────────────────────

    /**
     * Checks whether the target status is a valid initial status for the entity.
     * This handles the case where a record has no status field set yet.
     */
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
            default:
                return false;
        }
    }
}
