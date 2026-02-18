package com.fiscaladmin.gam.framework.status;

import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link StatusManager} — transition validation, execution, and audit.
 * Uses mocked {@link FormDataDao} to isolate business logic from database.
 */
public class StatusManagerTest {

    @Mock
    private FormDataDao mockDao;

    private StatusManager statusManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        statusManager = new StatusManager();
    }

    // ── Helper ──────────────────────────────────────────────────────

    private FormRow createRowWithStatus(String status) {
        FormRow row = new FormRow();
        row.setProperty("status", status);
        return row;
    }

    private void mockLoad(EntityType entityType, String recordId, String status) {
        String table = entityType.getTableName();
        when(mockDao.load(table, table, recordId))
                .thenReturn(createRowWithStatus(status));
    }

    // ════════════════════════════════════════════════════════════════
    //  1. Valid transitions succeed (one per entity type)
    // ════════════════════════════════════════════════════════════════

    @Test
    public void statement_new_to_importing() throws Exception {
        mockLoad(EntityType.STATEMENT, "S001", "new");
        statusManager.transition(mockDao, EntityType.STATEMENT, "S001",
                Status.IMPORTING, "statement-importer", "File upload started");
        verifyStatusSaved("bank_statement", "importing");
    }

    @Test
    public void bankTrx_new_to_processing() throws Exception {
        mockLoad(EntityType.BANK_TRX, "BT001", "new");
        statusManager.transition(mockDao, EntityType.BANK_TRX, "BT001",
                Status.PROCESSING, "rows-enrichment", "Enrichment started");
        verifyStatusSaved("bank_total_trx", "processing");
    }

    @Test
    public void secuTrx_processing_to_enriched() throws Exception {
        mockLoad(EntityType.SECU_TRX, "ST001", "processing");
        statusManager.transition(mockDao, EntityType.SECU_TRX, "ST001",
                Status.ENRICHED, "rows-enrichment", "Enrichment complete");
        verifyStatusSaved("secu_total_trx", "enriched");
    }

    @Test
    public void enrichment_enriched_to_paired() throws Exception {
        mockLoad(EntityType.ENRICHMENT, "E001", "enriched");
        statusManager.transition(mockDao, EntityType.ENRICHMENT, "E001",
                Status.PAIRED, "gl-preparator", "Pairing complete");
        verifyStatusSaved("trx_enrichment", "paired");
    }

    @Test
    public void pair_pendingReview_to_confirmed() throws Exception {
        mockLoad(EntityType.PAIR, "P001", "pending_review");
        statusManager.transition(mockDao, EntityType.PAIR, "P001",
                Status.CONFIRMED, "OPERATOR", "Operator confirmed pair");
        verifyStatusSaved("trx_pair", "confirmed");
    }

    @Test
    public void exception_open_to_inProgress() throws Exception {
        mockLoad(EntityType.EXCEPTION, "EX001", "open");
        statusManager.transition(mockDao, EntityType.EXCEPTION, "EX001",
                Status.IN_PROGRESS, "OPERATOR", "Investigation started");
        verifyStatusSaved("exception_queue", "in_progress");
    }

    // ════════════════════════════════════════════════════════════════
    //  2. Invalid transitions throw InvalidTransitionException
    // ════════════════════════════════════════════════════════════════

    @Test(expected = InvalidTransitionException.class)
    public void statement_new_to_posted_throws() throws Exception {
        mockLoad(EntityType.STATEMENT, "S001", "new");
        statusManager.transition(mockDao, EntityType.STATEMENT, "S001",
                Status.POSTED, "test", "Should fail");
    }

    @Test(expected = InvalidTransitionException.class)
    public void bankTrx_new_to_enriched_throws() throws Exception {
        mockLoad(EntityType.BANK_TRX, "BT001", "new");
        statusManager.transition(mockDao, EntityType.BANK_TRX, "BT001",
                Status.ENRICHED, "test", "Should fail");
    }

    @Test(expected = InvalidTransitionException.class)
    public void pair_confirmed_to_pendingReview_throws() throws Exception {
        mockLoad(EntityType.PAIR, "P001", "confirmed");
        statusManager.transition(mockDao, EntityType.PAIR, "P001",
                Status.PENDING_REVIEW, "test", "Should fail — terminal state");
    }

    @Test(expected = InvalidTransitionException.class)
    public void exception_resolved_to_open_throws() throws Exception {
        mockLoad(EntityType.EXCEPTION, "EX001", "resolved");
        statusManager.transition(mockDao, EntityType.EXCEPTION, "EX001",
                Status.OPEN, "test", "Should fail — terminal state");
    }

    @Test(expected = InvalidTransitionException.class)
    public void secuTrx_new_to_paired_throws() throws Exception {
        mockLoad(EntityType.SECU_TRX, "ST001", "new");
        statusManager.transition(mockDao, EntityType.SECU_TRX, "ST001",
                Status.PAIRED, "test", "Should fail — skipping states");
    }

    // ════════════════════════════════════════════════════════════════
    //  3. Terminal states have no valid transitions
    // ════════════════════════════════════════════════════════════════

    @Test
    public void pair_confirmed_isTerminal() {
        assertTrue(statusManager.getValidTransitions(EntityType.PAIR, Status.CONFIRMED).isEmpty());
    }

    @Test
    public void pair_rejected_isTerminal() {
        assertTrue(statusManager.getValidTransitions(EntityType.PAIR, Status.REJECTED).isEmpty());
    }

    @Test
    public void pair_autoAccepted_isTerminal() {
        assertTrue(statusManager.getValidTransitions(EntityType.PAIR, Status.AUTO_ACCEPTED).isEmpty());
    }

    @Test
    public void exception_resolved_isTerminal() {
        assertTrue(statusManager.getValidTransitions(EntityType.EXCEPTION, Status.RESOLVED).isEmpty());
    }

    @Test
    public void exception_dismissed_isTerminal() {
        assertTrue(statusManager.getValidTransitions(EntityType.EXCEPTION, Status.DISMISSED).isEmpty());
    }

    // ════════════════════════════════════════════════════════════════
    //  4. canTransition() returns correct boolean
    // ════════════════════════════════════════════════════════════════

    @Test
    public void canTransition_validTransition_returnsTrue() {
        assertTrue(statusManager.canTransition(EntityType.STATEMENT, Status.NEW, Status.IMPORTING));
        assertTrue(statusManager.canTransition(EntityType.BANK_TRX, Status.PROCESSING, Status.ENRICHED));
        assertTrue(statusManager.canTransition(EntityType.PAIR, Status.PENDING_REVIEW, Status.CONFIRMED));
    }

    @Test
    public void canTransition_invalidTransition_returnsFalse() {
        assertFalse(statusManager.canTransition(EntityType.STATEMENT, Status.NEW, Status.POSTED));
        assertFalse(statusManager.canTransition(EntityType.PAIR, Status.CONFIRMED, Status.PENDING_REVIEW));
    }

    @Test
    public void canTransition_nullEntityType_returnsFalse() {
        assertFalse(statusManager.canTransition(null, Status.NEW, Status.IMPORTING));
    }

    @Test
    public void canTransition_nullTargetStatus_returnsFalse() {
        assertFalse(statusManager.canTransition(EntityType.STATEMENT, Status.NEW, null));
    }

    @Test
    public void canTransition_nullCurrentStatus_allowsInitialStatus() {
        assertTrue(statusManager.canTransition(EntityType.STATEMENT, null, Status.NEW));
        assertTrue(statusManager.canTransition(EntityType.EXCEPTION, null, Status.OPEN));
        assertTrue(statusManager.canTransition(EntityType.PAIR, null, Status.AUTO_ACCEPTED));
        assertTrue(statusManager.canTransition(EntityType.PAIR, null, Status.PENDING_REVIEW));
    }

    @Test
    public void canTransition_nullCurrentStatus_rejectsNonInitialStatus() {
        assertFalse(statusManager.canTransition(EntityType.STATEMENT, null, Status.POSTED));
        assertFalse(statusManager.canTransition(EntityType.EXCEPTION, null, Status.RESOLVED));
    }

    // ════════════════════════════════════════════════════════════════
    //  5. Error recovery transitions work
    // ════════════════════════════════════════════════════════════════

    @Test
    public void statement_error_to_new() throws Exception {
        mockLoad(EntityType.STATEMENT, "S001", "error");
        statusManager.transition(mockDao, EntityType.STATEMENT, "S001",
                Status.NEW, "OPERATOR", "Retrying after error fix");
        verifyStatusSaved("bank_statement", "new");
    }

    @Test
    public void bankTrx_error_to_new() throws Exception {
        mockLoad(EntityType.BANK_TRX, "BT001", "error");
        statusManager.transition(mockDao, EntityType.BANK_TRX, "BT001",
                Status.NEW, "OPERATOR", "Retrying after error fix");
        verifyStatusSaved("bank_total_trx", "new");
    }

    @Test
    public void bankTrx_manualReview_to_new() throws Exception {
        mockLoad(EntityType.BANK_TRX, "BT001", "manual_review");
        statusManager.transition(mockDao, EntityType.BANK_TRX, "BT001",
                Status.NEW, "OPERATOR", "Reset for reprocessing");
        verifyStatusSaved("bank_total_trx", "new");
    }

    @Test
    public void bankTrx_manualReview_to_enriched() throws Exception {
        mockLoad(EntityType.BANK_TRX, "BT001", "manual_review");
        statusManager.transition(mockDao, EntityType.BANK_TRX, "BT001",
                Status.ENRICHED, "OPERATOR", "Manual enrichment completed");
        verifyStatusSaved("bank_total_trx", "enriched");
    }

    @Test
    public void enrichment_error_to_new() throws Exception {
        mockLoad(EntityType.ENRICHMENT, "E001", "error");
        statusManager.transition(mockDao, EntityType.ENRICHMENT, "E001",
                Status.NEW, "OPERATOR", "Retrying after data fix");
        verifyStatusSaved("trx_enrichment", "new");
    }

    // ════════════════════════════════════════════════════════════════
    //  6. Audit entry is written on every successful transition
    // ════════════════════════════════════════════════════════════════

    @Test
    public void transition_writesAuditEntry() throws Exception {
        mockLoad(EntityType.STATEMENT, "S001", "new");
        statusManager.transition(mockDao, EntityType.STATEMENT, "S001",
                Status.IMPORTING, "statement-importer", "File upload started");

        // saveOrUpdate called twice: once for entity, once for audit
        ArgumentCaptor<String> formDefCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FormRowSet> rowSetCaptor = ArgumentCaptor.forClass(FormRowSet.class);
        verify(mockDao, times(2)).saveOrUpdate(formDefCaptor.capture(), tableCaptor.capture(), rowSetCaptor.capture());

        // Second call should be the audit_log
        assertEquals("audit_log", tableCaptor.getAllValues().get(1));

        // Verify audit row content
        FormRowSet auditRowSet = rowSetCaptor.getAllValues().get(1);
        assertEquals(1, auditRowSet.size());
        FormRow auditRow = auditRowSet.get(0);
        assertEquals("STATEMENT", auditRow.getProperty("entity_type"));
        assertEquals("S001", auditRow.getProperty("entity_id"));
        assertEquals("new", auditRow.getProperty("from_status"));
        assertEquals("importing", auditRow.getProperty("to_status"));
        assertEquals("statement-importer", auditRow.getProperty("triggered_by"));
        assertEquals("File upload started", auditRow.getProperty("reason"));
        assertNotNull(auditRow.getProperty("timestamp"));
    }

    @Test
    public void invalidTransition_doesNotWriteAudit() {
        mockLoad(EntityType.STATEMENT, "S001", "new");
        try {
            statusManager.transition(mockDao, EntityType.STATEMENT, "S001",
                    Status.POSTED, "test", "Should fail");
            fail("Expected InvalidTransitionException");
        } catch (InvalidTransitionException e) {
            // Expected
        }
        // saveOrUpdate should NOT have been called at all
        verify(mockDao, never()).saveOrUpdate(anyString(), anyString(), any(FormRowSet.class));
    }

    // ════════════════════════════════════════════════════════════════
    //  7. Transition map completeness
    // ════════════════════════════════════════════════════════════════

    @Test
    public void transitionMap_has7EntityTypes() {
        Map<EntityType, Map<Status, Set<Status>>> map = StatusManager.getTransitionMap();
        assertEquals(7, map.size());
    }

    @Test
    public void statement_has7FromStates() {
        assertEquals(7, StatusManager.getTransitionMap().get(EntityType.STATEMENT).size());
    }

    @Test
    public void bankTrx_has7FromStates() {
        assertEquals(7, StatusManager.getTransitionMap().get(EntityType.BANK_TRX).size());
    }

    @Test
    public void secuTrx_has7FromStates() {
        assertEquals(7, StatusManager.getTransitionMap().get(EntityType.SECU_TRX).size());
    }

    @Test
    public void enrichment_has11FromStates() {
        assertEquals(11, StatusManager.getTransitionMap().get(EntityType.ENRICHMENT).size());
    }

    @Test
    public void pair_has4FromStates() {
        assertEquals(4, StatusManager.getTransitionMap().get(EntityType.PAIR).size());
    }

    @Test
    public void exception_has4FromStates() {
        assertEquals(4, StatusManager.getTransitionMap().get(EntityType.EXCEPTION).size());
    }

    @Test
    public void postingOperation_has5FromStates() {
        assertEquals(5, StatusManager.getTransitionMap().get(EntityType.POSTING_OPERATION).size());
    }

    // ════════════════════════════════════════════════════════════════
    //  8. getValidTransitions() returns correct sets
    // ════════════════════════════════════════════════════════════════

    @Test
    public void getValidTransitions_statement_new() {
        Set<Status> targets = statusManager.getValidTransitions(EntityType.STATEMENT, Status.NEW);
        assertEquals(1, targets.size());
        assertTrue(targets.contains(Status.IMPORTING));
    }

    @Test
    public void getValidTransitions_bankTrx_processing() {
        Set<Status> targets = statusManager.getValidTransitions(EntityType.BANK_TRX, Status.PROCESSING);
        assertEquals(3, targets.size());
        assertTrue(targets.contains(Status.ENRICHED));
        assertTrue(targets.contains(Status.ERROR));
        assertTrue(targets.contains(Status.MANUAL_REVIEW));
    }

    @Test
    public void getValidTransitions_unknownEntityStatus_returnsEmpty() {
        // Status.IMPORTING is not a valid "from" state for BANK_TRX
        Set<Status> targets = statusManager.getValidTransitions(EntityType.BANK_TRX, Status.IMPORTING);
        assertTrue(targets.isEmpty());
    }

    @Test
    public void getValidTransitions_nullEntity_returnsEmpty() {
        assertTrue(statusManager.getValidTransitions(null, Status.NEW).isEmpty());
    }

    @Test
    public void getValidTransitions_nullStatus_returnsEmpty() {
        assertTrue(statusManager.getValidTransitions(EntityType.STATEMENT, null).isEmpty());
    }

    // ════════════════════════════════════════════════════════════════
    //  9. Record not found throws IllegalStateException
    // ════════════════════════════════════════════════════════════════

    @Test(expected = IllegalStateException.class)
    public void transition_recordNotFound_throws() throws Exception {
        String table = EntityType.STATEMENT.getTableName();
        when(mockDao.load(table, table, "MISSING")).thenReturn(null);
        statusManager.transition(mockDao, EntityType.STATEMENT, "MISSING",
                Status.IMPORTING, "test", "Should fail");
    }

    // ════════════════════════════════════════════════════════════════
    //  10. ENRICHMENT new workspace transitions
    // ════════════════════════════════════════════════════════════════

    @Test
    public void enrichment_new_to_processing() throws Exception {
        mockLoad(EntityType.ENRICHMENT, "E001", "new");
        statusManager.transition(mockDao, EntityType.ENRICHMENT, "E001",
                Status.PROCESSING, "pipeline", "Processing started");
        verifyStatusSaved("trx_enrichment", "processing");
    }

    @Test
    public void enrichment_enriched_to_inReview() throws Exception {
        mockLoad(EntityType.ENRICHMENT, "E001", "enriched");
        statusManager.transition(mockDao, EntityType.ENRICHMENT, "E001",
                Status.IN_REVIEW, "OPERATOR", "Customer reviewing");
        verifyStatusSaved("trx_enrichment", "in_review");
    }

    @Test
    public void enrichment_enriched_to_adjusted() throws Exception {
        mockLoad(EntityType.ENRICHMENT, "E001", "enriched");
        statusManager.transition(mockDao, EntityType.ENRICHMENT, "E001",
                Status.ADJUSTED, "OPERATOR", "Customer made adjustments");
        verifyStatusSaved("trx_enrichment", "adjusted");
    }

    @Test
    public void enrichment_enriched_to_ready() throws Exception {
        mockLoad(EntityType.ENRICHMENT, "E001", "enriched");
        statusManager.transition(mockDao, EntityType.ENRICHMENT, "E001",
                Status.READY, "OPERATOR", "Ready for posting");
        verifyStatusSaved("trx_enrichment", "ready");
    }

    @Test
    public void enrichment_inReview_to_adjusted() throws Exception {
        mockLoad(EntityType.ENRICHMENT, "E001", "in_review");
        statusManager.transition(mockDao, EntityType.ENRICHMENT, "E001",
                Status.ADJUSTED, "OPERATOR", "Adjustments made");
        verifyStatusSaved("trx_enrichment", "adjusted");
    }

    @Test
    public void enrichment_adjusted_to_ready() throws Exception {
        mockLoad(EntityType.ENRICHMENT, "E001", "adjusted");
        statusManager.transition(mockDao, EntityType.ENRICHMENT, "E001",
                Status.READY, "OPERATOR", "Ready for posting");
        verifyStatusSaved("trx_enrichment", "ready");
    }

    @Test
    public void enrichment_ready_to_confirmed() throws Exception {
        mockLoad(EntityType.ENRICHMENT, "E001", "ready");
        statusManager.transition(mockDao, EntityType.ENRICHMENT, "E001",
                Status.CONFIRMED, "posting-service", "Confirmed");
        verifyStatusSaved("trx_enrichment", "confirmed");
    }

    @Test
    public void enrichment_enriched_to_superseded() throws Exception {
        mockLoad(EntityType.ENRICHMENT, "E001", "enriched");
        statusManager.transition(mockDao, EntityType.ENRICHMENT, "E001",
                Status.SUPERSEDED, "correction-service", "Superseded by correction");
        verifyStatusSaved("trx_enrichment", "superseded");
    }

    @Test
    public void enrichment_confirmed_isTerminal() {
        assertTrue(statusManager.getValidTransitions(EntityType.ENRICHMENT, Status.CONFIRMED).isEmpty());
    }

    @Test
    public void enrichment_superseded_isTerminal() {
        assertTrue(statusManager.getValidTransitions(EntityType.ENRICHMENT, Status.SUPERSEDED).isEmpty());
    }

    @Test(expected = InvalidTransitionException.class)
    public void enrichment_enriched_to_postingReady_nowFails() throws Exception {
        // Old transition that is no longer valid in the new map
        mockLoad(EntityType.ENRICHMENT, "E001", "enriched");
        statusManager.transition(mockDao, EntityType.ENRICHMENT, "E001",
                Status.POSTING_READY, "test", "Should fail - old transition removed");
    }

    // ════════════════════════════════════════════════════════════════
    //  11. POSTING_OPERATION transitions
    // ════════════════════════════════════════════════════════════════

    @Test
    public void postingOperation_pending_to_posting() throws Exception {
        mockLoad(EntityType.POSTING_OPERATION, "PO001", "pending");
        statusManager.transition(mockDao, EntityType.POSTING_OPERATION, "PO001",
                Status.POSTING, "posting-service", "Posting started");
        verifyStatusSaved("posting_operation", "posting");
    }

    @Test
    public void postingOperation_posting_to_posted() throws Exception {
        mockLoad(EntityType.POSTING_OPERATION, "PO001", "posting");
        statusManager.transition(mockDao, EntityType.POSTING_OPERATION, "PO001",
                Status.POSTED, "posting-service", "Posting complete");
        verifyStatusSaved("posting_operation", "posted");
    }

    @Test
    public void postingOperation_posting_to_error() throws Exception {
        mockLoad(EntityType.POSTING_OPERATION, "PO001", "posting");
        statusManager.transition(mockDao, EntityType.POSTING_OPERATION, "PO001",
                Status.ERROR, "posting-service", "Posting failed");
        verifyStatusSaved("posting_operation", "error");
    }

    @Test
    public void postingOperation_pending_to_revoked() throws Exception {
        mockLoad(EntityType.POSTING_OPERATION, "PO001", "pending");
        statusManager.transition(mockDao, EntityType.POSTING_OPERATION, "PO001",
                Status.REVOKED, "OPERATOR", "Operation revoked");
        verifyStatusSaved("posting_operation", "revoked");
    }

    @Test
    public void postingOperation_error_to_pending() throws Exception {
        mockLoad(EntityType.POSTING_OPERATION, "PO001", "error");
        statusManager.transition(mockDao, EntityType.POSTING_OPERATION, "PO001",
                Status.PENDING, "OPERATOR", "Retry after fix");
        verifyStatusSaved("posting_operation", "pending");
    }

    @Test
    public void postingOperation_error_to_revoked() throws Exception {
        mockLoad(EntityType.POSTING_OPERATION, "PO001", "error");
        statusManager.transition(mockDao, EntityType.POSTING_OPERATION, "PO001",
                Status.REVOKED, "OPERATOR", "Revoked after error");
        verifyStatusSaved("posting_operation", "revoked");
    }

    @Test
    public void postingOperation_posted_isTerminal() {
        assertTrue(statusManager.getValidTransitions(EntityType.POSTING_OPERATION, Status.POSTED).isEmpty());
    }

    @Test
    public void postingOperation_revoked_isTerminal() {
        assertTrue(statusManager.getValidTransitions(EntityType.POSTING_OPERATION, Status.REVOKED).isEmpty());
    }

    @Test
    public void canTransition_postingOperation_nullCurrentStatus_allowsPending() {
        assertTrue(statusManager.canTransition(EntityType.POSTING_OPERATION, null, Status.PENDING));
    }

    @Test
    public void canTransition_postingOperation_nullCurrentStatus_rejectsNonPending() {
        assertFalse(statusManager.canTransition(EntityType.POSTING_OPERATION, null, Status.POSTED));
        assertFalse(statusManager.canTransition(EntityType.POSTING_OPERATION, null, Status.POSTING));
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Verifies that saveOrUpdate was called for the entity table with the expected status.
     */
    private void verifyStatusSaved(String expectedTable, String expectedStatus) {
        ArgumentCaptor<String> formDefCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FormRowSet> rowSetCaptor = ArgumentCaptor.forClass(FormRowSet.class);
        verify(mockDao, atLeastOnce()).saveOrUpdate(formDefCaptor.capture(), tableCaptor.capture(), rowSetCaptor.capture());

        // First saveOrUpdate call is for the entity table
        assertEquals(expectedTable, tableCaptor.getAllValues().get(0));
        FormRowSet savedRowSet = rowSetCaptor.getAllValues().get(0);
        assertEquals(1, savedRowSet.size());
        assertEquals(expectedStatus, savedRowSet.get(0).getProperty("status"));
    }
}
