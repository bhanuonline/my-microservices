package notification.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class NotificationRequest {

    private String type;
    private List<String> emails;
    private List<String> phoneNumbers;
    private String message;
}
