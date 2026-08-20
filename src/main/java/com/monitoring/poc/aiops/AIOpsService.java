package com.monitoring.poc.aiops;

import com.monitoring.poc.aiops.dto.AIOpsChatRequest;
import com.monitoring.poc.aiops.dto.AIOpsChatResponse;
import com.monitoring.poc.aiops.dto.AIOpsConfigDto;
import com.monitoring.poc.aiops.dto.AnomalyCardDto;
import com.monitoring.poc.aiops.dto.AvailableMetricGroupDto;
import com.monitoring.poc.email.EmailService;
import com.monitoring.poc.entity.AiOpsAnomaly;
import com.monitoring.poc.entity.AiOpsConfig;
import com.monitoring.poc.entity.CustomMetricLog;
import com.monitoring.poc.entity.MetricEntity;
import com.monitoring.poc.entity.MetricGroup;
import com.monitoring.poc.enums.AiOpsAnomalyCategory;
import com.monitoring.poc.enums.AiOpsSeverity;
import com.monitoring.poc.repository.AiOpsAnomalyRepository;
import com.monitoring.poc.repository.AiOpsConfigRepository;
import com.monitoring.poc.repository.CustomMetricLogRepository;
import com.monitoring.poc.repository.MetricGroupRepository;
import com.monitoring.poc.repository.MetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Business logic for the AIOps module: turns raw metric/log rows into
 * Ollama prompts, persists the resulting anomaly/RCA/daily-summary cards,
 * and answers the embedded chat. Analysis methods here are also invoked
 * directly by {@link AIOpsScheduler} on a fixed cadence.
 *
 * <p>Everything is scoped to the real {@link MetricGroup}s the user has
 * chosen to track ({@link AiOpsConfig#getTrackedGroupIds()}) - if that set
 * is empty, no background job runs and the chat says so instead of
 * fabricating an answer from unrelated data.
 */
@Service
public class AIOpsService {

    private static final Logger log = LoggerFactory.getLogger(AIOpsService.class);

    private final OllamaService ollamaService;
    private final MetricRepository metricRepository;
    private final MetricGroupRepository metricGroupRepository;
    private final CustomMetricLogRepository customMetricLogRepository;
    private final AiOpsConfigRepository aiOpsConfigRepository;
    private final AiOpsAnomalyRepository aiOpsAnomalyRepository;
    private final EmailService emailService;

    private final double criticalCpuThreshold;
    private final double criticalRamThreshold;
    private final double criticalDiskThreshold;

    public AIOpsService(OllamaService ollamaService,
                         MetricRepository metricRepository,
                         MetricGroupRepository metricGroupRepository,
                         CustomMetricLogRepository customMetricLogRepository,
                         AiOpsConfigRepository aiOpsConfigRepository,
                         AiOpsAnomalyRepository aiOpsAnomalyRepository,
                         EmailService emailService,
                         @Value("${app.aiops.critical-cpu-threshold:90}") double criticalCpuThreshold,
                         @Value("${app.aiops.critical-ram-threshold:90}") double criticalRamThreshold,
                         @Value("${app.aiops.critical-disk-threshold:90}") double criticalDiskThreshold) {
        this.ollamaService = ollamaService;
        this.metricRepository = metricRepository;
        this.metricGroupRepository = metricGroupRepository;
        this.customMetricLogRepository = customMetricLogRepository;
        this.aiOpsConfigRepository = aiOpsConfigRepository;
        this.aiOpsAnomalyRepository = aiOpsAnomalyRepository;
        this.emailService = emailService;
        this.criticalCpuThreshold = criticalCpuThreshold;
        this.criticalRamThreshold = criticalRamThreshold;
        this.criticalDiskThreshold = criticalDiskThreshold;
    }

    /* ============================== Chat ============================== */

    @Transactional
    public AIOpsChatResponse chat(AIOpsChatRequest request) {
        String systemContext = buildSystemContext(request.getScopeGroupId());
        String reply = ollamaService.generate(request.getMessage(), systemContext);
        return new AIOpsChatResponse(reply);
    }

    /* ============================ Anomalies ============================ */

    public List<AnomalyCardDto> getAnomalies() {
        return aiOpsAnomalyRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(AnomalyCardDto::from)
                .toList();
    }

    /* ======================= Available metric groups ====================== */

    public List<AvailableMetricGroupDto> getAvailableGroups() {
        return metricGroupRepository.findAll().stream()
                .sorted(Comparator.comparing((MetricGroup g) -> g.getServer().getHostname())
                        .thenComparing(MetricGroup::getName))
                .map(AvailableMetricGroupDto::from)
                .toList();
    }

    /* ============================= Config =============================== */

    @Transactional
    public AIOpsConfigDto getConfig() {
        return AIOpsConfigDto.from(loadOrCreateConfig());
    }

    @Transactional
    public AIOpsConfigDto saveConfig(AIOpsConfigDto dto) {
        AiOpsConfig config = aiOpsConfigRepository.findById(AiOpsConfig.SINGLETON_ID)
                .orElseGet(AiOpsConfig::new);
        config.setId(AiOpsConfig.SINGLETON_ID);

        Set<Long> groupIds = dto.getTrackedGroupIds() != null ? new LinkedHashSet<>(dto.getTrackedGroupIds()) : Set.of();
        config.getTrackedGroupIds().clear();
        config.getTrackedGroupIds().addAll(groupIds);

        config.setAlertEmailEnabled(dto.isAlertEmailEnabled());
        config.setAlertEmail(dto.getAlertEmail());
        config.setDailySummaryEnabled(dto.isDailySummaryEnabled());
        config.setUpdatedAt(LocalDateTime.now());

        AiOpsConfig saved = aiOpsConfigRepository.save(config);
        log.info("[AIOPS CONFIG] Ayarlar guncellendi. IzlenenGrupSayisi={}, AlertEmailEnabled={}, DailySummaryEnabled={}",
                saved.getTrackedGroupIds().size(), saved.getAlertEmailEnabled(), saved.getDailySummaryEnabled());
        return AIOpsConfigDto.from(saved);
    }

    private AiOpsConfig loadOrCreateConfig() {
        return aiOpsConfigRepository.findById(AiOpsConfig.SINGLETON_ID)
                .orElseGet(() -> {
                    AiOpsConfig config = new AiOpsConfig();
                    config.setId(AiOpsConfig.SINGLETON_ID);
                    config.setAlertEmailEnabled(false);
                    config.setAlertEmail(null);
                    config.setDailySummaryEnabled(true);
                    config.setUpdatedAt(LocalDateTime.now());
                    return aiOpsConfigRepository.save(config);
                });
    }

    /* ==================== Scheduler-driven analysis jobs ==================== */

    /**
     * Coklu metrik korelasyonu: izlenmesi secilen gruplarin sunucularindaki
     * en guncel CPU/RAM/Disk okumalarini toplar ve Ollama'ya tek bir anomali
     * ozeti yorumlatir. Hicbir grup secili degilse hicbir sey yapmaz.
     */
    @Transactional
    public void runCorrelationAnalysis() {
        Set<Long> tracked = loadOrCreateConfig().getTrackedGroupIds();
        if (tracked.isEmpty()) {
            return;
        }
        List<String> hostnames = hostnamesForGroups(tracked);
        if (hostnames.isEmpty()) {
            return;
        }

        StringBuilder metricsSummary = new StringBuilder();
        for (String host : hostnames) {
            MetricEntity latest = metricRepository.findFirstByHostnameOrderByCreatedAtDesc(host);
            if (latest != null) {
                metricsSummary.append(host)
                        .append(" -> CPU %").append(latest.getCpuPercent())
                        .append(", RAM %").append(latest.getRamPercent())
                        .append(", Disk %").append(latest.getDiskPercent())
                        .append('\n');
            }
        }
        if (metricsSummary.isEmpty()) {
            return;
        }

        String prompt = "Asagidaki sunucu metriklerini analiz et. CPU, RAM ve Disk kullanimlari "
                + "arasinda anormal bir korelasyon (or. yuksek CPU ama bos disk kuyrugu, ani trafik "
                + "artisi, kaynak tukenmesi belirtisi) olup olmadigini tespit et. 2-3 cumleyle, "
                + "Turkce ve teknik bir dille ozetle:\n\n" + metricsSummary;

        String analysis = ollamaService.generate(prompt, buildSystemContext(null));
        persistAnomaly(AiOpsAnomalyCategory.CORRELATION, inferSeverity(analysis), "Coklu Metrik Korelasyon", analysis);
    }

    /**
     * Log analizi & kok neden tespiti (RCA): izlenen gruplardaki son
     * basarisiz ozel metrik calistirmalarini (stack trace/hata ciktisi)
     * Ollama'ya yorumlatir. Kritik bulundugunda ve ayarlarda aktifse alarm
     * e-postasi da yollar. Hicbir grup secili degilse hicbir sey yapmaz.
     */
    @Transactional
    public void runRootCauseAnalysis() {
        Set<Long> tracked = loadOrCreateConfig().getTrackedGroupIds();
        if (tracked.isEmpty()) {
            return;
        }

        List<CustomMetricLog> failures = customMetricLogRepository
                .findTop10BySuccessFalseAndGroupIdInOrderByCreatedAtDesc(tracked);
        if (failures.isEmpty()) {
            return;
        }

        StringBuilder logsText = new StringBuilder();
        for (CustomMetricLog failure : failures) {
            logsText.append('[').append(failure.getMetricKey()).append("] ")
                    .append(truncate(failure.getResultPayload(), 400))
                    .append('\n');
        }

        String prompt = "Asagidaki hata loglarini ve stack trace'leri incele. Olasi kok nedeni "
                + "(root cause) tespit et ve kisa, teknik, Turkce bir aciklama yaz. Mumkunse hangi "
                + "dosya/ayarin sorumlu oldugunu belirt:\n\n" + logsText;

        String analysis = ollamaService.generate(prompt, buildSystemContext(null));
        persistAnomaly(AiOpsAnomalyCategory.RCA, AiOpsSeverity.CRITICAL,
                "Log Analizi & Kok Neden Tespiti (RCA)", analysis);

        sendAlertIfEnabled("Log Analizi & Kok Neden Tespiti (RCA)", analysis);
    }

    /**
     * Akilli nobetci: izlenen gruplarin sunucularindaki en guncel okumalari
     * kritik esiklerle karsilastirir, asim varsa Ollama'dan kisa bir uyari
     * metni uretir ve (ayar aktifse) alarm e-postasi yollar. Ollama o an
     * ulasilamaz durumdaysa sablon bir metinle devam eder - esik asimi
     * bildirimi Ollama'nin varligina bagimli olmamali.
     */
    @Transactional
    public void checkCriticalThresholdAndAlert() {
        AiOpsConfig config = loadOrCreateConfig();
        if (!Boolean.TRUE.equals(config.getAlertEmailEnabled())
                || config.getAlertEmail() == null || config.getAlertEmail().isBlank()) {
            return;
        }
        Set<Long> tracked = config.getTrackedGroupIds();
        if (tracked.isEmpty()) {
            return;
        }

        for (String host : hostnamesForGroups(tracked)) {
            MetricEntity latest = metricRepository.findFirstByHostnameOrderByCreatedAtDesc(host);
            if (latest == null) {
                continue;
            }
            boolean critical = latest.getCpuPercent() >= criticalCpuThreshold
                    || latest.getRamPercent() >= criticalRamThreshold
                    || latest.getDiskPercent() >= criticalDiskThreshold;
            if (!critical) {
                continue;
            }

            String prompt = "Sunucu '" + host + "' kritik esik degerini asti: CPU %" + latest.getCpuPercent()
                    + ", RAM %" + latest.getRamPercent() + ", Disk %" + latest.getDiskPercent()
                    + ". Bu durumu 1-2 cumleyle Turkce ozetleyen kisa bir uyari mesaji yaz.";

            String summary;
            try {
                summary = ollamaService.generate(prompt, buildSystemContext(null));
            } catch (OllamaUnavailableException e) {
                log.warn("[AIOPS WATCHDOG] Ollama ulasilamaz durumda, sablon uyari kullaniliyor: {}", e.getMessage());
                summary = "Sunucu " + host + " kritik esik degerini asti (CPU %" + latest.getCpuPercent()
                        + ", RAM %" + latest.getRamPercent() + ", Disk %" + latest.getDiskPercent() + ").";
            }

            persistAnomaly(AiOpsAnomalyCategory.CORRELATION, AiOpsSeverity.CRITICAL,
                    "Kritik Esik Asimi: " + host, summary);
            emailService.sendAiOpsAlertEmail(config.getAlertEmail(), "Kritik Esik Asimi: " + host, summary);
        }
    }

    /**
     * Gunluk sistem sagligi ozeti: izlenen gruplarin son 24 saatlik metrik/
     * hata verilerini derleyip Ollama'ya ozetletir, ayar aktifse mail olarak
     * da yollar. Hicbir grup secili degilse hicbir sey yapmaz.
     */
    @Transactional
    public void runDailyHealthSummary() {
        AiOpsConfig config = loadOrCreateConfig();
        Set<Long> tracked = config.getTrackedGroupIds();
        if (tracked.isEmpty()) {
            return;
        }

        List<String> hostnames = hostnamesForGroups(tracked);
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        List<MetricEntity> recentMetrics = metricRepository.findByCreatedAtAfterOrderByCreatedAtDesc(cutoff).stream()
                .filter(m -> hostnames.contains(m.getHostname()))
                .toList();
        List<CustomMetricLog> recentFailures = customMetricLogRepository
                .findByCreatedAtAfterAndSuccessFalseAndGroupIdInOrderByCreatedAtDesc(cutoff, tracked);

        String prompt = buildDailySummaryPrompt(recentMetrics, recentFailures);
        String summary = ollamaService.generate(prompt, buildSystemContext(null));

        persistAnomaly(AiOpsAnomalyCategory.DAILY_SUMMARY, AiOpsSeverity.INFO, "Gunluk Sistem Durum Ozeti", summary);

        if (Boolean.TRUE.equals(config.getDailySummaryEnabled())
                && config.getAlertEmail() != null && !config.getAlertEmail().isBlank()) {
            emailService.sendAiOpsDailySummaryEmail(config.getAlertEmail(), summary);
        }
    }

    /* ============================== Helpers ============================== */

    private void sendAlertIfEnabled(String title, String summary) {
        AiOpsConfig config = loadOrCreateConfig();
        if (Boolean.TRUE.equals(config.getAlertEmailEnabled())
                && config.getAlertEmail() != null && !config.getAlertEmail().isBlank()) {
            emailService.sendAiOpsAlertEmail(config.getAlertEmail(), title, summary);
        }
    }

    private void persistAnomaly(AiOpsAnomalyCategory category, AiOpsSeverity severity, String title, String content) {
        AiOpsAnomaly anomaly = new AiOpsAnomaly(category, severity, title,
                content != null ? content.trim() : "", LocalDateTime.now());
        aiOpsAnomalyRepository.save(anomaly);
    }

    /** Distinct hostnames of the servers that own the given metric groups. */
    private List<String> hostnamesForGroups(Set<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return metricGroupRepository.findAllById(groupIds).stream()
                .map(g -> g.getServer().getHostname())
                .distinct()
                .toList();
    }

    /**
     * Chat ve analiz istekleri icin "System Context" bloğu: yalnizca
     * kullanicinin izlemeyi sectigi metrik gruplarinin sunucu durumlari +
     * o gruplardaki son basarisiz calistirmalar. {@code scopeGroupId}
     * verilmisse (kullanici chat'te belirli bir grubu secmisse) baglam
     * yalnizca o tek gruba daraltilir, aksi halde izlenen tum gruplar
     * kullanilir ("Genel"). qwen2.5-coder:1.5b gibi kucuk bir yerel model
     * icin baglami dar tutmak amaciyla en fazla 10 sunucu / 10 hata ozetlenir.
     */
    private String buildSystemContext(Long scopeGroupId) {
        Set<Long> tracked = loadOrCreateConfig().getTrackedGroupIds();

        StringBuilder sb = new StringBuilder();
        sb.append("Tarih/Saat: ").append(LocalDateTime.now()).append('\n');

        if (tracked.isEmpty()) {
            sb.append("Kullanici henuz hicbir metrik grubunu izlemeye almadi. ")
                    .append("AI Takip & Analiz sayfasindaki sol panelden en az bir metrik grubu secip kaydetmesi gerektigini belirt.\n");
            return sb.toString();
        }

        boolean scopedToOne = scopeGroupId != null && tracked.contains(scopeGroupId);
        Set<Long> effective = scopedToOne ? Set.of(scopeGroupId) : tracked;

        List<MetricGroup> groups = metricGroupRepository.findAllById(effective);
        sb.append(scopedToOne ? "Odaklanilan Metrik Grubu:\n" : "Izlenen Metrik Gruplari:\n");
        groups.stream().limit(10).forEach(g -> sb.append("- ").append(g.getName())
                .append(" (Sunucu: ").append(g.getServer().getHostname()).append(")\n"));

        List<String> hostnames = groups.stream().map(g -> g.getServer().getHostname()).distinct().limit(10).toList();
        if (!hostnames.isEmpty()) {
            sb.append("Sunucu Durumlari:\n");
            hostnames.forEach(host -> {
                MetricEntity latest = metricRepository.findFirstByHostnameOrderByCreatedAtDesc(host);
                if (latest != null) {
                    sb.append("- ").append(host)
                            .append(": CPU %").append(latest.getCpuPercent())
                            .append(", RAM %").append(latest.getRamPercent())
                            .append(", Disk %").append(latest.getDiskPercent())
                            .append(" (").append(latest.getCreatedAt()).append(")\n");
                }
            });
        }

        List<CustomMetricLog> recentFailures = customMetricLogRepository
                .findTop10BySuccessFalseAndGroupIdInOrderByCreatedAtDesc(effective);
        if (!recentFailures.isEmpty()) {
            sb.append("Son Hata Loglari:\n");
            recentFailures.forEach(failure -> sb.append("- [").append(failure.getMetricKey()).append("] ")
                    .append(truncate(failure.getResultPayload(), 300)).append('\n'));
        }

        sb.append(scopedToOne
                ? "Kullanici su an ozellikle yukaridaki tek metrik grubu hakkinda soru soruyor, cevabini bu grupla sinirla.\n"
                : "Kullanici genel/tum izlenen gruplar hakkinda soruyor.\n");

        return sb.toString();
    }

    private String buildDailySummaryPrompt(List<MetricEntity> metrics, List<CustomMetricLog> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append("Son 24 saatlik sistem verileri:\n");
        sb.append("- Toplam metrik kaydi: ").append(metrics.size()).append('\n');
        sb.append("- Basarisiz ozel metrik calistirma sayisi: ").append(failures.size()).append('\n');

        if (!metrics.isEmpty()) {
            DoubleSummaryStatistics cpuStats = metrics.stream().mapToDouble(MetricEntity::getCpuPercent).summaryStatistics();
            DoubleSummaryStatistics ramStats = metrics.stream().mapToDouble(MetricEntity::getRamPercent).summaryStatistics();
            DoubleSummaryStatistics diskStats = metrics.stream().mapToDouble(MetricEntity::getDiskPercent).summaryStatistics();
            sb.append(String.format("- CPU: ortalama %%%.1f, maks %%%.1f%n", cpuStats.getAverage(), cpuStats.getMax()));
            sb.append(String.format("- RAM: ortalama %%%.1f, maks %%%.1f%n", ramStats.getAverage(), ramStats.getMax()));
            sb.append(String.format("- Disk: ortalama %%%.1f, maks %%%.1f%n", diskStats.getAverage(), diskStats.getMax()));
        }

        if (!failures.isEmpty()) {
            sb.append("Ornek hatalar:\n");
            failures.stream().limit(5).forEach(failure -> sb.append("- [").append(failure.getMetricKey()).append("] ")
                    .append(truncate(failure.getResultPayload(), 200)).append('\n'));
        }

        sb.append("\nBu verilere dayanarak gunluk sistem sagligini 3-4 cumleyle Turkce olarak ozetle. "
                + "Genel durumu, dikkat cekici olaylari ve varsa onerileri belirt.");
        return sb.toString();
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private static AiOpsSeverity inferSeverity(String text) {
        if (text == null) {
            return AiOpsSeverity.INFO;
        }
        String lower = text.toLowerCase();
        if (lower.contains("kritik") || lower.contains("critical") || lower.contains("tukendi")
                || lower.contains("exhaust") || lower.contains("500 hata")) {
            return AiOpsSeverity.CRITICAL;
        }
        if (lower.contains("uyar") || lower.contains("warning") || lower.contains("yuksek")
                || lower.contains("anormal") || lower.contains("dalgalanma")) {
            return AiOpsSeverity.WARNING;
        }
        return AiOpsSeverity.INFO;
    }
}
