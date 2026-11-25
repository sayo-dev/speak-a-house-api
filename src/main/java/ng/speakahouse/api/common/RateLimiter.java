package ng.speakahouse.api.common;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ng.speakahouse.api.config.AppProperties;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private record Window(long minuteEpoch, int count) {}

    private final int maxPerMinute;
    private final Map<String, Window> buckets = new ConcurrentHashMap<>();

    public RateLimiter(AppProperties props) {
        int rpm = props.limits() != null ? props.limits().rateLimitPerMinute() : 30;
        this.maxPerMinute = Math.max(1, rpm);
    }

    public void check(String sessionId) {
        long minute = Instant.now().getEpochSecond() / 60;
        Window updated = buckets.compute(sessionId, (k, w) -> {
            if (w == null || w.minuteEpoch() != minute) {
                return new Window(minute, 1);
            }
            return new Window(minute, w.count() + 1);
        });
        if (updated.count() > maxPerMinute) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "Too many requests, wait a moment");
        }
    }
}
