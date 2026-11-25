package ng.speakahouse.api.spec;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HouseSpec(
        @NotNull @Valid PlotSpec plot,
        @NotNull HouseType houseType,
        @NotNull @Valid RoomsSpec rooms,
        @NotNull @Valid FeaturesSpec features,
        @NotNull RoofingType roofing,
        @NotNull FinishLevel finish,
        @Valid BudgetSpec budget,
        @NotNull Region region) {

    public enum PlotUnit { ft, m }
    public enum HouseType { bungalow }
    public enum RoofingType { flat, gable, hip }
    public enum FinishLevel { basic, standard, premium }
    public enum Region { abeokuta, lagos }
    public enum Currency { NGN }

    public record PlotSpec(
            @Positive double width,
            @Positive double depth,
            @NotNull PlotUnit unit,
            boolean unitConfirmed) {}

    public record RoomsSpec(
            @Min(value = 1, message = "must be between 1 and 6")
            @Max(value = 6, message = "must be between 1 and 6")
            int bedrooms,
            @Min(0) int ensuites,
            boolean dining) {}

    public record FeaturesSpec(
            boolean shop,
            boolean bq,
            boolean garage,
            boolean fence) {}

    public record BudgetSpec(
            @Positive long amount,
            @NotNull Currency currency) {}

    public static HouseSpec defaults(PlotSpec plot, int bedrooms, FeaturesSpec features,
                                     FinishLevel finish, BudgetSpec budget, Region region) {
        return new HouseSpec(
                plot,
                HouseType.bungalow,
                new RoomsSpec(bedrooms, 0, false),
                features != null ? features : new FeaturesSpec(false, false, false, true),
                RoofingType.hip,
                finish != null ? finish : FinishLevel.standard,
                budget,
                region != null ? region : Region.abeokuta);
    }
}

