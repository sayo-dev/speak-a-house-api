package ng.speakahouse.api.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException e, HttpServletRequest req) {
        ProblemDetail pd = problem(e.code, e.getMessage(), req);
        e.extra.forEach(pd::setProperty);
        return ResponseEntity.status(e.code.status).body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        String detail = errors.isEmpty() ? "Invalid request" : errors.get(0).get("field") + " " + errors.get(0).get("message");
        ProblemDetail pd = problem(ErrorCode.VALIDATION_FAILED, detail, req);
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException e, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(problem(ErrorCode.VALIDATION_FAILED, "Malformed JSON body", req));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> other(Exception e, HttpServletRequest req) {
        log.error("Unhandled error on {}", req.getRequestURI(), e);
        return ResponseEntity.internalServerError().body(problem(ErrorCode.INTERNAL_ERROR, "Unexpected error", req));
    }

    private static ProblemDetail problem(ErrorCode code, String detail, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(code.status, detail);
        pd.setType(URI.create(code.typeUri()));
        pd.setTitle(code.title);
        pd.setInstance(URI.create(req.getRequestURI()));
        pd.setProperty("code", code.name());
        return pd;
    }
}

