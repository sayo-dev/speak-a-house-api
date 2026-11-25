package ng.speakahouse.api.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import ng.speakahouse.api.common.ApiException;
import ng.speakahouse.api.common.ErrorCode;
import ng.speakahouse.api.cost.CostEngine;
import ng.speakahouse.api.cost.CostEstimate;
import ng.speakahouse.api.layout.LayoutEngine;
import ng.speakahouse.api.layout.Plan;
import ng.speakahouse.api.spec.HouseSpec;
import ng.speakahouse.api.spec.SpecValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class VersionService {

    private final DesignSessionRepository sessionRepo;
    private final DesignVersionRepository versionRepo;
    private final SessionService sessionService;
    private final SpecValidator specValidator;
    private final LayoutEngine layoutEngine;
    private final CostEngine costEngine;
    private final DiffService diffService;
    private final ObjectMapper mapper;

    public VersionService(
            DesignSessionRepository sessionRepo,
            DesignVersionRepository versionRepo,
            SessionService sessionService,
            SpecValidator specValidator,
            LayoutEngine layoutEngine,
            CostEngine costEngine,
            DiffService diffService,
            ObjectMapper mapper) {
        this.sessionRepo = sessionRepo;
        this.versionRepo = versionRepo;
        this.sessionService = sessionService;
        this.specValidator = specValidator;
        this.layoutEngine = layoutEngine;
        this.costEngine = costEngine;
        this.diffService = diffService;
        this.mapper = mapper;
    }

    @Transactional
    public SessionDtos.VersionResponse createVersion(
            String sessionId, String token, SessionDtos.CreateVersionRequest req) {
        DesignSessionEntity session = sessionService.requireSession(sessionId, token);
        specValidator.validate(req.spec());

        HouseSpec prevSpec = null;
        Plan prevPlan = null;
        CostEstimate prevEst = null;
        if (req.parentVersionId() != null && !req.parentVersionId().isBlank()) {
            DesignVersionEntity parent = versionRepo.findByIdAndSessionId(req.parentVersionId(), sessionId)
                    .orElseThrow(() -> new ApiException(
                            ErrorCode.VERSION_NOT_FOUND, "Parent version not found: " + req.parentVersionId()));
            prevSpec = mapper.readValue(parent.getSpecJson(), HouseSpec.class);
            prevPlan = mapper.readValue(parent.getPlanJson(), Plan.class);
            prevEst = mapper.readValue(parent.getEstimateJson(), CostEstimate.class);
        }

        Plan plan = layoutEngine.layout(req.spec());
        CostEstimate estimate = costEngine.estimate(req.spec(), plan);
        SessionDtos.VersionDiff diff = diffService.diff(prevSpec, prevPlan, prevEst, req.spec(), plan, estimate);
        List<String> explanation = buildExplanation(req.spec(), diff);

        int nextSeq = versionRepo.findTopBySessionIdOrderBySeqDesc(sessionId)
                .map(v -> v.getSeq() + 1)
                .orElse(1);
        String vid = "ver_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Instant now = Instant.now();
        String budgetStatus = estimate.budget() != null ? estimate.budget().status() : null;

        DesignVersionEntity entity = new DesignVersionEntity(
                vid,
                sessionId,
                nextSeq,
                req.parentVersionId(),
                req.inputText(),
                mapper.writeValueAsString(req.spec()),
                mapper.writeValueAsString(plan),
                mapper.writeValueAsString(estimate),
                diff != null ? mapper.writeValueAsString(diff) : null,
                mapper.writeValueAsString(explanation),
                estimate.expected(),
                budgetStatus,
                now);
        versionRepo.save(entity);
        session.setHeadVersionId(vid);
        sessionRepo.save(session);

        return new SessionDtos.VersionResponse(
                vid, sessionId, nextSeq, req.parentVersionId(), req.inputText(), now,
                req.spec(), plan, estimate, diff, explanation);
    }

    @Transactional(readOnly = true)
    public SessionDtos.VersionsListResponse listVersions(String sessionId, String token) {
        DesignSessionEntity session = sessionService.requireSession(sessionId, token);
        List<SessionDtos.VersionSummary> versions = versionRepo.findBySessionIdOrderBySeqAsc(sessionId).stream()
                .map(v -> new SessionDtos.VersionSummary(
                        v.getId(), v.getSeq(), v.getParentId(), v.getInputText(),
                        v.getCreatedAt(), v.getExpectedNgn(), v.getBudgetStatus()))
                .toList();
        return new SessionDtos.VersionsListResponse(sessionId, session.getHeadVersionId(), versions);
    }

    @Transactional(readOnly = true)
    public SessionDtos.VersionResponse getVersion(String sessionId, String token, String versionId) {
        sessionService.requireSession(sessionId, token);
        DesignVersionEntity v = versionRepo.findByIdAndSessionId(versionId, sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.VERSION_NOT_FOUND, "Version not found: " + versionId));
        return toResponse(v);
    }

    @Transactional(readOnly = true)
    public HouseSpec getSpec(String sessionId, String token, String versionId) {
        sessionService.requireSession(sessionId, token);
        DesignVersionEntity v = versionRepo.findByIdAndSessionId(versionId, sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.VERSION_NOT_FOUND, "Version not found: " + versionId));
        return mapper.readValue(v.getSpecJson(), HouseSpec.class);
    }

    @Transactional
    public SessionDtos.VersionsListResponse updateHead(String sessionId, String token, String versionId) {
        DesignSessionEntity session = sessionService.requireSession(sessionId, token);
        versionRepo.findByIdAndSessionId(versionId, sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.VERSION_NOT_FOUND, "Version not found: " + versionId));
        session.setHeadVersionId(versionId);
        sessionRepo.save(session);
        return listVersions(sessionId, token);
    }

    private SessionDtos.VersionResponse toResponse(DesignVersionEntity v) {
        HouseSpec spec = mapper.readValue(v.getSpecJson(), HouseSpec.class);
        Plan plan = mapper.readValue(v.getPlanJson(), Plan.class);
        CostEstimate estimate = mapper.readValue(v.getEstimateJson(), CostEstimate.class);
        SessionDtos.VersionDiff diff = v.getDiffJson() != null
                ? mapper.readValue(v.getDiffJson(), SessionDtos.VersionDiff.class)
                : null;
        List<String> explanation = v.getExplanationJson() != null
                ? mapper.readValue(v.getExplanationJson(), new TypeReference<>() {})
                : List.of();
        return new SessionDtos.VersionResponse(
                v.getId(), v.getSessionId(), v.getSeq(), v.getParentId(), v.getInputText(), v.getCreatedAt(),
                spec, plan, estimate, diff, explanation);
    }

    private static List<String> buildExplanation(HouseSpec spec, SessionDtos.VersionDiff diff) {
        if (diff == null) {
            String shop = spec.features().shop() ? " with a shop at the front" : "";
            String w = fmtNum(spec.plot().width());
            String d = fmtNum(spec.plot().depth());
            return List.of(spec.rooms().bedrooms() + "-bedroom bungalow" + shop
                    + " on a " + w + " × " + d + " " + spec.plot().unit().name() + " plot.");
        }
        List<String> out = new ArrayList<>();
        for (SessionDtos.VersionDiff.Change c : diff.changes()) {
            out.add("Updated " + c.path() + ": " + c.from() + " → " + c.to() + ".");
        }
        if (out.isEmpty()) {
            out.add("Recomputed layout and cost estimate.");
        }
        return out;
    }

    private static String fmtNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}

