package com.example.springcore.di.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificationDemo {
    @Autowired
    private NotificationService notificationService;

    public void demonstrate() {
        notificationService.bySMS();
        notificationService.byEmail();
        notificationService.byPush();
    }
}