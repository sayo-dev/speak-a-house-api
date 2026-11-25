package ng.speakahouse.api.web;

import jakarta.validation.Valid;
import ng.speakahouse.api.session.SessionDtos;
import ng.speakahouse.api.session.SessionService;
import ng.speakahouse.api.session.VersionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final VersionService versionService;

    public SessionController(SessionService sessionService, VersionService versionService) {
        this.sessionService = sessionService;
        this.versionService = versionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionDtos.SessionCreateResponse create() {
        return sessionService.create();
    }

    @PutMapping("/{id}/head")
    public SessionDtos.VersionsListResponse updateHead(
            @PathVariable String id,
            @RequestHeader(value = "X-Session-Token", required = false) String token,
            @Valid @RequestBody SessionDtos.UpdateHeadRequest req) {
        return versionService.updateHead(id, token, req.versionId());
    }
}

