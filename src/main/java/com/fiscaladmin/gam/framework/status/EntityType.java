package com.fiscaladmin.gam.framework.status;

/**
 * Entity types in the GAM pipeline that carry a status lifecycle.
 * <p>
 * Each enum constant maps to the <b>bare</b> Joget form table name
 * (without the {@code app_fd_} prefix that Joget adds automatically).
 * <p>
 * Implements {@link global.govstack.statusframework.api.EntityType} so GAM
 * entities are first-class citizens of the shared joget-status-framework
 * registry. External callers continue to reference {@code EntityType.STATEMENT}
 * etc. exactly as before — no API change.
 */
public enum EntityType implements global.govstack.statusframework.api.EntityType {

    STATEMENT("bank_statement"),
    BANK_TRX("bank_total_trx"),
    SECU_TRX("secu_total_trx"),
    ENRICHMENT("trxEnrichment"),
    PAIR("trx_pair"),
    EXCEPTION("exception_queue"),
    POSTING_OPERATION("posting_operation");

    private final String tableName;

    EntityType(String tableName) {
        this.tableName = tableName;
    }

    /** Returns the bare table name used in {@code FormDataDao} calls. */
    public String getTableName() {
        return tableName;
    }

    @Override
    public String toString() {
        return name();
    }
}
