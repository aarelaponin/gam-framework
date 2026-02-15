package com.fiscaladmin.gam.framework.status;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for the {@link EntityType} enum — table name mappings and count.
 */
public class EntityTypeTest {

    @Test
    public void statement_tableName() {
        assertEquals("bank_statement", EntityType.STATEMENT.getTableName());
    }

    @Test
    public void bankTrx_tableName() {
        assertEquals("bank_total_trx", EntityType.BANK_TRX.getTableName());
    }

    @Test
    public void secuTrx_tableName() {
        assertEquals("secu_total_trx", EntityType.SECU_TRX.getTableName());
    }

    @Test
    public void enrichment_tableName() {
        assertEquals("trx_enrichment", EntityType.ENRICHMENT.getTableName());
    }

    @Test
    public void pair_tableName() {
        assertEquals("trx_pair", EntityType.PAIR.getTableName());
    }

    @Test
    public void exception_tableName() {
        assertEquals("exception_queue", EntityType.EXCEPTION.getTableName());
    }

    @Test
    public void totalEntityTypeCount_is6() {
        assertEquals(6, EntityType.values().length);
    }

    @Test
    public void toString_returnsEnumName() {
        assertEquals("STATEMENT", EntityType.STATEMENT.toString());
        assertEquals("BANK_TRX", EntityType.BANK_TRX.toString());
        assertEquals("EXCEPTION", EntityType.EXCEPTION.toString());
    }
}
