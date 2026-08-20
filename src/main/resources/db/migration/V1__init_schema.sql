-- ==========================================
-- Users (JWT auth + admin approval gate)
-- ==========================================
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'VIEWER',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_role   CHECK (role IN ('ADMIN', 'OPERATOR', 'VIEWER')),
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- ==========================================
-- Servers (provisioned via zero-persistence SSH)
-- ==========================================
CREATE TABLE servers (
    id                    BIGSERIAL PRIMARY KEY,
    hostname              VARCHAR(150) NOT NULL UNIQUE,
    ip_address            VARCHAR(45)  NOT NULL,
    ssh_username          VARCHAR(100) NOT NULL,
    secret_key_encrypted  VARCHAR(500) NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    added_by_user_id      BIGINT REFERENCES users (id),
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_servers_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

-- ==========================================
-- Metrics (HMAC-signed ingestion)
-- ==========================================
CREATE TABLE metrics (
    id           BIGSERIAL PRIMARY KEY,
    server_id    BIGINT REFERENCES servers (id) ON DELETE SET NULL,
    hostname     VARCHAR(150) NOT NULL,
    cpu_percent  REAL         NOT NULL,
    ram_percent  REAL         NOT NULL,
    disk_percent REAL         NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_metrics_hostname   ON metrics (hostname);
CREATE INDEX idx_metrics_created_at ON metrics (created_at);
CREATE INDEX idx_metrics_server_id  ON metrics (server_id);
CREATE INDEX idx_servers_status     ON servers (status);
CREATE INDEX idx_users_status       ON users (status);
