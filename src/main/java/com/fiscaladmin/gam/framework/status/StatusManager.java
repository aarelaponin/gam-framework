package com.fiscaladmin.gam.framework.status;

import global.govstack.statusframework.core.StatusFramework;

import org.joget.apps.form.dao.FormDataDao;

import java.util.*;

/**
 * Centralised status lifecycle management for all GAM entities.
 * <p>
 * <b>Phase A2 refactor (2026-04-26):</b> the underlying transition engine,
 * audit row write, and validation logic now live in
 * {@link StatusFramework} (joget-status-framework). This class:
 * <ul>
 *   <li>Defines the GAM transition map as the single source of truth.</li>
 *   <li>Registers that map with {@link StatusFramework} at class-load time.</li>
 *   <li>Exposes a thin static API that delegates to {@link StatusFramework}
 *       so all existing callers in gam-plugins continue to work without
 *       any source change.</li>
 * </ul>
 * <p>
 * The local {@link #TRANSITIONS} and {@link #INITIAL_STATUS_MAP} maps are
 * retained for backwards compatibility with the regression-snapshot tests
 * ({@code TransitionMapSnapshotTest}, {@code InitialStatusMapSnapshotTest})
 * — they remain the input fed into {@link StatusFramework#register} so the
 * two views are guaranteed identical.
 */
public class StatusManager {

    // ──────────────────────────────────────────────────────────────────
    //  Transition Map — single source of truth
    // ──────────────────────────────────────────────────────────────────

    private static final Map<EntityType, Map<Status, Set<Status>>> TRANSITIONS;
    private static final Map<EntityType, Set<Status>> INITIAL_STATUS_MAP;

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

        // --- ENRICHMENT --- (Enrichment Workspace lifecycle - 11 from-states)
        Map<Status, Set<Status>> enrMap = new EnumMap<>(Status.class);
        enrMap.put(Status.NEW,            EnumSet.of(Status.PROCESSING));
        enrMap.put(Status.PROCESSING,     EnumSet.of(Status.ENRICHED, Status.ERROR, Status.MANUAL_REVIEW));
        enrMap.put(Status.ENRICHED,       EnumSet.of(Status.IN_REVIEW, Status.ADJUSTED, Status.READY,
                                                      Status.PAIRED, Status.MANUAL_REVIEW, Status.SUPERSEDED));
        enrMap.put(Status.IN_REVIEW,      EnumSet.of(Status.ADJUSTED, Status.READY, Status.ENRICHED, Status.SUPERSEDED));
        enrMap.put(Status.ADJUSTED,       EnumSet.of(Status.READY, Status.IN_REVIEW, Status.ENRICHED, Status.SUPERSEDED));
        enrMap.put(Status.READY,          EnumSet.of(Status.CONFIRMED, Status.ENRICHED, Status.IN_REVIEW, Status.SUPERSEDED));
        enrMap.put(Status.PAIRED,         EnumSet.of(Status.READY, Status.MANUAL_REVIEW));
        enrMap.put(Status.CONFIRMED,      Collections.emptySet());
        enrMap.put(Status.SUPERSEDED,     Collections.emptySet());
        enrMap.put(Status.ERROR,          EnumSet.of(Status.NEW, Status.MANUAL_REVIEW));
        enrMap.put(Status.MANUAL_REVIEW,  EnumSet.of(Status.NEW, Status.ENRICHED, Status.READY));
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

        // --- POSTING_OPERATION ---
        Map<Status, Set<Status>> postOpMap = new EnumMap<>(Status.class);
        postOpMap.put(Status.PENDING,  EnumSet.of(Status.POSTING, Status.REVOKED));
        postOpMap.put(Status.POSTING,  EnumSet.of(Status.POSTED, Status.ERROR));
        postOpMap.put(Status.POSTED,   Collections.emptySet());
        postOpMap.put(Status.ERROR,    EnumSet.of(Status.PENDING, Status.REVOKED));
        postOpMap.put(Status.REVOKED,  Collections.emptySet());
        map.put(EntityType.POSTING_OPERATION, Collections.unmodifiableMap(postOpMap));

        TRANSITIONS = Collections.unmodifiableMap(map);

        // --- INITIAL STATUS MAP ---
        Map<EntityType, Set<Status>> initMap = new EnumMap<>(EntityType.class);
        initMap.put(EntityType.STATEMENT,         EnumSet.of(Status.NEW));
        initMap.put(EntityType.BANK_TRX,          EnumSet.of(Status.NEW));
        initMap.put(EntityType.SECU_TRX,          EnumSet.of(Status.NEW));
        initMap.put(EntityType.ENRICHMENT,        EnumSet.of(Status.NEW));
        initMap.put(EntityType.PAIR,              EnumSet.of(Status.AUTO_ACCEPTED, Status.PENDING_REVIEW));
        initMap.put(EntityType.EXCEPTION,         EnumSet.of(Status.OPEN));
        initMap.put(EntityType.POSTING_OPERATION, EnumSet.of(Status.PENDING));
        INITIAL_STATUS_MAP = Collections.unmodifiableMap(initMap);

