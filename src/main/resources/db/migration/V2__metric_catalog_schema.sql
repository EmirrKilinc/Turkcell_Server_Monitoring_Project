-- ==========================================
-- Metric Definitions (reusable catalog entries)
-- ==========================================
CREATE TABLE metric_definitions (
    id                 BIGSERIAL PRIMARY KEY,
    name               VARCHAR(150) NOT NULL,
    metric_key         VARCHAR(100) NOT NULL UNIQUE,
    category           VARCHAR(50)  NOT NULL,
    type               VARCHAR(30)  NOT NULL,
    command_payload    TEXT         NOT NULL,
    is_custom_command  BOOLEAN      NOT NULL DEFAULT FALSE,
    description        VARCHAR(500),
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_metric_definitions_type CHECK (type IN
        ('PREDEFINED_TEMPLATE', 'HTTP_ENDPOINT', 'PORT_CHECK', 'LOG_PARSER', 'CUSTOM_COMMAND'))
);

-- ==========================================
-- Metric Groups (per-server, own a default execution mode + interval)
-- ==========================================
CREATE TABLE metric_groups (
    id                       BIGSERIAL PRIMARY KEY,
    server_id                BIGINT       NOT NULL REFERENCES servers (id) ON DELETE CASCADE,
    name                     VARCHAR(150) NOT NULL,
    default_execution_mode   VARCHAR(20)  NOT NULL DEFAULT 'PERIODIC',
    interval_seconds         INT          NOT NULL DEFAULT 15,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_metric_groups_mode CHECK (default_execution_mode IN ('PERIODIC', 'ON_DEMAND')),
    CONSTRAINT chk_metric_groups_interval CHECK (interval_seconds > 0),
    CONSTRAINT uq_metric_groups_server_name UNIQUE (server_id, name)
);

-- ==========================================
-- Metric Group Items (join: which definitions belong to which group)
-- ==========================================
CREATE TABLE metric_group_items (
    id                        BIGSERIAL PRIMARY KEY,
    group_id                  BIGINT    NOT NULL REFERENCES metric_groups (id) ON DELETE CASCADE,
    metric_definition_id      BIGINT    NOT NULL REFERENCES metric_definitions (id) ON DELETE RESTRICT,
    override_execution_mode   VARCHAR(20),
    last_run_at               TIMESTAMP,
    created_at                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_metric_group_items_mode CHECK (override_execution_mode IS NULL OR override_execution_mode IN ('PERIODIC', 'ON_DEMAND')),
    CONSTRAINT uq_metric_group_items_pair UNIQUE (group_id, metric_definition_id)
);

-- ==========================================
-- Custom Metric Logs (ingested results, periodic or on-demand)
-- ==========================================
CREATE TABLE custom_metric_logs (
    id              BIGSERIAL PRIMARY KEY,
    server_id       BIGINT       NOT NULL REFERENCES servers (id) ON DELETE CASCADE,
    group_id        BIGINT       REFERENCES metric_groups (id) ON DELETE SET NULL,
    group_item_id   BIGINT       REFERENCES metric_group_items (id) ON DELETE SET NULL,
    metric_key      VARCHAR(100) NOT NULL,
    success         BOOLEAN      NOT NULL,
    result_payload  TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ==========================================
-- Metric Fetch Requests ("Fetch Now" poll/wait tracking)
-- ==========================================
CREATE TABLE metric_fetch_requests (
    id                     BIGSERIAL PRIMARY KEY,
    group_id               BIGINT      NOT NULL REFERENCES metric_groups (id) ON DELETE CASCADE,
    server_id              BIGINT      NOT NULL REFERENCES servers (id) ON DELETE CASCADE,
    requested_by_user_id   BIGINT      REFERENCES users (id),
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at           TIMESTAMP,
    result_payload         TEXT,
    CONSTRAINT chk_fetch_requests_status CHECK (status IN ('PENDING', 'COMPLETED', 'TIMED_OUT'))
);

CREATE INDEX idx_metric_definitions_type      ON metric_definitions (type);
CREATE INDEX idx_metric_groups_server_id      ON metric_groups (server_id);
CREATE INDEX idx_metric_group_items_group_id  ON metric_group_items (group_id);
CREATE INDEX idx_custom_metric_logs_server    ON custom_metric_logs (server_id, created_at);
CREATE INDEX idx_custom_metric_logs_group     ON custom_metric_logs (group_id, created_at);
CREATE INDEX idx_fetch_requests_server_status ON metric_fetch_requests (server_id, status);
