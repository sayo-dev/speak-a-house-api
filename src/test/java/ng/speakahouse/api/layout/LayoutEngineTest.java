package ng.speakahouse.api.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Random;
import ng.speakahouse.api.common.ApiException;
import ng.speakahouse.api.common.ErrorCode;
import ng.speakahouse.api.spec.HouseSpec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class LayoutEngineTest {

    private final LayoutEngine engine = new LayoutEngine();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void matchesVersion1FixtureExactly() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/version.1.json")) {
            JsonNode root = mapper.readTree(is);
            HouseSpec spec = mapper.treeToValue(root.get("spec"), HouseSpec.class);
            Plan expected = mapper.treeToValue(root.get("plan"), Plan.class);

            Plan actual = engine.layout(spec);
            assertEquals(expected.rooms().size(), actual.rooms().size());
            assertEquals(expected.walls().size(), actual.walls().size());
            assertEquals(expected.openings().size(), actual.openings().size());
            for (int i = 0; i < expected.rooms().size(); i++) {
                Plan.Room er = expected.rooms().get(i);
                Plan.Room ar = actual.rooms().get(i);
                assertEquals(er.label(), ar.label());
                assertEquals(er.x(), ar.x(), 0.01);
                assertEquals(er.y(), ar.y(), 0.01);
                assertEquals(er.width(), ar.width(), 0.01);
                assertEquals(er.depth(), ar.depth(), 0.01);
            }
        }
    }

    @Test
    void matchesVersion2FixtureWithBq() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/version.2.json")) {
            JsonNode root = mapper.readTree(is);
            HouseSpec spec = mapper.treeToValue(root.get("spec"), HouseSpec.class);
            Plan expected = mapper.treeToValue(root.get("plan"), Plan.class);

            Plan actual = engine.layout(spec);
            assertEquals(expected.rooms().size(), actual.rooms().size());
            assertEquals(expected.walls().size(), actual.walls().size());
            assertEquals(expected.openings().size(), actual.openings().size());
        }
    }

    @Test
    void infeasibleSixBedsAndBqOnSmallPlotReturns422Violations() {
        HouseSpec spec = new HouseSpec(
                new HouseSpec.PlotSpec(40, 60, HouseSpec.PlotUnit.ft, true),
                HouseSpec.HouseType.bungalow,
                new HouseSpec.RoomsSpec(6, 0, false),
                new HouseSpec.FeaturesSpec(false, true, false, true),
                HouseSpec.RoofingType.hip,
                HouseSpec.FinishLevel.standard,
                null,
                HouseSpec.Region.abeokuta);

        ApiException ex = assertThrows(ApiException.class, () -> engine.layout(spec));
        assertEquals(ErrorCode.LAYOUT_INFEASIBLE, ex.code);
        @SuppressWarnings("unchecked")
        List<LayoutEngine.Violation> violations = (List<LayoutEngine.Violation>) ex.extra.get("violations");
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> "annex_gap".equals(v.rule())));
    }

    @Test
    void propertyTest200RandomValidSpecsNeverOverlapOrCrash() {
        Random rng = new Random(42);
        for (int i = 0; i < 200; i++) {
            boolean useFeet = rng.nextBoolean();
            double w = useFeet ? 30 + rng.nextInt(120) : 10 + rng.nextInt(35);
            double d = useFeet ? 40 + rng.nextInt(140) : 12 + rng.nextInt(40);
            int beds = 1 + rng.nextInt(6);
            HouseSpec spec = new HouseSpec(
                    new HouseSpec.PlotSpec(w, d, useFeet ? HouseSpec.PlotUnit.ft : HouseSpec.PlotUnit.m, true),
                    HouseSpec.HouseType.bungalow,
                    new HouseSpec.RoomsSpec(beds, rng.nextInt(beds + 1), rng.nextBoolean()),
                    new HouseSpec.FeaturesSpec(rng.nextBoolean(), rng.nextBoolean(), rng.nextBoolean(), rng.nextBoolean()),
                    HouseSpec.RoofingType.values()[rng.nextInt(3)],
                    HouseSpec.FinishLevel.values()[rng.nextInt(3)],
                    null,
                    HouseSpec.Region.abeokuta);
            try {
                Plan plan = engine.layout(spec);
                List<Plan.Room> rooms = plan.rooms();
                for (int a = 0; a < rooms.size(); a++) {
                    Plan.Room ra = rooms.get(a);
                    assertTrue(ra.x() >= plan.buildable().x() - 1e-6);
                    assertTrue(ra.y() >= plan.buildable().y() - 1e-6);
                    for (int b = a + 1; b < rooms.size(); b++) {
                        Plan.Room rb = rooms.get(b);
                        boolean overlap = ra.x() < rb.x() + rb.width() - 1e-6
                                && rb.x() < ra.x() + ra.width() - 1e-6
                                && ra.y() < rb.y() + rb.depth() - 1e-6
                                && rb.y() < ra.y() + ra.depth() - 1e-6;
                        assertFalse(overlap, "Overlap in iteration " + i);
                    }
                }
            } catch (ApiException ex) {
                assertEquals(ErrorCode.LAYOUT_INFEASIBLE, ex.code);
            }
        }
    }
}

