package com.monitoring.poc.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean mailEnabled;

    public EmailServiceImpl(JavaMailSender mailSender,
                            @Value("${app.mail.from:monitoring-noreply@example.com}") String fromAddress,
                            @Value("${app.mail.enabled:true}") boolean mailEnabled) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.mailEnabled = mailEnabled;
        log.info("[SMTP INIT] EmailService baslatildi. MailEnabled={}, FromAddress={}", mailEnabled, fromAddress);
    }

    @Async
    @Override
    public void sendOtpEmail(String to, String otpCode) {
        log.info("[OTP FLOW] OTP mail istegi alindi -> To: {}, Kod: {}", to, otpCode);
        send(to, "[Giriş Doğrulama] Tek Kullanımlık Doğrulama Kodunuz (2FA)",
                "Dogrulama kodunuz: " + otpCode + "\n\nBu kod 5 dakika icinde gecerliligini yitirecektir. "
                        + "Bu istegi siz yapmadiysanız bu e-postayi yok sayabilirsiniz.");
    }

    @Async
    @Override
    public void sendNewMetricRequestEmail(List<String> adminEmails, String metricKey, String requestedBy) {
        log.info("[METRIC REQUEST] Yeni metrik bildirimi -> MetricKey: {}, RequestedBy: {}, Hedef Admin Sayisi: {}",
                metricKey, requestedBy, (adminEmails != null ? adminEmails.size() : 0));
        if (adminEmails == null || adminEmails.isEmpty()) {
            log.warn("[METRIC REQUEST] Admin listesi bos oldugu icin e-posta gonderilmedi.");
            return;
        }
        String subject = "[Monitoring] Yeni Metrik Onay Talebi: " + metricKey;
        String body = requestedBy + " kullanicisi \"" + metricKey + "\" anahtarli yeni bir metrik talebi olusturdu. Onay bekleniyor.";
        adminEmails.forEach(to -> send(to, subject, body));
    }

    @Async
    @Override
    public void sendMetricApprovedEmail(String to, String metricKey) {
        log.info("[METRIC APPROVE] Metrik onay bildirimi -> To: {}, MetricKey: {}", to, metricKey);
        send(to, "[Monitoring] Metrik Talebiniz Onaylandı: " + metricKey,
                "\"" + metricKey + "\" anahtarli metrik talebiniz onaylandi.");
    }

    @Async
    @Override
    public void sendMetricRejectedEmail(String to, String metricKey, String reason) {
        log.info("[METRIC REJECT] Metrik red bildirimi -> To: {}, MetricKey: {}, Sebep: {}", to, metricKey, reason);
        send(to, "[Monitoring] Metrik Talebiniz Reddedildi: " + metricKey,
                "\"" + metricKey + "\" anahtarli metrik talebiniz reddedildi.\n\nGerekce: " + reason);
    }

    @Async
    @Override
    public void sendConfigDriftEmail(List<String> recipients, String serverName, String fileName) {
        log.warn("[CONFIG DRIFT] Kritik Drift alarm bildirimi -> Sunucu: {}, Dosya: {}, Alici Sayisi: {}",
                serverName, fileName, (recipients != null ? recipients.size() : 0));
        if (recipients == null || recipients.isEmpty()) {
            log.warn("[CONFIG DRIFT] Alici listesi bos oldugu icin alarm maili iletilemedi.");
            return;
        }
        String subject = "⚠️ [KRİTİK ALARM] Yapılandırma Sapması (Drift): " + serverName + " - " + fileName;
        String body = serverName + " sunucusundaki \"" + fileName + "\" dosyasinda beklenmeyen bir degisiklik tespit edildi. "
                + "Lutfen paneldeki Diff goruntuleyiciden inceleyin.";
        recipients.forEach(to -> send(to, subject, body));
    }

    @Async
    @Override
    public void sendProfileChangeRequestEmail(List<String> adminEmails, String username, String changeType) {
        log.info("[PROFILE REQUEST] Profil degisiklik bildirimi -> User: {}, Type: {}", username, changeType);
        if (adminEmails == null || adminEmails.isEmpty()) return;
        String subject = "[Monitoring] Profil Degisiklik Talebi: " + username;
        String body = username + " kullanicisi bir " + changeType + " degisikligi talep etti. Onay bekleniyor.";
        adminEmails.forEach(to -> send(to, subject, body));
    }

    @Async
    @Override
    public void sendProfileChangeApprovedEmail(String to, String changeType) {
        log.info("[PROFILE APPROVED] Profil degisiklik onay bildirimi -> To: {}, Type: {}", to, changeType);
        send(to, "[Monitoring] Profil Degisiklik Talebiniz Onaylandı",
                changeType + " degisikligi talebiniz onaylandi ve uygulandi.");
    }

    @Async
    @Override
    public void sendProfileChangeRejectedEmail(String to, String changeType, String reason) {
        log.info("[PROFILE REJECTED] Profil degisiklik red bildirimi -> To: {}, Type: {}, Sebep: {}", to, changeType, reason);
        send(to, "[Monitoring] Profil Degisiklik Talebiniz Reddedildi",
                changeType + " degisikligi talebiniz reddedildi.\n\nGerekce: " + reason);
    }

    @Async
    @Override
    public void sendAiOpsAlertEmail(String to, String subject, String summary) {
        log.warn("[AIOPS ALERT] Kritik anomali maili -> To: {}, Konu: {}", to, subject);
        send(to, "🚨 [AIOps Kritik Uyarı] " + subject, summary);
    }

    @Async
    @Override
    public void sendAiOpsDailySummaryEmail(String to, String summary) {
        log.info("[AIOPS DAILY SUMMARY] Gunluk saglik ozeti maili -> To: {}", to);
        send(to, "[AIOps] Günlük Sistem Sağlığı Özeti", summary);
    }

    private void send(String to, String subject, String body) {
        log.debug("[SMTP PRE-CHECK] Send metoduna girildi. To='{}', Subject='{}'", to, subject);

        if (!mailEnabled) {
            log.warn("[SMTP DISABLED] E-posta gonderimi uygulama ayarlarindan (app.mail.enabled=false) kapatilmis.");
            return;
        }

        if (to == null || to.isBlank()) {
            log.error("[SMTP VALIDATION ERROR] Alici adresi (to) null veya bos olamaz!");
            return;
        }

        String targetRecipient = to.trim();
        log.info("[SMTP DISPATCH] E-posta gonderimi baslatiliyor -> From: '{}', To: '{}', Subject: '{}'",
                fromAddress, targetRecipient, subject);

        long startTime = System.currentTimeMillis();

        try {
            log.debug("[SMTP PREPARE] MimeMessage nesnesi hazirlaniyor...");
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());

            helper.setFrom(fromAddress);
            helper.setTo(targetRecipient);
            helper.setSubject(subject);
            helper.setText(body, false);

            log.debug("[SMTP SENDING] JavaMailSender.send() cagriliyor, SMTP sunucusuna veri iletiliyor...");
            mailSender.send(mimeMessage);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[SMTP SUCCESS] E-posta basariyla iletildi! To: '{}', Subject: '{}', Sure: {} ms",
                    targetRecipient, subject, duration);

        } catch (MessagingException me) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[SMTP MIME ERROR] MimeMessage paketleme/header hatasi ({} ms sonra)! To: '{}', Hata: {}",
                    duration, targetRecipient, me.getMessage(), me);
        } catch (MailException me) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[SMTP NETWORK/AUTH ERROR] SMTP sunucusuna iletim sirasinda hata olustu ({} ms sonra)! To: '{}', Hata Sinifi: '{}', Mesaj: '{}'",
                    duration, targetRecipient, me.getClass().getSimpleName(), me.getMessage(), me);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[SMTP UNEXPECTED ERROR] Beklenmeyen hata ({} ms sonra)! To: '{}', Hata: {}",
                    duration, targetRecipient, e.getMessage(), e);
        }
    }
}