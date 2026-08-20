-- ==========================================
-- AIOps & Sistem Analiz Modulu.
-- aiops_config: tekil (singleton, id=1) ayar satiri - hangi metrik
-- gruplarinin yerel LLM tarafindan izlendigi + e-posta alarm ayarlari.
-- aiops_anomalies: Ollama tarafindan uretilen canli anomali / RCA / gunluk
-- ozet kartlari (AIOpsScheduler tarafindan periyodik olarak yazilir).
-- ==========================================
CREATE TABLE aiops_config (
    id BIGINT PRIMARY KEY,
    track_cpu BOOLEAN NOT NULL DEFAULT TRUE,
    track_memory BOOLEAN NOT NULL DEFAULT TRUE,
    track_disk BOOLEAN NOT NULL DEFAULT TRUE,
    track_database BOOLEAN NOT NULL DEFAULT TRUE,
    track_logs BOOLEAN NOT NULL DEFAULT FALSE,
    alert_email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    alert_email VARCHAR(200),
    daily_summary_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO aiops_config (id, track_cpu, track_memory, track_disk, track_database, track_logs,
                           alert_email_enabled, alert_email, daily_summary_enabled, updated_at)
VALUES (1, TRUE, TRUE, TRUE, TRUE, FALSE, FALSE, NULL, TRUE, CURRENT_TIMESTAMP);

CREATE TABLE aiops_anomalies (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(30) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_aiops_anomalies_created_at ON aiops_anomalies(created_at DESC);
CREATE INDEX idx_aiops_anomalies_category ON aiops_anomalies(category, created_at DESC);
