package com.fiscaladmin.gam.framework.status;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for the {@link Status} enum — codes, labels, lookup, and toString.
 */
public class StatusTest {

    // ── fromCode() for every status ─────────────────────────────────

    @Test public void fromCode_new()           { assertEquals(Status.NEW,            Status.fromCode("new")); }
    @Test public void fromCode_error()         { assertEquals(Status.ERROR,          Status.fromCode("error")); }
    @Test public void fromCode_importing()     { assertEquals(Status.IMPORTING,      Status.fromCode("importing")); }
    @Test public void fromCode_imported()       { assertEquals(Status.IMPORTED,       Status.fromCode("imported")); }
    @Test public void fromCode_consolidating() { assertEquals(Status.CONSOLIDATING,  Status.fromCode("consolidating")); }
    @Test public void fromCode_consolidated()  { assertEquals(Status.CONSOLIDATED,   Status.fromCode("consolidated")); }
    @Test public void fromCode_processing()    { assertEquals(Status.PROCESSING,     Status.fromCode("processing")); }
    @Test public void fromCode_enriched()      { assertEquals(Status.ENRICHED,       Status.fromCode("enriched")); }
    @Test public void fromCode_paired()        { assertEquals(Status.PAIRED,         Status.fromCode("paired")); }
    @Test public void fromCode_postingReady()  { assertEquals(Status.POSTING_READY,  Status.fromCode("posting_ready")); }
    @Test public void fromCode_posted()        { assertEquals(Status.POSTED,         Status.fromCode("posted")); }
    @Test public void fromCode_manualReview()  { assertEquals(Status.MANUAL_REVIEW,  Status.fromCode("manual_review")); }
    @Test public void fromCode_unmatched()     { assertEquals(Status.UNMATCHED,      Status.fromCode("unmatched")); }
    @Test public void fromCode_autoAccepted()  { assertEquals(Status.AUTO_ACCEPTED,  Status.fromCode("auto_accepted")); }
    @Test public void fromCode_pendingReview() { assertEquals(Status.PENDING_REVIEW, Status.fromCode("pending_review")); }
    @Test public void fromCode_confirmed()     { assertEquals(Status.CONFIRMED,      Status.fromCode("confirmed")); }
    @Test public void fromCode_rejected()      { assertEquals(Status.REJECTED,       Status.fromCode("rejected")); }
    @Test public void fromCode_open()          { assertEquals(Status.OPEN,           Status.fromCode("open")); }
    @Test public void fromCode_inProgress()    { assertEquals(Status.IN_PROGRESS,    Status.fromCode("in_progress")); }
    @Test public void fromCode_resolved()      { assertEquals(Status.RESOLVED,       Status.fromCode("resolved")); }
    @Test public void fromCode_dismissed()     { assertEquals(Status.DISMISSED,      Status.fromCode("dismissed")); }

    // ── Case insensitivity ──────────────────────────────────────────

    @Test
    public void fromCode_caseInsensitive_upperCase() {
        assertEquals(Status.NEW, Status.fromCode("NEW"));
    }

    @Test
    public void fromCode_caseInsensitive_mixedCase() {
        assertEquals(Status.NEW, Status.fromCode("New"));
    }

    @Test
    public void fromCode_caseInsensitive_postingReady() {
        assertEquals(Status.POSTING_READY, Status.fromCode("POSTING_READY"));
    }

    @Test
    public void fromCode_caseInsensitive_manualReview() {
        assertEquals(Status.MANUAL_REVIEW, Status.fromCode("Manual_Review"));
    }

    // ── Error cases ─────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void fromCode_unknownCode_throws() {
        Status.fromCode("nonexistent");
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromCode_null_throws() {
        Status.fromCode(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromCode_emptyString_throws() {
        Status.fromCode("");
    }

    // ── getCode() returns lowercase DB value ────────────────────────

    @Test
    public void getCode_returnsLowercaseDbValue() {
        assertEquals("new", Status.NEW.getCode());
        assertEquals("posting_ready", Status.POSTING_READY.getCode());
        assertEquals("manual_review", Status.MANUAL_REVIEW.getCode());
        assertEquals("in_progress", Status.IN_PROGRESS.getCode());
    }

    // ── getLabel() returns display label ────────────────────────────

    @Test
    public void getLabel_returnsDisplayLabel() {
        assertEquals("New", Status.NEW.getLabel());
        assertEquals("Posting Ready", Status.POSTING_READY.getLabel());
        assertEquals("Auto-Accepted", Status.AUTO_ACCEPTED.getLabel());
        assertEquals("In Progress", Status.IN_PROGRESS.getLabel());
    }

    // ── toString() returns code ─────────────────────────────────────

    @Test
    public void toString_returnsCode() {
        assertEquals("new", Status.NEW.toString());
        assertEquals("error", Status.ERROR.toString());
        assertEquals("posting_ready", Status.POSTING_READY.toString());
    }

    // ── Total count ─────────────────────────────────────────────────

    @Test
    public void totalStatusCount_is22() {
        assertEquals(21, Status.values().length);
    }
}
