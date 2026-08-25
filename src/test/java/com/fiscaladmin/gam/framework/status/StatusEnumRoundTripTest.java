package com.fiscaladmin.gam.framework.status;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Round-trip and structural integrity tests for the {@link Status} enum.
 * <p>
 * Catches regressions when {@code Status} is later refactored to implement an
 * external interface (Phase A2 plan): every value's code must still survive
 * the {@code getCode() -> fromCode()} round trip, codes must be unique, and
 * labels must not be blank.
 */
public class StatusEnumRoundTripTest {

    /**
     * For every Status, {@code Status.fromCode(s.getCode()) == s}.
     */
    @Test
    public void everyStatus_codeRoundTripsExactCase() {
        for (Status s : Status.values()) {
            Status round = Status.fromCode(s.getCode());
            assertEquals("Round-trip failed for " + s.name()
                    + " — fromCode(" + s.getCode() + ") returned " + round,
                    s, round);
        }
    }

    /**
     * Codes are case-insensitive: uppercase, mixed case, and as-stored all map
     * back to the same enum constant.
     */
    @Test
    public void everyStatus_codeRoundTripsCaseInsensitive() {
        for (Status s : Status.values()) {
            assertEquals("upper-case fromCode broke for " + s.name(),
                    s, Status.fromCode(s.getCode().toUpperCase()));
            assertEquals("mixed-case fromCode broke for " + s.name(),
                    s, Status.fromCode(toggleCase(s.getCode())));
        }
    }

    /**
     * Codes must be globally unique — two enum values cannot share a database code.
     */
    @Test
    public void allStatusCodes_areUnique() {
        Set<String> codes = new HashSet<>();
        for (Status s : Status.values()) {
            assertTrue("Duplicate status code: '" + s.getCode() + "' shared by "
                    + s.name() + " and another enum value",
                    codes.add(s.getCode()));
        }
    }

    /**
     * Labels must be non-null and non-blank — they're shown directly in Joget UI.
     */
    @Test
    public void allStatusLabels_areNotBlank() {
        for (Status s : Status.values()) {
            assertNotNull(s.name() + " has null label", s.getLabel());
            assertTrue(s.name() + " has blank label '" + s.getLabel() + "'",
                    !s.getLabel().trim().isEmpty());
        }
    }

    /**
     * Codes use lowercase + underscore convention only.
     * No spaces, no camelCase, no mixed case in the database.
     */
    @Test
    public void allStatusCodes_followLowercaseUnderscoreConvention() {
        for (Status s : Status.values()) {
            String code = s.getCode();
            assertTrue(s.name() + " code '" + code
                            + "' violates [a-z0-9_]+ convention",
                    code.matches("[a-z0-9_]+"));
        }
    }

    /**
     * Locks the total count so accidental status additions or deletions are caught.
     * If you intentionally add/remove a Status, update this number AND the
     * transition snapshot.
     */
    @Test
    public void totalStatusCount_is28() {
        assertEquals(28, Status.values().length);
    }

    private static String toggleCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(i % 2 == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return sb.toString();
    }
}
