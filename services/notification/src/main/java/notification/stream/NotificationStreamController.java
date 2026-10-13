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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private final Map<String, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    // APPROACH 2 — dedicated push thread pool (see pushParallel below)
    // Virtual threads (Java 21): each emitter.send() gets its own lightweight thread.
    // If one user's connection is slow/blocked, other users are not affected.
    private final ExecutorService pushExecutor = Executors.newVirtualThreadPerTaskExecutor();

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

    // -------------------------------------------------------------------------
    // APPROACH 1 (original) — Sequential push (single thread)
    // -------------------------------------------------------------------------
    // Iterates emitters one by one on the caller's thread.
    // Problem: if user A has a slow connection, user B waits behind them.
    // Fine when there is only 1 emitter per user (one tab open).
    // -------------------------------------------------------------------------
    /** Called by downstream event bridges to push a notification to a specific user. */
    public void push(String userId, String eventName, Object payload) {
        List<SseEmitter> list = emittersByUser.get(userId);
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

    // -------------------------------------------------------------------------
    // APPROACH 2 — Parallel push with ExecutorService (virtual threads)
    // -------------------------------------------------------------------------
    // Each emitter.send() runs on its own virtual thread — completely independent.
    // A blocked/slow connection for one user does NOT delay any other user's push.
    //
    // Use this when:
    //   - Multiple users need to be notified simultaneously (broadcast events)
    //   - Users may have multiple tabs open (multiple emitters per user)
    //   - You need non-blocking push under load
    // -------------------------------------------------------------------------
    public void pushParallel(String userId, String eventName, Object payload) {
        List<SseEmitter> list = emittersByUser.get(userId);
        if (list == null || list.isEmpty()) return;

        // Submit one task per emitter — all run concurrently on virtual threads
        for (SseEmitter emitter : list) {
            pushExecutor.submit(() -> {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(payload));
                } catch (IOException e) {
                    log.debug("SSE parallel send failed for user {} — removing: {}", userId, e.toString());
                    emitter.complete();
                }
            });
        }
    }

    // -------------------------------------------------------------------------
    // APPROACH 3 — Broadcast to ALL connected users in parallel
    // -------------------------------------------------------------------------
    // Useful for system-wide events: maintenance alerts, feature announcements.
    // Each user's push is independent — no single slow user blocks others.
    // -------------------------------------------------------------------------
    public void broadcast(String eventName, Object payload) {
        emittersByUser.forEach((userId, emitters) ->
                pushExecutor.submit(() -> push(userId, eventName, payload))
        );
        log.info("Broadcast '{}' dispatched to {} users", eventName, emittersByUser.size());
    }

    /** Observability — expose current connected users as a metric via /actuator/metrics. */
    public int getActiveUserCount() { return emittersByUser.size(); }
}
