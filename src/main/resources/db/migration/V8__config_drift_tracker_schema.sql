-- ==========================================
-- Configuration Drift Tracker
-- ==========================================
CREATE TABLE tracked_config_files (
    id                      BIGSERIAL PRIMARY KEY,
    server_id               BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    file_path               VARCHAR(500) NOT NULL,
    file_label              VARCHAR(150) NOT NULL,
    check_interval_seconds  INT NOT NULL DEFAULT 60,
    last_checked_at         TIMESTAMP,
    current_hash            VARCHAR(64),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_by              VARCHAR(100) NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_tracked_config_status CHECK (status IN ('PENDING','IN_SYNC','DRIFT_DETECTED','FILE_NOT_FOUND','PERMISSION_DENIED')),
    CONSTRAINT chk_tracked_config_interval CHECK (check_interval_seconds > 0),
    CONSTRAINT uq_tracked_config_server_path UNIQUE (server_id, file_path)
);

CREATE TABLE config_file_history (
    id               BIGSERIAL PRIMARY KEY,
    tracked_file_id  BIGINT NOT NULL REFERENCES tracked_config_files(id) ON DELETE CASCADE,
    version_number   INT NOT NULL,
    file_content     TEXT NOT NULL,
    file_hash        VARCHAR(64) NOT NULL,
    diff_summary     VARCHAR(200),
    captured_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_config_file_history_version UNIQUE (tracked_file_id, version_number)
);

CREATE INDEX idx_tracked_config_files_server ON tracked_config_files (server_id);
CREATE INDEX idx_config_file_history_tracked ON config_file_history (tracked_file_id, version_number);
