package ng.speakahouse.api.session;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import ng.speakahouse.api.cost.CostEstimate;
import ng.speakahouse.api.layout.Plan;
import ng.speakahouse.api.spec.HouseSpec;

public final class SessionDtos {
    private SessionDtos() {}

    public record SessionCreateResponse(String sessionId, String token, Instant createdAt) {}

    public record CreateVersionRequest(
            @NotNull @Valid HouseSpec spec,
            @Size(max = 500) String inputText,
            String parentVersionId) {}

    public record UpdateHeadRequest(@NotNull String versionId) {}

    public record VersionDiff(
            List<Change> changes,
            List<String> roomsAdded,
            List<String> roomsRemoved,
            CostDelta costDelta) {
        public record Change(String path, Object from, Object to) {}
        public record CostDelta(long expected, List<GroupDelta> byGroup) {}
        public record GroupDelta(String group, long delta) {}
    }

    public record VersionResponse(
            String id,
            String sessionId,
            int seq,
            String parentId,
            String inputText,
            Instant createdAt,
            HouseSpec spec,
            Plan plan,
            CostEstimate estimate,
            VersionDiff diff,
            List<String> explanation) {}

    public record VersionsListResponse(
            String sessionId,
            String headVersionId,
            List<VersionSummary> versions) {}

    public record VersionSummary(
            String id,
            int seq,
            String parentId,
            String inputText,
            Instant createdAt,
            long expected,
            String budgetStatus) {}
}

