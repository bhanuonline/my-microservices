package notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class NotificationAPPs {
    public static void main(String[] args) {
        SpringApplication.run(NotificationAPPs.class, args);

    }

    public String getNotification(){
        return "notification";
    }
}
