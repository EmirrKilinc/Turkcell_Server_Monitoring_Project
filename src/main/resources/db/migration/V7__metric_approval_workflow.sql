-- ==========================================
-- Metric Definition Approval Workflow
-- ==========================================
ALTER TABLE metric_definitions
    ADD COLUMN approval_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING_APPROVAL',
    ADD COLUMN created_by       VARCHAR(100),
    ADD COLUMN approved_by      VARCHAR(100),
    ADD COLUMN rejection_reason VARCHAR(500),
    ADD COLUMN approved_at      TIMESTAMP;

ALTER TABLE metric_definitions
    ADD CONSTRAINT chk_metric_definitions_approval_status
    CHECK (approval_status IN ('APPROVED', 'PENDING_APPROVAL', 'REJECTED'));

-- Backfill: the 20 metrics seeded by V5 are pre-vetted and already in active
-- use (attached to groups) - they must not regress to unusable PENDING_APPROVAL.
UPDATE metric_definitions
   SET approval_status = 'APPROVED',
       created_by = 'system',
       approved_by = 'system',
       approved_at = CURRENT_TIMESTAMP;

CREATE INDEX idx_metric_definitions_approval_status ON metric_definitions (approval_status);
