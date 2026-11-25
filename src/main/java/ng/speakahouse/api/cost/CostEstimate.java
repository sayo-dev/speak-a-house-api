package ng.speakahouse.api.cost;

import java.util.List;

public record CostEstimate(
        String currency,
        String region,
        String finish,
        boolean priceDataVerified,
        String priceAsOf,
        List<CostLine> lines,
        List<GroupTotal> groups,
        long low,
        long expected,
        long high,
        Range range,
        BudgetStatus budget,
        List<Driver> drivers,
        List<String> missingPrices,
        Quantities quantities) {

    public record CostLine(
            String key,
            String group,
            String description,
            double quantity,
            String unit,
            long rate,
            long amount,
            String priceSource,
            String observedOn,
            boolean verified) {}

    public record GroupTotal(String group, long amount) {}

    public record Range(double lowFactor, double highFactor) {}

    public record BudgetStatus(long amount, String status, long delta) {}

    public record Driver(String group, long amount, double share) {}

    public record Quantities(
            double floorAreaM2,
            double roofAreaM2,
            double externalWallAreaM2,
            double internalWallAreaM2,
            int doors,
            int windows) {}
}

