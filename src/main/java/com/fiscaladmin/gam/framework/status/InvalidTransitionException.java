package com.fiscaladmin.gam.framework.status;

/**
 * Checked exception thrown when an invalid status transition is attempted.
 * <p>
 * Carries full context about the failed transition for diagnostics and logging.
 */
public class InvalidTransitionException extends Exception {

    private final EntityType entityType;
    private final String recordId;
    private final Status fromStatus;
    private final Status toStatus;

    public InvalidTransitionException(EntityType entityType, String recordId,
                                      Status fromStatus, Status toStatus) {
        super("Invalid transition for " + entityType + " record " + recordId
                + ": " + (fromStatus != null ? fromStatus.getCode() : "null")
                + " → " + toStatus.getCode());
        this.entityType = entityType;
        this.recordId = recordId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getRecordId() {
        return recordId;
    }

    public Status getFromStatus() {
        return fromStatus;
    }

    public Status getToStatus() {
        return toStatus;
    }
}
