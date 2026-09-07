package com.orbitflow.notification;

import com.orbitflow.task.Task;
import com.orbitflow.task.TaskRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Due-date reminders + digest aggregation.
 * Runs in controlled batches; deduplicates via Notification.dedupeKey.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWorker {
    private final TaskRepository tasks;
    private final NotificationService notifications;

    @Scheduled(fixedDelayString = "${orbitflow.reminders.poll-ms:60000}")
    @Transactional
    public void dueDateReminders() {
        Instant soon = Instant.now().plus(24, ChronoUnit.HOURS);
        Instant now = Instant.now();
        int scanned = 0;
        for (Task t : tasks.findUpcomingDueTasks(now, soon, PageRequest.of(0, 200))) {
            Set<UUID> recipients = new HashSet<>();
            t.getAssignees().forEach(a -> recipients.add(a.getUser().getId()));
            if (recipients.isEmpty()) continue;
            try {
                notifications.notifyUsers(t.getWorkspace().getId(), recipients,
                        "DUE_SOON", "Due soon: " + t.getTaskKey() + " " + t.getTitle(),
                        "Task " + t.getTaskKey() + " is due at " + t.getDueDate(),
                        "Task", t.getId());
                scanned++;
                if (scanned >= 200) break; // controlled batches
            } catch (Exception e) {
                log.debug("Reminder failed for {}: {}", t.getId(), e.toString());
            }
        }
    }
}
