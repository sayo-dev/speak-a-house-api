package ng.speakahouse.api.web;

import jakarta.validation.Valid;
import ng.speakahouse.api.common.ApiException;
import ng.speakahouse.api.common.ErrorCode;
import ng.speakahouse.api.config.AppProperties;
import ng.speakahouse.api.interpret.InterpretResult;
import ng.speakahouse.api.interpret.RuleBasedInterpreter;
import ng.speakahouse.api.session.SessionService;
import ng.speakahouse.api.session.VersionService;
import ng.speakahouse.api.spec.HouseSpec;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{id}/interpret")
public class InterpretController {

    private final AppProperties props;
    private final SessionService sessionService;
    private final VersionService versionService;
    private final RuleBasedInterpreter interpreter;

    public InterpretController(
            AppProperties props,
            SessionService sessionService,
            VersionService versionService,
            RuleBasedInterpreter interpreter) {
        this.props = props;
        this.sessionService = sessionService;
        this.versionService = versionService;
        this.interpreter = interpreter;
    }

    @PostMapping
    public InterpretResult interpret(
            @PathVariable String id,
            @RequestHeader(value = "X-Session-Token", required = false) String token,
            @Valid @RequestBody InterpretResult.InterpretRequest req) {
        sessionService.requireSession(id, token);
        if (req.text().length() > props.limits().maxInputChars()) {
            throw new ApiException(
                    ErrorCode.INPUT_TOO_LONG,
                    "Input exceeds " + props.limits().maxInputChars() + " characters");
        }
        HouseSpec baseSpec = null;
        if (req.baseVersionId() != null && !req.baseVersionId().isBlank()) {
            baseSpec = versionService.getSpec(id, token, req.baseVersionId());
        }
        return interpreter.interpret(req.text(), req.baseVersionId(), baseSpec);
    }
}

