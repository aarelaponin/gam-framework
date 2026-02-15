package com.fiscaladmin.gam.framework.status;

/**
 * Entity types in the GAM pipeline that carry a status lifecycle.
 * <p>
 * Each enum constant maps to the <b>bare</b> Joget form table name
 * (without the {@code app_fd_} prefix that Joget adds automatically).
 */
public enum EntityType {

    STATEMENT("bank_statement"),
    BANK_TRX("bank_total_trx"),
    SECU_TRX("secu_total_trx"),
    ENRICHMENT("trx_enrichment"),
    PAIR("trx_pair"),
    EXCEPTION("exception_queue");

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
