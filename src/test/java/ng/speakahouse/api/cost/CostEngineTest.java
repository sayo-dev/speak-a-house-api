package ng.speakahouse.api.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import ng.speakahouse.api.layout.LayoutEngine;
import ng.speakahouse.api.layout.Plan;
import ng.speakahouse.api.spec.HouseSpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class CostEngineTest {

    @Autowired CostEngine costEngine;
    @Autowired LayoutEngine layoutEngine;
    @Autowired ObjectMapper mapper;

    @Test
    void matchesVersion1Through3FixtureEstimates() throws Exception {
        for (String fixture : new String[]{"/fixtures/version.1.json", "/fixtures/version.2.json", "/fixtures/version.3.json"}) {
            try (InputStream is = getClass().getResourceAsStream(fixture)) {
                JsonNode root = mapper.readTree(is);
                HouseSpec spec = mapper.treeToValue(root.get("spec"), HouseSpec.class);
                long expectedCost = root.get("estimate").get("expected").asLong();
                String expectedStatus = root.get("estimate").get("budget").get("status").asText();

                Plan plan = layoutEngine.layout(spec);
                CostEstimate actual = costEngine.estimate(spec, plan);
                assertEquals(expectedCost, actual.expected(), "Mismatch for " + fixture);
                assertEquals(expectedStatus, actual.budget().status(), "Status mismatch for " + fixture);
                assertTrue(actual.missingPrices().isEmpty());
            }
        }
    }
}

