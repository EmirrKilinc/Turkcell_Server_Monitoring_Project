-- ==========================================
-- Default command allowlist - one row per unique command string used by the
-- V5 seed catalog (free -m is reused by two metric definitions, so it's
-- seeded once here, not twice). OPERATOR-created CUSTOM_COMMAND metric
-- definitions must exactly match one of these command_template values;
-- ADMIN is exempt (see MetricDefinitionService).
-- ==========================================

INSERT INTO command_allowlist (command_template, description, category, created_by) VALUES
('cat /proc/loadavg', 'Sistem yuk ortalamasini okur (1/5/15 dk)', 'SYSTEM', 'system'),
('cat /proc/uptime', 'Sistemin acik kaldigi sureyi okur', 'SYSTEM', 'system'),
('cat /proc/meminfo', 'Detayli bellek kullanim bilgisini okur', 'MEMORY', 'system'),
('free -m', 'Toplam/kullanilan/bos RAM ve swap bilgisini MB cinsinden okur', 'MEMORY', 'system'),
('df -h /', 'Kok dosya sistemi disk kullanimini okur', 'DISK', 'system'),
('df -h /data01', 'Veri bolumu disk kullanimini okur', 'DISK', 'system'),
('df -i /', 'Kok bolumdeki inode kullanimini okur', 'DISK', 'system'),
('ps -e --no-headers', 'Calisan tum sureclerin listesini okur (sayim icin)', 'PROCESS', 'system'),
('ps -eo pid,ppid,cmd,%mem,%cpu --sort=-%cpu', 'Surecleri CPU kullanimina gore azalan sirada listeler', 'PROCESS', 'system'),
('ps -eo pid,ppid,cmd,%mem,%cpu --sort=-%mem', 'Surecleri bellek kullanimina gore azalan sirada listeler', 'PROCESS', 'system'),
('ps -eo stat', 'Tum sureclerin durum kodlarini listeler (zombi tespiti icin)', 'PROCESS', 'system'),
('nproc', 'Kullanilabilir CPU cekirdek sayisini okur', 'SYSTEM', 'system'),
('uname -r', 'Calisan kernel surumunu okur', 'SYSTEM', 'system'),
('cat /etc/os-release', 'Isletim sistemi dagitim ve surum bilgisini okur', 'SYSTEM', 'system'),
('cat /proc/net/dev', 'Ag arabirimi trafik sayaclarini okur', 'NETWORK', 'system'),
('cat /proc/net/sockstat', 'TCP/UDP soket istatistiklerini okur', 'NETWORK', 'system'),
('cat /proc/sys/fs/file-nr', 'Sistem genelinde ayrilan dosya tanimlayici sayisini okur', 'SYSTEM', 'system');
