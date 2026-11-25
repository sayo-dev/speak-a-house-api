package ng.speakahouse.api.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import ng.speakahouse.api.common.ApiException;
import ng.speakahouse.api.common.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private static final SecureRandom RNG = new SecureRandom();
    private final DesignSessionRepository sessionRepo;

    public SessionService(DesignSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @Transactional
    public SessionDtos.SessionCreateResponse create() {
        String id = "ses_" + randomHex(8);
        String token = "tok_" + randomHex(16);
        Instant now = Instant.now();
        DesignSessionEntity entity = new DesignSessionEntity(id, sha256(token), now);
        sessionRepo.save(entity);
        return new SessionDtos.SessionCreateResponse(id, token, now);
    }

    public DesignSessionEntity requireSession(String sessionId, String token) {
        DesignSessionEntity session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND, "Session not found: " + sessionId));
        if (token != null && !token.isBlank() && !session.getTokenHash().equals(sha256(token))) {
            throw new ApiException(ErrorCode.INVALID_SESSION_TOKEN, "Invalid session token");
        }
        return session;
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RNG.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

