-- ==========================================
-- Hazir metrik katalogu (kullanicilar kod yazmadan gruplara ekleyebilir).
-- command_payload, tipe gore alanlar tasiyan bir JSON metnidir:
--   HTTP_ENDPOINT   -> {url, method, timeoutSeconds, expectedStatus, extractPattern?, valueType}
--   CUSTOM_COMMAND  -> {command, timeoutSeconds, extractPattern?, valueType}
--   LOG_PARSER      -> {filePath, pattern, tailBytes, valueType}
--   PREDEFINED_TEMPLATE -> agent tarafinda metric_key'e gore sabit bir fonksiyona yonlendirilir
-- CUSTOM_COMMAND satirlari (Postgres/Redis/Docker) hedef sunucuda ilgili
-- istemci binary'sinin (psql/redis-cli/docker) kurulu olmasini varsayar -
-- bootstrap-target-host.sh bunlari kurmaz, agent eksik binary'de hata
-- metniyle basarisiz olur ama agent dongusu asla cokmez.
-- ==========================================

INSERT INTO metric_definitions (name, metric_key, category, type, command_payload, is_custom_command, description) VALUES
('Nginx Aktif Baglanti Sayisi', 'nginx_active_connections', 'WEB_SERVER', 'HTTP_ENDPOINT',
 '{"url":"http://127.0.0.1/nginx_status","method":"GET","timeoutSeconds":3,"expectedStatus":200,"extractPattern":"Active connections:\\s*(\\d+)","valueType":"number"}',
 FALSE, 'Nginx stub_status modulunden aktif baglanti sayisini okur (stub_status ayarli olmalidir).'),

('PostgreSQL Aktif Baglanti Sayisi', 'pg_active_connections', 'DATABASE', 'CUSTOM_COMMAND',
 '{"command":"psql -h 127.0.0.1 -U postgres -tAc \"SELECT count(*) FROM pg_stat_activity;\"","timeoutSeconds":5,"valueType":"number"}',
 TRUE, 'pg_stat_activity uzerinden aktif baglanti sayisini sorgular. Hedefte psql istemcisi gereklidir.'),

('PostgreSQL Veritabani Boyutu', 'pg_database_size', 'DATABASE', 'CUSTOM_COMMAND',
 '{"command":"psql -h 127.0.0.1 -U postgres -tAc \"SELECT pg_database_size(current_database());\"","timeoutSeconds":5,"valueType":"number"}',
 TRUE, 'Guncel veritabaninin boyutunu (byte) sorgular. Hedefte psql istemcisi gereklidir.'),

('Redis Bellek Kullanimi', 'redis_memory_usage', 'CACHE', 'CUSTOM_COMMAND',
 '{"command":"redis-cli -h 127.0.0.1 INFO memory","timeoutSeconds":5,"extractPattern":"used_memory:(\\d+)","valueType":"number"}',
 TRUE, 'redis-cli INFO memory ciktisindan used_memory degerini okur. Hedefte redis-cli istemcisi gereklidir.'),

('Uygulama Saglik Kontrolu', 'app_health_check', 'APPLICATION', 'HTTP_ENDPOINT',
 '{"url":"http://127.0.0.1:8081/health","method":"GET","timeoutSeconds":3,"expectedStatus":200,"valueType":"bool"}',
 FALSE, 'Uygulamanin /health endpointine GET istegi atar, 200 donup donmedigini kontrol eder.'),

('Log Dosyasi Hata Sayaci', 'log_error_counter', 'LOGS', 'LOG_PARSER',
 '{"filePath":"/var/log/app/app.log","pattern":"ERROR","tailBytes":1048576,"valueType":"number"}',
 FALSE, 'Belirtilen log dosyasinin son kismini (varsayilan 1MB) tarayip ERROR gecen satir sayisini dondurur.'),

('Disk G/C ve Kullanim Alani', 'disk_io_and_space', 'SYSTEM', 'PREDEFINED_TEMPLATE',
 '{"path":"/"}',
 FALSE, 'Agent icindeki psutil tabanli hazir fonksiyon ile disk kullanimi ve G/C sayaclarini okur.'),

('Docker Calisan Container Sayisi', 'docker_running_containers', 'CONTAINERS', 'CUSTOM_COMMAND',
 '{"command":"docker ps -q","timeoutSeconds":5,"valueType":"lineCount"}',
 TRUE, 'docker ps -q ciktisindaki satir sayisini dondurur. Hedefte docker CLI ve monitoring_user erisimi gereklidir.');
