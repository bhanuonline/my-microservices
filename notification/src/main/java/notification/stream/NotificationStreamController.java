package notification.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Server-Sent Events push surface.
 *
 *   GET /notifications/stream/{userId}
 *     Content-Type: text/event-stream
 *     keeps the connection open; emits an SSE event per push.
 *
 * Backed by a per-user {@link SseEmitter} list (CopyOnWriteArrayList) because
 * one user can have multiple open tabs. On broken connection, remove the
 * emitter so push doesn't fail.
 *
 * Horizontal scale: this map lives per-instance. With multiple notification
 * replicas, you need either sticky sessions at the LB or a Redis pub/sub bus
 * so events reach whichever instance holds the user's connection. Documented
 * in tier4-roadmap.md §8.
 */
@RestController
@RequestMapping("/notifications/stream")
public class NotificationStreamController {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamController.class);
    private static final long TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);

    private final Map<String, java.util.List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    @GetMapping(path = "/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable removal = () -> {
            java.util.List<SseEmitter> list = emittersByUser.get(userId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) emittersByUser.remove(userId);
            }
        };
        emitter.onCompletion(removal);
        emitter.onTimeout(removal);
        emitter.onError(e -> removal.run());

        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("userId", userId)));
        } catch (IOException e) {
            removal.run();
        }
        return emitter;
    }

    /** Called by downstream event bridges to push a notification to a specific user. */
    public void push(String userId, String eventName, Object payload) {
        java.util.List<SseEmitter> list = emittersByUser.get(userId);
        if (list == null || list.isEmpty()) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException e) {
                log.debug("SSE send failed for user {} — removing emitter: {}", userId, e.toString());
                emitter.complete();
            }
        }
    }

    /** Observability — expose current connected users as a metric via /actuator/metrics. */
    public int getActiveUserCount() { return emittersByUser.size(); }
}
