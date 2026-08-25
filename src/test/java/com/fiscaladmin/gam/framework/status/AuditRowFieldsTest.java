package com.fiscaladmin.gam.framework.status;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Locks the EXACT shape of the audit row written to {@code audit_log} on
 * every transition. Prevents regressions to the column set, value semantics,
 * and timestamp format during the Phase A2 refactor.
 * <p>
 * Existing {@link StatusManagerTest#transition_writesAuditEntry} confirms
 * the audit call happens; this test sharpens that by capturing every column
 * value with an {@link ArgumentCaptor} and asserting field-by-field.
 */
public class AuditRowFieldsTest {

    @Mock
    private FormDataDao mockDao;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    /**
     * After a successful transition, audit row carries:
     * id (UUID), entity_type (enum.toString), entity_id, from_status (code),
     * to_status (code), triggered_by, reason, timestamp (ISO-8601 instant).
     */
    @Test
    public void auditRow_containsAllExpectedColumns_andCorrectValues() throws Exception {
        FormRow current = new FormRow();
        current.setProperty("status", "new");
        when(mockDao.load(null, EntityType.STATEMENT.getTableName(), "S001"))
                .thenReturn(current);

        long beforeMs = Instant.now().toEpochMilli();
        StatusManager.transition(mockDao, EntityType.STATEMENT, "S001",
                Status.IMPORTING, "statement-importer-test", "Unit test trigger");
        long afterMs = Instant.now().toEpochMilli();

        // Capture both saveOrUpdate calls (entity row + audit row)
        ArgumentCaptor<String> tableArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FormRowSet> rowSetArg = ArgumentCaptor.forClass(FormRowSet.class);
        verify(mockDao, times(2))
                .saveOrUpdate(any(), tableArg.capture(), rowSetArg.capture());

        // Locate the audit invocation (table name = "audit_log")
        FormRow audit = null;
        for (int i = 0; i < tableArg.getAllValues().size(); i++) {
            if ("audit_log".equals(tableArg.getAllValues().get(i))) {
                audit = rowSetArg.getAllValues().get(i).get(0);
                break;
            }
        }
        assertNotNull("No saveOrUpdate call targeted the audit_log table", audit);

        // Field-by-field assertions
        assertNotNull("audit.id is null", audit.getId());
        try {
            UUID.fromString(audit.getId());
        } catch (IllegalArgumentException e) {
            fail("audit.id is not a valid UUID: " + audit.getId());
        }

        assertEquals("STATEMENT",            audit.getProperty("entity_type"));
        assertEquals("S001",                 audit.getProperty("entity_id"));
        assertEquals("new",                  audit.getProperty("from_status"));
        assertEquals("importing",            audit.getProperty("to_status"));
        assertEquals("statement-importer-test",
                                             audit.getProperty("triggered_by"));
        assertEquals("Unit test trigger",    audit.getProperty("reason"));

        // Timestamp must be ISO-8601, parseable by Instant
        String ts = audit.getProperty("timestamp");
        assertNotNull("audit.timestamp is null", ts);
        Instant parsed;
        try {
            parsed = Instant.parse(ts);
        } catch (DateTimeParseException e) {
            fail("audit.timestamp is not ISO-8601: " + ts);
            return;
        }
        long tsMs = parsed.toEpochMilli();
        assertTrue("audit.timestamp out of bounds: " + tsMs
                        + " not in [" + beforeMs + "," + afterMs + "]",
                tsMs >= beforeMs && tsMs <= afterMs);

        // No surprise columns — exactly 7 properties + id
        // (entity_type, entity_id, from_status, to_status, triggered_by, reason, timestamp)
        // We can't reliably enumerate FormRow keys without reflection; instead spot-check
        // that nothing unexpected is set under common alternative names:
        assertNull("Did not expect 'transitionTime'", audit.getProperty("transitionTime"));
        assertNull("Did not expect 'fromStatus'",     audit.getProperty("fromStatus"));
        assertNull("Did not expect 'toStatus'",       audit.getProperty("toStatus"));
        assertNull("Did not expect 'entityType'",     audit.getProperty("entityType"));
    }

    /**
     * Initial transition (no current status) yields the literal string {@code "null"}
     * in {@code from_status} — NOT a real null, NOT an empty string. This is the
     * documented contract of {@link TransitionAuditEntry#toFormRow()}.
     */
    @Test
    public void auditRow_fromStatus_isLiteralNullStringWhenInitial() throws Exception {
        FormRow current = new FormRow();
        // No status property → currentStatus passed in is null → from_status = "null"
        when(mockDao.load(null, EntityType.STATEMENT.getTableName(), "S002"))
                .thenReturn(current);

        StatusManager.transition(mockDao, EntityType.STATEMENT, "S002",
                Status.NEW, "test", "initial");

        ArgumentCaptor<String> tableArg = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FormRowSet> rowSetArg = ArgumentCaptor.forClass(FormRowSet.class);
        verify(mockDao, times(2))
                .saveOrUpdate(any(), tableArg.capture(), rowSetArg.capture());

        FormRow audit = null;
        for (int i = 0; i < tableArg.getAllValues().size(); i++) {
            if ("audit_log".equals(tableArg.getAllValues().get(i))) {
                audit = rowSetArg.getAllValues().get(i).get(0);
                break;
            }
        }
        assertNotNull(audit);
        assertEquals("from_status must be the literal string 'null' for initial transitions",
                "null", audit.getProperty("from_status"));
    }
}
