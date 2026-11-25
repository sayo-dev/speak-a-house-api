package ng.speakahouse.api.interpret;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import ng.speakahouse.api.spec.HouseSpec;
import ng.speakahouse.api.spec.SpecPatchOp;

public sealed interface InterpretResult
        permits InterpretResult.SpecResult, InterpretResult.PatchResult,
                InterpretResult.ClarifyResult, InterpretResult.RejectedResult {

    String kind();
    String source();

    record InterpretRequest(
            @NotNull String text,
            String baseVersionId) {}

    record Confirmation(
            String field,
            String question,
            List<String> options,
            @JsonProperty("default") String defaultValue) {}

    record SpecResult(
            String kind,
            String source,
            HouseSpec spec,
            List<String> summary,
            boolean needsConfirm,
            List<Confirmation> confirmations) implements InterpretResult {
        public SpecResult(String source, HouseSpec spec, List<String> summary,
                          boolean needsConfirm, List<Confirmation> confirmations) {
            this("spec", source, spec, summary, needsConfirm, confirmations);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PatchResult(
            String kind,
            String source,
            String baseVersionId,
            List<SpecPatchOp> patch,
            HouseSpec spec,
            List<String> summary,
            String reason) implements InterpretResult {
        public PatchResult(String source, String baseVersionId, List<SpecPatchOp> patch,
                           HouseSpec spec, List<String> summary, String reason) {
            this("patch", source, baseVersionId, patch, spec, summary, reason);
        }
    }

    record ClarifyResult(
            String kind,
            String source,
            String field,
            String question,
            List<String> options) implements InterpretResult {
        public ClarifyResult(String source, String field, String question, List<String> options) {
            this("clarify", source, field, question, options);
        }
    }

    record RejectedResult(
            String kind,
            String source,
            String code,
            String reason) implements InterpretResult {
        public RejectedResult(String source, String code, String reason) {
            this("rejected", source, code, reason);
        }
    }
}

