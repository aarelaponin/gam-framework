package com.fiscaladmin.gam.framework.status;

import org.joget.apps.form.model.FormRow;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable DTO representing one status transition audit log record.
 * <p>
 * Timestamp is auto-generated at creation time using ISO 8601 format.
 */
public class TransitionAuditEntry {

    private final String entityType;
    private final String entityId;
    private final String fromStatus;
    private final String toStatus;
    private final String triggeredBy;
    private final String reason;
    private final String timestamp;

    /**
     * Creates a new audit entry. Timestamp is set automatically to now.
     *
     * @param entityType  e.g., "STATEMENT", "BANK_TRX"
     * @param entityId    the record ID
     * @param fromStatus  previous status code
     * @param toStatus    new status code
     * @param triggeredBy plugin name (e.g., "statement-importer") or "OPERATOR"
     * @param reason      human-readable explanation
     */
    public TransitionAuditEntry(String entityType, String entityId,
                                String fromStatus, String toStatus,
                                String triggeredBy, String reason) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.triggeredBy = triggeredBy;
        this.reason = reason;
        this.timestamp = Instant.now().toString();
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public String getReason() {
        return reason;
    }

    public String getTimestamp() {
        return timestamp;
    }

    /**
     * Converts this DTO to a Joget {@link FormRow} for persistence
     * via {@code FormDataDao.saveOrUpdate("audit_log", rowSet)}.
     */
    public FormRow toFormRow() {
        FormRow row = new FormRow();
        row.setId(UUID.randomUUID().toString());
        row.setProperty("entity_type", entityType);
        row.setProperty("entity_id", entityId);
        row.setProperty("from_status", fromStatus);
        row.setProperty("to_status", toStatus);
        row.setProperty("triggered_by", triggeredBy);
        row.setProperty("reason", reason);
        row.setProperty("timestamp", timestamp);
        return row;
    }
}
