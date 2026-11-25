package ng.speakahouse.api.spec;

import jakarta.validation.constraints.NotBlank;

public record SpecPatchOp(
        @NotBlank String op,
        @NotBlank String path,
        Object value) {
}

