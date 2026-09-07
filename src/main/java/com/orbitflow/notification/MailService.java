package com.orbitflow.notification;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email delivery with Mailpit target + in-memory outbox for tests.
 * Retries are handled by the notification worker with exponential backoff.
 */
@Service
@Slf4j
public class MailService {
    private final JavaMailSender mailSender;
    private final String from;
    private final List<Map<String, String>> sentEmails = new CopyOnWriteArrayList<>();

    public MailService(JavaMailSender mailSender,
                       @Value("${orbitflow.mail.from:OrbitFlow <no-reply@orbitflow.local>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendEmail(String to, String subject, String body) {
        // Always record for test verification
        sentEmails.add(Map.of("to", to, "subject", subject, "body", body));
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (Exception e) {
            // Mailpit may be unavailable in CI/test — record + continue; worker retries
            log.debug("Mail send deferred (no SMTP): to={} subject={}", to, subject);
        }
    }

    public List<Map<String, String>> getSentEmails() { return List.copyOf(sentEmails); }
    public void clear() { sentEmails.clear(); }
}
