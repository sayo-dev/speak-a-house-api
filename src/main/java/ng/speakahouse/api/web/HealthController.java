package ng.speakahouse.api.web;

import java.time.Instant;
import java.util.Map;
import ng.speakahouse.api.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final AppProperties props;
    private final JdbcTemplate jdbc;

    public HealthController(AppProperties props, JdbcTemplate jdbc) {
        this.props = props;
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Integer unverified = jdbc.queryForObject("select count(*) from price_item where verified = false", Integer.class);
        return Map.of(
                "status", "UP",
                "version", "0.1.0",
                "contractVersion", props.contractVersion(),
                "aiEnabled", props.ai().enabled(),
                "priceDataVerified", unverified != null && unverified == 0,
                "time", Instant.now().toString());
    }
}