        // ─── Register every entity with the shared StatusFramework ──────────
        // The framework registry is typed against api.Status / api.EntityType;
        // our enums implement those interfaces, so we widen the generic types
        // when copying into framework-shaped maps.
        for (EntityType et : EntityType.values()) {
            Map<global.govstack.statusframework.api.Status,
                Set<global.govstack.statusframework.api.Status>> tx = new LinkedHashMap<>();
            for (Map.Entry<Status, Set<Status>> e : TRANSITIONS.get(et).entrySet()) {
                tx.put(e.getKey(),
                       new LinkedHashSet<global.govstack.statusframework.api.Status>(e.getValue()));
            }
            Set<global.govstack.statusframework.api.Status> initial =
                    new LinkedHashSet<global.govstack.statusframework.api.Status>(INITIAL_STATUS_MAP.get(et));
            StatusFramework.register(et, tx, initial);
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Public API — delegates to StatusFramework
    // ──────────────────────────────────────────────────────────────────

    /**
     * Transition an entity's status. Validates the transition, writes the new
     * status to the entity's form table, and creates an audit log entry.
     *
     * @throws InvalidTransitionException if the transition is not allowed
     */
    public static void transition(FormDataDao dao, EntityType entityType, String recordId,
                                  Status targetStatus, String triggeredBy, String reason)
            throws InvalidTransitionException {
        try {
            StatusFramework.transition(dao, entityType, recordId,
                    targetStatus, triggeredBy, reason);
        } catch (global.govstack.statusframework.api.InvalidTransitionException e) {
            // Rewrap framework's exception as gam-framework's specific subtype so
            // all existing `catch (com.fiscaladmin.gam.framework.status.InvalidTransitionException)`
            // sites in gam-plugins continue to work unchanged.
            throw new InvalidTransitionException(entityType, recordId,
                    castStatus(e.getFromStatus()), castStatus(e.getToStatus()));
        }
    }

    /**
     * Transition an entity's status using an explicit table name.
     *
     * @throws InvalidTransitionException if the transition is not allowed
     */
    public static void transition(FormDataDao dao, String tableName, EntityType entityType,
                                  String recordId, Status targetStatus,
                                  String triggeredBy, String reason)
            throws InvalidTransitionException {
        try {
            StatusFramework.transition(dao, tableName, entityType, recordId,
                    targetStatus, triggeredBy, reason);
        } catch (global.govstack.statusframework.api.InvalidTransitionException e) {
            throw new InvalidTransitionException(entityType, recordId,
                    castStatus(e.getFromStatus()), castStatus(e.getToStatus()));
        }
    }

    public static boolean canTransition(EntityType entityType, Status currentStatus,
                                        Status targetStatus) {
        return StatusFramework.canTransition(entityType, currentStatus, targetStatus);
    }

    public static Set<Status> getValidTransitions(EntityType entityType, Status currentStatus) {
        // Framework returns Set<api.Status>; narrow to Set<Status> for callers.
        Set<global.govstack.statusframework.api.Status> raw =
                StatusFramework.getValidTransitions(entityType, currentStatus);
        if (raw.isEmpty()) return Collections.emptySet();
        Set<Status> narrowed = EnumSet.noneOf(Status.class);
        for (global.govstack.statusframework.api.Status s : raw) {
            narrowed.add((Status) s);
        }
        return Collections.unmodifiableSet(narrowed);
    }

    public static boolean isInitialStatus(EntityType entityType, Status targetStatus) {
        return StatusFramework.isInitialStatus(entityType, targetStatus);
    }

    public static FormDataDao getFormDataDao() {
        return StatusFramework.getFormDataDao();
    }

    // ──────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ──────────────────────────────────────────────────────────────────

    private static Status castStatus(global.govstack.statusframework.api.Status s) {
        // Framework's CODE_INDEX is populated from gam Status values, so any
        // Status it surfaces back to us IS a gam Status. Safe cast.
        return s == null ? null : (Status) s;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Package-private — for testing only
    // ──────────────────────────────────────────────────────────────────

    /** Returns the local (gam) transition map. Used by snapshot tests. */
    static Map<EntityType, Map<Status, Set<Status>>> getTransitionMap() {
        return TRANSITIONS;
    }

    /** Returns the local (gam) initial-status map. Used by snapshot tests. */
    static Map<EntityType, Set<Status>> getInitialStatusMap() {
        return INITIAL_STATUS_MAP;
    }
}
