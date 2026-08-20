-- ==========================================
-- Command Allowlist (exact-match whitelist for OPERATOR-authored CUSTOM_COMMAND
-- metric definitions). No FK to metric_definitions - the relationship is a
-- runtime exact-string check performed by MetricDefinitionService, not
-- referential integrity.
-- ==========================================
CREATE TABLE command_allowlist (
    id                BIGSERIAL PRIMARY KEY,
    command_template  VARCHAR(500) NOT NULL UNIQUE,
    description       VARCHAR(300) NOT NULL,
    category          VARCHAR(50)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(100) NOT NULL
);

CREATE INDEX idx_command_allowlist_category ON command_allowlist (category);
