-- ==========================================
-- Pure command-based metrics refactor: every metric is now a shell command
-- with structured fields (command/timeout_seconds/value_type/extract_pattern)
-- instead of a freeform JSON command_payload blob + type discriminator.
-- The command whitelist is removed entirely - the existing approval
-- workflow (approval_status) is now the sole safety gate for
-- OPERATOR-authored commands. Full reseed, same precedent as V5: no
-- existing test/environment depends on the old seeded rows surviving.
-- ==========================================

-- metric_group_items.metric_definition_id is ON DELETE RESTRICT, so
-- existing group attachments must go first. custom_metric_logs.group_item_id
-- is ON DELETE SET NULL, so historical logs are untouched.
DELETE FROM metric_group_items;
DELETE FROM metric_definitions;

DROP TABLE command_allowlist;

ALTER TABLE metric_definitions DROP CONSTRAINT chk_metric_definitions_type;
ALTER TABLE metric_definitions
    DROP COLUMN type,
    DROP COLUMN command_payload,
    DROP COLUMN is_custom_command;

ALTER TABLE metric_definitions
    ADD COLUMN command          VARCHAR(1000) NOT NULL,
    ADD COLUMN timeout_seconds  INT           NOT NULL DEFAULT 3,
    ADD COLUMN value_type       VARCHAR(20)   NOT NULL DEFAULT 'RAW',
    ADD COLUMN extract_pattern  VARCHAR(300);

ALTER TABLE metric_definitions
    ADD CONSTRAINT chk_metric_definitions_value_type
    CHECK (value_type IN ('RAW', 'NUMERIC', 'LINE_COUNT', 'MATCH_COUNT'));

-- Re-seed the same 20 metrics, translated 1:1 into the new structured
-- shape. The 2 former HTTP_ENDPOINT rows (app_health_status/app_info)
-- become curl commands - "curl -sf" fails non-zero on a non-2xx response
-- (equivalent to the old expectedStatus:200 check) and reads the body as
-- the old code did.
INSERT INTO metric_definitions
    (name, metric_key, category, command, timeout_seconds, value_type, extract_pattern, description,
     approval_status, created_by, approved_by, approved_at) VALUES

