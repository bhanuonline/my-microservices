package com.example.springcore.di.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class NotificationService {
    @Autowired
    @Qualifier("emailNotification")
    private Notification email;
    @Autowired
    @Qualifier("SMSNotification")
    private Notification sms;
    @Autowired
    @Qualifier("pushNotification")
    private Notification push;

    public void byEmail() {
        NotificationData emailData = new NotificationData();
        emailData.setNotificationId(100);
        emailData.setTo("abc@gmail.com");
        email.send(emailData);
    }

    public void bySMS() {
        NotificationData smsdata = new NotificationData();
        smsdata.setNotificationId(100);
        smsdata.setTo("879879987");
        sms.send(smsdata);
    }

    public void byPush() {
        NotificationData pushdata = new NotificationData();
        pushdata.setNotificationId(100);
        pushdata.setTo("Bhanu");
        push.send(pushdata);
    }
}