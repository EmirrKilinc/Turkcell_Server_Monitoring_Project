-- ==========================================
-- Replaces the original 8-item seed catalog (V3) with 20 metrics verified to
-- run under an unprivileged monitoring_user using only /proc, ps, df, free,
-- nproc, uname and the target application's own actuator endpoints - no
-- optional client binaries (psql/redis-cli/docker) required.
--
-- command_payload keeps the same JSON shape as before:
--   HTTP_ENDPOINT   -> {url, method, timeoutSeconds, expectedStatus, extractPattern?, valueType?}
--   CUSTOM_COMMAND  -> {command, timeoutSeconds, extractPattern?, valueType?}
-- valueType (CUSTOM_COMMAND): raw (default, full trimmed stdout) | number |
-- lineCount | matchCount (counts extractPattern matches, e.g. zombie states).
-- ==========================================

-- Group items referencing the old catalog would block the FK
-- (metric_group_items.metric_definition_id is ON DELETE RESTRICT) - clean
-- those first. No existing test depends on the old seeded rows.
DELETE FROM metric_group_items WHERE metric_definition_id IN (
    SELECT id FROM metric_definitions WHERE metric_key IN (
        'nginx_active_connections', 'pg_active_connections', 'pg_database_size',
        'redis_memory_usage', 'app_health_check', 'log_error_counter',
        'disk_io_and_space', 'docker_running_containers'
    )
);

DELETE FROM metric_definitions WHERE metric_key IN (
    'nginx_active_connections', 'pg_active_connections', 'pg_database_size',
    'redis_memory_usage', 'app_health_check', 'log_error_counter',
    'disk_io_and_space', 'docker_running_containers'
);

INSERT INTO metric_definitions (name, metric_key, category, type, command_payload, is_custom_command, description) VALUES

('Sistem Yuk Ortalamasi', 'system_loadavg', 'SYSTEM', 'CUSTOM_COMMAND',
 '{"command":"cat /proc/loadavg","timeoutSeconds":3}',
 TRUE, '/proc/loadavg dosyasini okur, 1/5/15 dakikalik yuk ortalamalarini ve calisan/toplam surec sayisini icerir.'),

('Sistem Calisma Suresi', 'system_uptime', 'SYSTEM', 'CUSTOM_COMMAND',
 '{"command":"cat /proc/uptime","timeoutSeconds":3,"extractPattern":"^([0-9.]+)","valueType":"number"}',
 TRUE, '/proc/uptime dosyasindan sistemin acik kaldigi sureyi saniye cinsinden okur.'),

('Toplam ve Bos RAM', 'mem_info', 'MEMORY', 'CUSTOM_COMMAND',
 '{"command":"cat /proc/meminfo","timeoutSeconds":3}',
 TRUE, '/proc/meminfo dosyasinin tamamini okur (MemTotal, MemFree, Buffers, Cached vb.).'),

