-- ==========================================
-- AIOps modulunu kategori bazli sahte switch'ler (track_cpu/track_memory/...)
-- yerine gercek metric_groups tablosuna baglar: hangi metrik gruplarinin
-- yerel LLM tarafindan izlenecegini kullanici artik gercek grup kimlikleriyle
-- seciyor. Bos secim = hicbir arka plan gorevi calismaz (AIOpsService).
-- ==========================================
ALTER TABLE aiops_config
    DROP COLUMN track_cpu,
    DROP COLUMN track_memory,
    DROP COLUMN track_disk,
    DROP COLUMN track_database,
    DROP COLUMN track_logs;

CREATE TABLE aiops_tracked_groups (
    config_id BIGINT NOT NULL REFERENCES aiops_config(id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL REFERENCES metric_groups(id) ON DELETE CASCADE,
    PRIMARY KEY (config_id, group_id)
);
