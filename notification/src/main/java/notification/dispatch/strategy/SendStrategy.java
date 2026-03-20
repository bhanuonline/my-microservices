package notification.dispatch.strategy;

import java.util.List;

public interface SendStrategy<T> {
    void send(List<T> payloads);
}