('Kullanilabilir Bellek (MB)', 'mem_available_mb', 'MEMORY', 'CUSTOM_COMMAND',
 '{"command":"free -m","timeoutSeconds":3,"extractPattern":"Mem:\\s+\\d+\\s+\\d+\\s+\\d+\\s+\\d+\\s+\\d+\\s+(\\d+)","valueType":"number"}',
 TRUE, '''free -m'' ciktisindan Mem satirinin ''available'' sutununu MB cinsinden okur.'),

('Swap Bellek Kullanimi (MB)', 'swap_usage_mb', 'MEMORY', 'CUSTOM_COMMAND',
 '{"command":"free -m","timeoutSeconds":3,"extractPattern":"Swap:\\s+\\d+\\s+(\\d+)","valueType":"number"}',
 TRUE, '''free -m'' ciktisindan Swap satirinin ''used'' sutununu MB cinsinden okur.'),

('Kok Dosya Sistemi Disk Alani', 'disk_usage_root', 'DISK', 'CUSTOM_COMMAND',
 '{"command":"df -h /","timeoutSeconds":3}',
 TRUE, '''df -h /'' komutuyla kok bolumun disk kullanimini okur.'),

('Veri Bolumu Disk Alani', 'disk_usage_data', 'DISK', 'CUSTOM_COMMAND',
 '{"command":"df -h /data01","timeoutSeconds":3}',
 TRUE, '''df -h /data01'' komutuyla veri bolumunun disk kullanimini okur (bolum yoksa hata mesajiyla basarisiz olur, agent dongusu cokmez).'),

('Disk Inode Kullanimi', 'disk_inode_usage', 'DISK', 'CUSTOM_COMMAND',
 '{"command":"df -i /","timeoutSeconds":3}',
 TRUE, '''df -i /'' komutuyla kok bolumdeki inode kullanim oranini okur.'),

('Toplam Calisan Surec Sayisi', 'total_processes', 'PROCESS', 'CUSTOM_COMMAND',
 '{"command":"ps -e --no-headers","timeoutSeconds":5,"valueType":"lineCount"}',
 TRUE, '''ps -e --no-headers'' ciktisindaki satir sayisini (toplam surec sayisi) dondurur.'),

('En Cok CPU Tuketen Surecler', 'top_cpu_processes', 'PROCESS', 'CUSTOM_COMMAND',
 '{"command":"ps -eo pid,ppid,cmd,%mem,%cpu --sort=-%cpu","timeoutSeconds":5}',
 TRUE, 'Tum surecleri CPU kullanimina gore azalan sirada listeler (pid, ppid, komut, %mem, %cpu); en ust satirlar en yuksek tuketenlerdir.'),

('En Cok Bellek Tuketen Surecler', 'top_mem_processes', 'PROCESS', 'CUSTOM_COMMAND',
 '{"command":"ps -eo pid,ppid,cmd,%mem,%cpu --sort=-%mem","timeoutSeconds":5}',
 TRUE, 'Tum surecleri bellek kullanimina gore azalan sirada listeler (pid, ppid, komut, %mem, %cpu); en ust satirlar en yuksek tuketenlerdir.'),

('Zombi Surec Sayisi', 'zombie_processes', 'PROCESS', 'CUSTOM_COMMAND',
 '{"command":"ps -eo stat","timeoutSeconds":5,"extractPattern":"^Z","valueType":"matchCount"}',
 TRUE, '''ps -eo stat'' ciktisinda durumu Z (zombie) ile baslayan satir sayisini sayar.'),

('CPU Cekirdek Sayisi', 'cpu_cores_info', 'SYSTEM', 'CUSTOM_COMMAND',
 '{"command":"nproc","timeoutSeconds":3,"valueType":"number"}',
 TRUE, '''nproc'' komutuyla kullanilabilir islemci cekirdegi sayisini okur.'),

('Isletim Sistemi Cekirdek Surumu', 'os_kernel_version', 'SYSTEM', 'CUSTOM_COMMAND',
 '{"command":"uname -r","timeoutSeconds":3}',
 TRUE, '''uname -r'' komutuyla calisan kernel surumunu okur.'),

('Sunucu ve Isletim Sistemi Bilgisi', 'os_release_info', 'SYSTEM', 'CUSTOM_COMMAND',
 '{"command":"cat /etc/os-release","timeoutSeconds":3}',
 TRUE, '/etc/os-release dosyasini okuyarak dagitim adi ve surum bilgilerini dondurur.'),

('Ag Arabirimleri Durumu', 'network_interfaces', 'NETWORK', 'CUSTOM_COMMAND',
 '{"command":"cat /proc/net/dev","timeoutSeconds":3}',
 TRUE, '/proc/net/dev dosyasini okuyarak her ag arabiriminin gonderilen/alinan byte ve paket sayaclarini dondurur.'),

('TCP Baglanti Istatistikleri', 'tcp_socket_stats', 'NETWORK', 'CUSTOM_COMMAND',
 '{"command":"cat /proc/net/sockstat","timeoutSeconds":3}',
 TRUE, '/proc/net/sockstat dosyasini okuyarak aktif soket/baglanti sayilarini dondurur.'),

('Acik Dosya Tanimlayici Sayisi', 'system_file_nr', 'SYSTEM', 'CUSTOM_COMMAND',
 '{"command":"cat /proc/sys/fs/file-nr","timeoutSeconds":3,"extractPattern":"^([0-9]+)","valueType":"number"}',
 TRUE, '/proc/sys/fs/file-nr dosyasindan sistem genelinde ayrilan (allocated) dosya tanimlayici sayisini okur.'),

('Spring Boot Uygulama Saglik Kontrolu', 'app_health_status', 'APPLICATION', 'HTTP_ENDPOINT',
 '{"url":"http://localhost:8080/actuator/health","method":"GET","timeoutSeconds":3,"expectedStatus":200}',
 FALSE, 'Hedef uygulamanin /actuator/health endpointine GET istegi atar, 200 donup donmedigini ve govde icerigini kontrol eder.'),

('Spring Boot Uygulama Bilgisi', 'app_info', 'APPLICATION', 'HTTP_ENDPOINT',
 '{"url":"http://localhost:8080/actuator/info","method":"GET","timeoutSeconds":3,"expectedStatus":200}',
 FALSE, 'Hedef uygulamanin /actuator/info endpointinden uygulama meta verilerini okur.');
