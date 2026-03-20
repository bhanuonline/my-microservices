package notification.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import notification.api.dto.NotificationRequest;
import notification.core.NotificationContext;
import notification.core.NotificationTarget;
import notification.core.NotificationType;
import notification.orchestration.NotificationOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationOrchestrator orchestrator;

    @PostMapping
    public ResponseEntity<String> send(@RequestBody NotificationRequest request) {
        log.info("Received notification request: {}", request);

        NotificationContext  context= map(request);
        orchestrator.orchestrate(context);
        return ResponseEntity.ok("Notification sent");
    }

    private NotificationContext map(NotificationRequest req) {

        NotificationTarget target = NotificationTarget.builder()
                .emails(req.getEmails())
                .phoneNumbers(req.getPhoneNumbers())
                .attributes(Map.of("locale", "en_US"))
                .build();

        return NotificationContext.builder()
                .type(NotificationType.valueOf(req.getType()))
                .target(target)
                .message(req.getMessage())
                .build();
    }

    @GetMapping("/test")
    public String test() {
        return "Notification controller is working";
    }
}
