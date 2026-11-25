package ng.speakahouse.api.spec;

import java.util.List;
import java.util.Set;
import ng.speakahouse.api.common.ApiException;
import ng.speakahouse.api.common.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class SpecPatchApplier {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/plot/width", "/plot/depth", "/plot/unit", "/plot/unitConfirmed",
            "/rooms/bedrooms", "/rooms/ensuites", "/rooms/dining",
            "/features/shop", "/features/bq", "/features/garage", "/features/fence",
            "/roofing", "/finish", "/region", "/budget", "/budget/amount"
    );

    private final SpecValidator validator;

    public SpecPatchApplier(SpecValidator validator) {
        this.validator = validator;
    }

    public HouseSpec apply(HouseSpec base, List<SpecPatchOp> ops) {
        HouseSpec current = base;
        for (SpecPatchOp op : ops) {
            if (!"set".equals(op.op())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported patch op: " + op.op());
            }
            if (!ALLOWED_PATHS.contains(op.path())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported patch path: " + op.path());
            }
            current = applyOne(current, op.path(), op.value());
        }
        validator.validate(current);
        return current;
    }

    private HouseSpec applyOne(HouseSpec s, String path, Object value) {
        var plot = s.plot();
        var rooms = s.rooms();
        var features = s.features();
        var roofing = s.roofing();
        var finish = s.finish();
        var budget = s.budget();
        var region = s.region();

        switch (path) {
            case "/plot/width" -> plot = new HouseSpec.PlotSpec(asDouble(value), plot.depth(), plot.unit(), plot.unitConfirmed());
            case "/plot/depth" -> plot = new HouseSpec.PlotSpec(plot.width(), asDouble(value), plot.unit(), plot.unitConfirmed());
            case "/plot/unit" -> plot = new HouseSpec.PlotSpec(plot.width(), plot.depth(), HouseSpec.PlotUnit.valueOf(asString(value)), plot.unitConfirmed());
            case "/plot/unitConfirmed" -> plot = new HouseSpec.PlotSpec(plot.width(), plot.depth(), plot.unit(), asBoolean(value));
            case "/rooms/bedrooms" -> {
                int beds = asInt(value);
                int ensuites = Math.min(rooms.ensuites(), beds);
                rooms = new HouseSpec.RoomsSpec(beds, ensuites, rooms.dining());
            }
            case "/rooms/ensuites" -> rooms = new HouseSpec.RoomsSpec(rooms.bedrooms(), asInt(value), rooms.dining());
            case "/rooms/dining" -> rooms = new HouseSpec.RoomsSpec(rooms.bedrooms(), rooms.ensuites(), asBoolean(value));
            case "/features/shop" -> features = new HouseSpec.FeaturesSpec(asBoolean(value), features.bq(), features.garage(), features.fence());
            case "/features/bq" -> features = new HouseSpec.FeaturesSpec(features.shop(), asBoolean(value), features.garage(), features.fence());
            case "/features/garage" -> features = new HouseSpec.FeaturesSpec(features.shop(), features.bq(), asBoolean(value), features.fence());
            case "/features/fence" -> features = new HouseSpec.FeaturesSpec(features.shop(), features.bq(), features.garage(), asBoolean(value));
            case "/roofing" -> roofing = HouseSpec.RoofingType.valueOf(asString(value));
            case "/finish" -> finish = HouseSpec.FinishLevel.valueOf(asString(value));
            case "/region" -> region = HouseSpec.Region.valueOf(asString(value));
            case "/budget" -> {
                if (value == null) {
                    budget = null;
                } else if (value instanceof HouseSpec.BudgetSpec b) {
                    budget = b;
                } else {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid budget value");
                }
            }
            case "/budget/amount" -> budget = new HouseSpec.BudgetSpec(asLong(value), HouseSpec.Currency.NGN);
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported patch path: " + path);
        }
        return new HouseSpec(plot, s.houseType(), rooms, features, roofing, finish, budget, region);
    }

    private static double asDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(v));
    }

    private static int asInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    private static long asLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }

    private static boolean asBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static String asString(Object v) {
        if (v == null) throw new ApiException(ErrorCode.VALIDATION_FAILED, "Value must not be null");
        return String.valueOf(v);
    }
}

