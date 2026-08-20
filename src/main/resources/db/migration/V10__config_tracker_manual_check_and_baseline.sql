-- ==========================================
-- force_check_requested: lets an operator/admin force a tracked file into
-- "due" on the very next agent sync, regardless of its check_interval_seconds.
-- baseline_accepted_by/at: audit trail for the "accept drift as new
-- baseline" action, mirrors the approved_by/approved_at pattern already
-- used by the metric approval workflow (V7).
-- ==========================================
ALTER TABLE tracked_config_files
    ADD COLUMN force_check_requested BOOLEAN   NOT NULL DEFAULT FALSE,
    ADD COLUMN baseline_accepted_by  VARCHAR(100),
    ADD COLUMN baseline_accepted_at  TIMESTAMP;
