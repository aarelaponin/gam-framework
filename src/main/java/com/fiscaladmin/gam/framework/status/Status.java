package com.fiscaladmin.gam.framework.status;

/**
 * All status values used across the GAM system.
 * <p>
 * Each enum constant has:
 * <ul>
 *   <li>{@code code} — the lowercase value stored in the database</li>
 *   <li>{@code label} — the human-readable label shown in Joget UI dropdowns</li>
 * </ul>
 * <p>
 * This is the single source of truth for status values. No string literals
 * should be used anywhere in the codebase for status values.
 */
public enum Status {

    // === Universal ===
    NEW("new", "New"),
    ERROR("error", "Error"),

    // === Statement-level ===
    IMPORTING("importing", "Importing"),
    IMPORTED("imported", "Imported"),
    CONSOLIDATING("consolidating", "Consolidating"),
    CONSOLIDATED("consolidated", "Consolidated"),

    // === Transaction-level ===
    PROCESSING("processing", "Processing"),
    ENRICHED("enriched", "Enriched"),
    PAIRED("paired", "Paired"),
    POSTING_READY("posting_ready", "Posting Ready"),
    POSTED("posted", "Posted"),
    MANUAL_REVIEW("manual_review", "Manual Review"),
    UNMATCHED("unmatched", "Unmatched"),

    // === Pair-level ===
    AUTO_ACCEPTED("auto_accepted", "Auto-Accepted"),
    PENDING_REVIEW("pending_review", "Pending Review"),
    CONFIRMED("confirmed", "Confirmed"),
    REJECTED("rejected", "Rejected"),

    // === Exception-level ===
    OPEN("open", "Open"),
    IN_PROGRESS("in_progress", "In Progress"),
    RESOLVED("resolved", "Resolved"),
    DISMISSED("dismissed", "Dismissed");

    private final String code;
    private final String label;

    Status(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** Returns the lowercase value stored in the database. */
    public String getCode() {
        return code;
    }

    /** Returns the human-readable label for Joget UI dropdowns. */
    public String getLabel() {
        return label;
    }

    /**
     * Lookup a Status by its database code value.
     * Comparison is case-insensitive.
     *
     * @param code the database code (e.g., "new", "importing")
     * @return the matching Status enum constant
     * @throws IllegalArgumentException if no Status matches the given code
     */
    public static Status fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Status code must not be null");
        }
        String lowerCode = code.toLowerCase();
        for (Status s : values()) {
            if (s.code.equals(lowerCode)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }

    /** Returns the database code value. */
    @Override
    public String toString() {
        return code;
    }
}
