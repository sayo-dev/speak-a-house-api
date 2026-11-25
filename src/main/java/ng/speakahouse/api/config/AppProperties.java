package ng.speakahouse.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String contractVersion,
        Cors cors,
        Ai ai,
        Limits limits,
        String adminKey) {

    public record Cors(List<String> allowedOrigins) {}

    public record Ai(String apiKey, String baseUrl, String model, int timeoutSeconds, int maxCallsPerSession) {
        public boolean enabled() { return apiKey != null && !apiKey.isBlank(); }
    }

    public record Limits(int maxInputChars, int rateLimitPerMinute) {}
}
