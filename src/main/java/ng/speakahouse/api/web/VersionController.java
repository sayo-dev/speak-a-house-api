package ng.speakahouse.api.web;

import jakarta.validation.Valid;
import java.net.URI;
import ng.speakahouse.api.session.SessionDtos;
import ng.speakahouse.api.session.VersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{id}/versions")
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    @PostMapping
    public ResponseEntity<SessionDtos.VersionResponse> create(
            @PathVariable String id,
            @RequestHeader(value = "X-Session-Token", required = false) String token,
            @Valid @RequestBody SessionDtos.CreateVersionRequest req) {
        SessionDtos.VersionResponse created = versionService.createVersion(id, token, req);
        URI location = URI.create("/api/v1/sessions/" + id + "/versions/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public SessionDtos.VersionsListResponse list(
            @PathVariable String id,
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        return versionService.listVersions(id, token);
    }

    @GetMapping("/{vid}")
    public SessionDtos.VersionResponse get(
            @PathVariable String id,
            @PathVariable String vid,
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        return versionService.getVersion(id, token, vid);
    }

    @GetMapping(value = "/{vid}/boq.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<String> exportBoqCsv(
            @PathVariable String id,
            @PathVariable String vid,
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        SessionDtos.VersionResponse v = versionService.getVersion(id, token, vid);
        StringBuilder sb = new StringBuilder();
        sb.append("key,group,description,quantity,unit,rate_ngn,amount_ngn,price_source,observed_on,verified\n");
        for (var line : v.estimate().lines()) {
            sb.append(csv(line.key())).append(',')
                    .append(csv(line.group())).append(',')
                    .append(csv(line.description())).append(',')
                    .append(line.quantity()).append(',')
                    .append(csv(line.unit())).append(',')
                    .append(line.rate()).append(',')
                    .append(line.amount()).append(',')
                    .append(csv(line.priceSource())).append(',')
                    .append(csv(line.observedOn())).append(',')
                    .append(line.verified()).append('\n');
        }
        sb.append("total,,Expected Total,,,,,")
                .append(v.estimate().expected()).append(",,\n");
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"boq-" + vid + ".csv\"")
                .body(sb.toString());
    }

    private static String csv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}

