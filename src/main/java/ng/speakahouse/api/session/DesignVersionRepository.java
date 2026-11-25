package ng.speakahouse.api.session;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignVersionRepository extends JpaRepository<DesignVersionEntity, String> {
    List<DesignVersionEntity> findBySessionIdOrderBySeqAsc(String sessionId);
    Optional<DesignVersionEntity> findByIdAndSessionId(String id, String sessionId);
    Optional<DesignVersionEntity> findTopBySessionIdOrderBySeqDesc(String sessionId);
}

