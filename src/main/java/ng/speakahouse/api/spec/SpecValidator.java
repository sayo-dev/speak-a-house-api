package ng.speakahouse.api.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ng.speakahouse.api.common.ApiException;
import ng.speakahouse.api.common.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class SpecValidator {

    public void validate(HouseSpec spec) {
        List<Map<String, String>> errors = new ArrayList<>();
        if (spec == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "spec is required");
        }
        if (spec.plot() != null) {
            double min = spec.plot().unit() == HouseSpec.PlotUnit.ft ? 6.0 : 2.0;
            double max = spec.plot().unit() == HouseSpec.PlotUnit.ft ? 200.0 : 60.0;
            String unitLabel = spec.plot().unit().name();
            if (spec.plot().width() < min || spec.plot().width() > max) {
                errors.add(Map.of("field", "plot.width",
                        "message", "must be between " + (int) min + " and " + (int) max + " " + unitLabel));
            }
            if (spec.plot().depth() < min || spec.plot().depth() > max) {
                errors.add(Map.of("field", "plot.depth",
                        "message", "must be between " + (int) min + " and " + (int) max + " " + unitLabel));
            }
        }
        if (spec.rooms() != null) {
            if (spec.rooms().bedrooms() < 1 || spec.rooms().bedrooms() > 6) {
                errors.add(Map.of("field", "rooms.bedrooms", "message", "must be between 1 and 6"));
            }
            if (spec.rooms().ensuites() < 0 || spec.rooms().ensuites() > spec.rooms().bedrooms()) {
                errors.add(Map.of("field", "rooms.ensuites",
                        "message", "must be between 0 and rooms.bedrooms (" + spec.rooms().bedrooms() + ")"));
            }
        }
        if (spec.budget() != null && spec.budget().amount() <= 0) {
            errors.add(Map.of("field", "budget.amount", "message", "must be positive"));
        }
        if (!errors.isEmpty()) {
            String detail = errors.get(0).get("field") + " " + errors.get(0).get("message");
            throw new ApiException(ErrorCode.VALIDATION_FAILED, detail, Map.of("errors", errors));
        }
    }
}

