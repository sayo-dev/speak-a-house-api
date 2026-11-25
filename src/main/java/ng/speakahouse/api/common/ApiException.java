package ng.speakahouse.api.common;

import java.util.Map;

/** Throw this from services; GlobalExceptionHandler turns it into RFC 7807 with a `code`. */
public class ApiException extends RuntimeException {
    public final ErrorCode code;
    public final Map<String, Object> extra;   // e.g. "violations" -> List<Violation>

    public ApiException(ErrorCode code, String detail) { this(code, detail, Map.of()); }

    public ApiException(ErrorCode code, String detail, Map<String, Object> extra) {
        super(detail);
        this.code = code;
        this.extra = extra;
    }
}

