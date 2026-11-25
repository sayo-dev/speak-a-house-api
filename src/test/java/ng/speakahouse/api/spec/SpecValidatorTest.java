package ng.speakahouse.api.spec;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import ng.speakahouse.api.common.ApiException;
import ng.speakahouse.api.common.ErrorCode;
import org.junit.jupiter.api.Test;

class SpecValidatorTest {

    private final SpecValidator validator = new SpecValidator();
    private final SpecPatchApplier patchApplier = new SpecPatchApplier(validator);

    private static HouseSpec validSpec() {
        return new HouseSpec(
                new HouseSpec.PlotSpec(50, 100, HouseSpec.PlotUnit.ft, true),
                HouseSpec.HouseType.bungalow,
                new HouseSpec.RoomsSpec(3, 1, false),
                new HouseSpec.FeaturesSpec(true, false, false, true),
                HouseSpec.RoofingType.hip,
                HouseSpec.FinishLevel.standard,
                new HouseSpec.BudgetSpec(50_000_000, HouseSpec.Currency.NGN),
                HouseSpec.Region.abeokuta);
    }

    @Test
    void validSpecPasses() {
        assertDoesNotThrow(() -> validator.validate(validSpec()));
    }

    @Test
    void ensuitesExceedingBedroomsFails() {
        HouseSpec bad = new HouseSpec(
                validSpec().plot(), HouseSpec.HouseType.bungalow,
                new HouseSpec.RoomsSpec(2, 3, false),
                validSpec().features(), validSpec().roofing(), validSpec().finish(),
                validSpec().budget(), validSpec().region());
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(bad));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.code);
        assertTrue(ex.getMessage().contains("rooms.ensuites"));
    }

    @Test
    void plotOutOfRangeForFeetFails() {
        HouseSpec bad = new HouseSpec(
                new HouseSpec.PlotSpec(300, 100, HouseSpec.PlotUnit.ft, true),
                HouseSpec.HouseType.bungalow, validSpec().rooms(), validSpec().features(),
                validSpec().roofing(), validSpec().finish(), validSpec().budget(), validSpec().region());
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(bad));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.code);
        assertTrue(ex.getMessage().contains("plot.width"));
    }

    @Test
    void plotOutOfRangeForMetresFails() {
        HouseSpec bad = new HouseSpec(
                new HouseSpec.PlotSpec(15, 75, HouseSpec.PlotUnit.m, true),
                HouseSpec.HouseType.bungalow, validSpec().rooms(), validSpec().features(),
                validSpec().roofing(), validSpec().finish(), validSpec().budget(), validSpec().region());
        ApiException ex = assertThrows(ApiException.class, () -> validator.validate(bad));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.code);
        assertTrue(ex.getMessage().contains("plot.depth"));
    }

    @Test
    void patchApplierUpdatesWhitelistedPathAndRejectsUnknownPath() {
        HouseSpec updated = patchApplier.apply(
                validSpec(), List.of(new SpecPatchOp("set", "/features/bq", true)));
        assertTrue(updated.features().bq());

        ApiException ex = assertThrows(ApiException.class, () ->
                patchApplier.apply(validSpec(), List.of(new SpecPatchOp("set", "/price/cement", 0))));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.code);
    }
}

