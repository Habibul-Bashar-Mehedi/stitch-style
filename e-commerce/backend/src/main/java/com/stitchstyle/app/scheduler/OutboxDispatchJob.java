package com.stitchstyle.app.scheduler;

import com.stitchstyle.app.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDispatchJob {

    private final NotificationService notificationService;

    public OutboxDispatchJob(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void dispatch() {
        notificationService.dispatchDue();
    }
}