('Sistem Yuk Ortalamasi', 'system_loadavg', 'SYSTEM', 'cat /proc/loadavg', 3, 'RAW', NULL,
 '/proc/loadavg dosyasini okur, 1/5/15 dakikalik yuk ortalamalarini ve calisan/toplam surec sayisini icerir.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Sistem Calisma Suresi', 'system_uptime', 'SYSTEM', 'cat /proc/uptime', 3, 'NUMERIC', '^([0-9.]+)',
 '/proc/uptime dosyasindan sistemin acik kaldigi sureyi saniye cinsinden okur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Toplam ve Bos RAM', 'mem_info', 'MEMORY', 'cat /proc/meminfo', 3, 'RAW', NULL,
 '/proc/meminfo dosyasinin tamamini okur (MemTotal, MemFree, Buffers, Cached vb.).',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Kullanilabilir Bellek (MB)', 'mem_available_mb', 'MEMORY', 'free -m', 3, 'NUMERIC',
 'Mem:\s+\d+\s+\d+\s+\d+\s+\d+\s+\d+\s+(\d+)',
 '''free -m'' ciktisindan Mem satirinin ''available'' sutununu MB cinsinden okur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Swap Bellek Kullanimi (MB)', 'swap_usage_mb', 'MEMORY', 'free -m', 3, 'NUMERIC',
 'Swap:\s+\d+\s+(\d+)',
 '''free -m'' ciktisindan Swap satirinin ''used'' sutununu MB cinsinden okur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Kok Dosya Sistemi Disk Alani', 'disk_usage_root', 'DISK', 'df -h /', 3, 'RAW', NULL,
 '''df -h /'' komutuyla kok bolumun disk kullanimini okur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Veri Bolumu Disk Alani', 'disk_usage_data', 'DISK', 'df -h /data01', 3, 'RAW', NULL,
 '''df -h /data01'' komutuyla veri bolumunun disk kullanimini okur (bolum yoksa hata mesajiyla basarisiz olur, agent dongusu cokmez).',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Disk Inode Kullanimi', 'disk_inode_usage', 'DISK', 'df -i /', 3, 'RAW', NULL,
 '''df -i /'' komutuyla kok bolumdeki inode kullanim oranini okur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Toplam Calisan Surec Sayisi', 'total_processes', 'PROCESS', 'ps -e --no-headers', 5, 'LINE_COUNT', NULL,
 '''ps -e --no-headers'' ciktisindaki satir sayisini (toplam surec sayisi) dondurur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('En Cok CPU Tuketen Surecler', 'top_cpu_processes', 'PROCESS', 'ps -eo pid,ppid,cmd,%mem,%cpu --sort=-%cpu', 5, 'RAW', NULL,
 'Tum surecleri CPU kullanimina gore azalan sirada listeler (pid, ppid, komut, %mem, %cpu); en ust satirlar en yuksek tuketenlerdir.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('En Cok Bellek Tuketen Surecler', 'top_mem_processes', 'PROCESS', 'ps -eo pid,ppid,cmd,%mem,%cpu --sort=-%mem', 5, 'RAW', NULL,
 'Tum surecleri bellek kullanimina gore azalan sirada listeler (pid, ppid, komut, %mem, %cpu); en ust satirlar en yuksek tuketenlerdir.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Zombi Surec Sayisi', 'zombie_processes', 'PROCESS', 'ps -eo stat', 5, 'MATCH_COUNT', '^Z',
 '''ps -eo stat'' ciktisinda durumu Z (zombie) ile baslayan satir sayisini sayar.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('CPU Cekirdek Sayisi', 'cpu_cores_info', 'SYSTEM', 'nproc', 3, 'NUMERIC', NULL,
 '''nproc'' komutuyla kullanilabilir islemci cekirdegi sayisini okur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Isletim Sistemi Cekirdek Surumu', 'os_kernel_version', 'SYSTEM', 'uname -r', 3, 'RAW', NULL,
 '''uname -r'' komutuyla calisan kernel surumunu okur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Sunucu ve Isletim Sistemi Bilgisi', 'os_release_info', 'SYSTEM', 'cat /etc/os-release', 3, 'RAW', NULL,
 '/etc/os-release dosyasini okuyarak dagitim adi ve surum bilgilerini dondurur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Ag Arabirimleri Durumu', 'network_interfaces', 'NETWORK', 'cat /proc/net/dev', 3, 'RAW', NULL,
 '/proc/net/dev dosyasini okuyarak her ag arabiriminin gonderilen/alinan byte ve paket sayaclarini dondurur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('TCP Baglanti Istatistikleri', 'tcp_socket_stats', 'NETWORK', 'cat /proc/net/sockstat', 3, 'RAW', NULL,
 '/proc/net/sockstat dosyasini okuyarak aktif soket/baglanti sayilarini dondurur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Acik Dosya Tanimlayici Sayisi', 'system_file_nr', 'SYSTEM', 'cat /proc/sys/fs/file-nr', 3, 'NUMERIC', '^([0-9]+)',
 '/proc/sys/fs/file-nr dosyasindan sistem genelinde ayrilan (allocated) dosya tanimlayici sayisini okur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Spring Boot Uygulama Saglik Kontrolu', 'app_health_status', 'APPLICATION',
 'curl -sf http://localhost:8080/actuator/health', 3, 'RAW', NULL,
 'Hedef uygulamanin /actuator/health endpointine istek atar; 2xx disinda bir durum donerse basarisiz olur, govde icerigini okur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP),

('Spring Boot Uygulama Bilgisi', 'app_info', 'APPLICATION',
 'curl -sf http://localhost:8080/actuator/info', 3, 'RAW', NULL,
 'Hedef uygulamanin /actuator/info endpointinden uygulama meta verilerini okur.',
 'APPROVED', 'system', 'system', CURRENT_TIMESTAMP);
