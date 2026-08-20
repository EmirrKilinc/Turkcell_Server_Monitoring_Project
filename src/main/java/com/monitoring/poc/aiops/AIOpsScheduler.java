package com.monitoring.poc.aiops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background cadence for the AIOps module. Every job is wrapped so a
 * temporarily unreachable Ollama instance (or an empty dataset) never stops
 * the scheduler thread - each tick logs and moves on, the next tick tries
 * again.
 */
@Component
public class AIOpsScheduler {

    private static final Logger log = LoggerFactory.getLogger(AIOpsScheduler.class);

    private final AIOpsService aiOpsService;

    public AIOpsScheduler(AIOpsService aiOpsService) {
        this.aiOpsService = aiOpsService;
    }

    /**
     * Periodic scan: multi-metric correlation, log/RCA analysis, and the
     * critical-threshold watchdog. Default every 5 minutes
     * (app.aiops.scan-interval-ms).
     */
    @Scheduled(fixedDelayString = "${app.aiops.scan-interval-ms:1800000}")
    public void periodicScan() {
        runSafely("Coklu Metrik Korelasyonu", aiOpsService::runCorrelationAnalysis);
        runSafely("Log Analizi & RCA", aiOpsService::runRootCauseAnalysis);
        runSafely("Kritik Esik Nobetcisi", aiOpsService::checkCriticalThresholdAndAlert);
    }

    /** Her sabah 08:00: Gunluk Sistem Sagligi Ozeti. */
    @Scheduled(cron = "0 0 8 * * ?")
    public void dailyHealthSummaryJob() {
        runSafely("Gunluk Sistem Sagligi Ozeti", aiOpsService::runDailyHealthSummary);
    }

    private void runSafely(String jobName, Runnable job) {
        try {
            job.run();
        } catch (OllamaUnavailableException e) {
            log.warn("[AIOPS SCHEDULER] '{}' atlandi - Ollama'ya ulasilamadi: {}", jobName, e.getMessage());
        } catch (Exception e) {
            log.error("[AIOPS SCHEDULER] '{}' calisirken beklenmeyen hata: {}", jobName, e.getMessage(), e);
        }
    }
}
