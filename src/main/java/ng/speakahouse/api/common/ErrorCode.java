package ng.speakahouse.api.common;

import org.springframework.http.HttpStatus;

/** Stable error codes from contract/API-CONTRACT.md section 5. Never rename. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Invalid request"),
    INVALID_SESSION_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid session token"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Session not found"),
    VERSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Version not found"),
    INPUT_TOO_LONG(HttpStatus.valueOf(413), "Input too long"),
    LAYOUT_INFEASIBLE(HttpStatus.valueOf(422), "This house doesn't fit the plot"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI unavailable"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");

    public final HttpStatus status;
    public final String title;

    ErrorCode(HttpStatus status, String title) { this.status = status; this.title = title; }

    public String typeUri() {
        return "https://speakahouse.ng/problems/" + name().toLowerCase().replace('_', '-');
    }
}

