package ng.speakahouse.api.cost;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ng.speakahouse.api.layout.LayoutEngine;
import ng.speakahouse.api.layout.Plan;
import ng.speakahouse.api.spec.HouseSpec;
import org.springframework.stereotype.Service;

@Service
public class CostEngine {

    private static final double LABOUR_SHARE = 0.30;
    private static final double LOW_FACTOR = 0.90;
    private static final double HIGH_FACTOR = 1.20;

    private static final Map<String, Double> ROOF_FACTOR = Map.of(
            "flat", 1.05,
            "gable", 1.18,
            "hip", 1.22
    );

    private static final Map<String, String> COVER_LABEL = Map.of(
            "basic", "aluminium long-span (basic)",
            "standard", "aluminium long-span",
            "premium", "stone-coated tiles"
    );

    private final PriceItemRepository priceRepo;

    public CostEngine(PriceItemRepository priceRepo) {
        this.priceRepo = priceRepo;
    }

    public CostEstimate estimate(HouseSpec spec, Plan plan) {
        String region = spec.region().name();
        String finish = spec.finish().name();
        Map<String, PriceItemEntity> prices = priceRepo.findByRegionAndFinishIn(region, List.of(finish, "any"))
                .stream()
                .collect(Collectors.toMap(
                        PriceItemEntity::getPriceKey,
                        p -> p,
                        (a, b) -> "any".equals(a.getFinish()) ? b : a));

        Map<String, List<Plan.Opening>> opByWall = plan.openings().stream()
                .collect(Collectors.groupingBy(Plan.Opening::wallId));

        double extArea = 0.0;
        double intArea = 0.0;
        double wallLen = 0.0;
        for (Plan.Wall w : plan.walls()) {
            double len = Math.hypot(w.x2() - w.x1(), w.y2() - w.y1());
            wallLen += len;
            double openArea = opByWall.getOrDefault(w.id(), List.of()).stream()
                    .mapToDouble(o -> o.width() * o.height())
                    .sum();
            double a = len * LayoutEngine.WALL_H - openArea;
            if (w.exterior()) {
                extArea += a;
            } else {
                intArea += a;
            }
        }

        double floorArea = plan.rooms().stream().mapToDouble(Plan.Room::area).sum();
        double annexArea = plan.rooms().stream()
                .filter(r -> "annex".equals(r.zone()))
                .mapToDouble(Plan.Room::area)
                .sum();
        double roofPlan = plan.footprint().width() * plan.footprint().depth() + annexArea;
        double roofArea = roofPlan * ROOF_FACTOR.get(spec.roofing().name());
        int doors = (int) plan.openings().stream().filter(o -> "door".equals(o.type())).count();
        int windows = (int) plan.openings().stream().filter(o -> "window".equals(o.type())).count();

        double blocks9 = Math.ceil(extArea * 10.0 + wallLen * 0.6 * 10.0 * 0.5);
        double blocks6 = Math.ceil(intArea * 10.0);
        double cement = Math.ceil((blocks9 + blocks6) / 30.0 + (extArea + intArea) * 2.0 / 10.0);
        double concrete = wallLen * 0.675 * 0.225 + floorArea * 0.15;

        List<Map.Entry<String, Double>> takeOff = new ArrayList<>();
        takeOff.add(Map.entry("concrete_m3", concrete));
        takeOff.add(Map.entry("block_9in", blocks9));
        takeOff.add(Map.entry("block_6in", blocks6));
        takeOff.add(Map.entry("cement_bag", cement));
        takeOff.add(Map.entry("roof_timber_m2", roofArea));
        takeOff.add(Map.entry("roof_cover_m2", roofArea));
        takeOff.add(Map.entry("door_each", (double) doors));
        takeOff.add(Map.entry("window_each", (double) windows));
        takeOff.add(Map.entry("floor_finish_m2", floorArea));
        takeOff.add(Map.entry("ceiling_m2", floorArea));
        takeOff.add(Map.entry("paint_m2", (extArea + intArea) * 2.0));
        takeOff.add(Map.entry("services_m2", floorArea));
        if (plan.fence() != null) {
            double fenceLen = 2.0 * (plan.plot().width() + plan.plot().depth()) - plan.fence().gateWidth();
            takeOff.add(Map.entry("fence_m", fenceLen));
        }

        List<CostEstimate.CostLine> lines = new ArrayList<>();
        List<String> missingPrices = new ArrayList<>();
        boolean allVerified = true;
        LocalDate latestObserved = null;

        for (var entry : takeOff) {
            String key = entry.getKey();
            PriceItemEntity item = prices.get(key);
            if (item == null) {
                missingPrices.add(key);
                allVerified = false;
                continue;
            }
            double qty = LayoutEngine.round3(entry.getValue());
            long amount = Math.round(qty * item.getRateNgn());
            String desc = "roof_cover_m2".equals(key)
                    ? "Roof covering, " + COVER_LABEL.get(finish)
                    : item.getDescription();
            if (!item.isVerified()) {
                allVerified = false;
            }
            if (latestObserved == null || item.getObservedOn().isAfter(latestObserved)) {
                latestObserved = item.getObservedOn();
            }
            lines.add(new CostEstimate.CostLine(
                    key,
                    item.getGrp(),
                    desc,
                    qty,
                    item.getUnit(),
                    item.getRateNgn(),
                    amount,
                    item.getSource(),
                    item.getObservedOn().toString(),
                    item.isVerified()));
        }

        long materials = lines.stream().mapToLong(CostEstimate.CostLine::amount).sum();
        long labour = Math.round(materials * LABOUR_SHARE);
        String asOf = latestObserved != null ? latestObserved.toString() : LocalDate.now().toString();
        lines.add(new CostEstimate.CostLine(
                "labour",
                "labour",
                "Labour (30% of materials, sample assumption)",
                1,
                "lot",
                labour,
                labour,
                "Sample baseline (team estimate), not supplier-verified",
                asOf,
                false));

        Map<String, Long> groupTotals = new LinkedHashMap<>();
        for (CostEstimate.CostLine l : lines) {
            groupTotals.merge(l.group(), l.amount(), Long::sum);
        }
        long expected = groupTotals.values().stream().mapToLong(Long::longValue).sum();
        long low = Math.round(expected * LOW_FACTOR);
        long high = Math.round(expected * HIGH_FACTOR);

        CostEstimate.BudgetStatus budget = null;
        if (spec.budget() != null) {
            long b = spec.budget().amount();
            String status = expected > b ? "over" : (high > b ? "near" : "within");
            budget = new CostEstimate.BudgetStatus(b, status, b - expected);
        }

        List<CostEstimate.GroupTotal> groups = groupTotals.entrySet().stream()
                .map(e -> new CostEstimate.GroupTotal(e.getKey(), e.getValue()))
                .toList();

        List<CostEstimate.Driver> drivers = groupTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .map(e -> new CostEstimate.Driver(
                        e.getKey(),
                        e.getValue(),
                        expected > 0 ? LayoutEngine.round3((double) e.getValue() / expected) : 0.0))
                .toList();

        return new CostEstimate(
                "NGN",
                region,
                finish,
                allVerified && !lines.isEmpty(),
                asOf,
                lines,
                groups,
                low,
                expected,
                high,
                new CostEstimate.Range(LOW_FACTOR, HIGH_FACTOR),
                budget,
                drivers,
                missingPrices,
                new CostEstimate.Quantities(
                        LayoutEngine.round3(floorArea),
                        LayoutEngine.round3(roofArea),
                        LayoutEngine.round3(extArea),
                        LayoutEngine.round3(intArea),
                        doors,
                        windows));
    }
}

