package ng.speakahouse.api.interpret;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ng.speakahouse.api.spec.HouseSpec;
import ng.speakahouse.api.spec.SpecPatchApplier;
import ng.speakahouse.api.spec.SpecPatchOp;
import ng.speakahouse.api.spec.SpecValidator;
import org.springframework.stereotype.Service;

@Service
public class RuleBasedInterpreter {

    private final SpecPatchApplier patchApplier;
    private final SpecValidator specValidator;

    public RuleBasedInterpreter(SpecPatchApplier patchApplier, SpecValidator specValidator) {
        this.patchApplier = patchApplier;
        this.specValidator = specValidator;
    }

    public InterpretResult interpret(String text, String baseVersionId, HouseSpec baseSpec) {
        NigerianLexicon.LexResult lex = NigerianLexicon.parse(text);

        if (lex.injection()) {
            return new InterpretResult.RejectedResult(
                    "rules",
                    "INJECTION",
                    "Prices and building rules can't be changed from the chat. I can change the house design.");
        }
        if (lex.outOfScope()) {
            return new InterpretResult.RejectedResult(
                    "rules",
                    "OUT_OF_SCOPE",
                    "Right now I can design single-storey bungalows only. Duplexes are coming next.");
        }
        if (!lex.clarify().isEmpty()) {
            NigerianLexicon.Clarification c = lex.clarify().get(0);
            return new InterpretResult.ClarifyResult("rules", c.field(), c.question(), c.options());
        }

        if (baseSpec != null && baseVersionId != null) {
            return interpretPatch(lex, baseVersionId, baseSpec);
        }
        return interpretNewSpec(lex);
    }

    private InterpretResult interpretNewSpec(NigerianLexicon.LexResult lex) {
        if (lex.bedrooms() == null && lex.plot() == null) {
            return new InterpretResult.RejectedResult(
                    "rules",
                    "UNPARSEABLE",
                    "Tell me the number of bedrooms and your plot size (for example, \"3-bedroom bungalow on 50 by 100 ft in Abeokuta\").");
        }
        HouseSpec.PlotSpec plot = lex.plot() != null
                ? lex.plot()
                : new HouseSpec.PlotSpec(50, 100, HouseSpec.PlotUnit.ft, false);
        int bedrooms = lex.bedrooms() != null ? lex.bedrooms() : 3;
        boolean shop = lex.addFeatures().contains("shop");
        boolean bq = lex.addFeatures().contains("bq");
        boolean garage = lex.addFeatures().contains("garage");
        boolean fence = !lex.removeFeatures().contains("fence");
        HouseSpec.FeaturesSpec features = new HouseSpec.FeaturesSpec(shop, bq, garage, fence);
        HouseSpec.BudgetSpec budget = lex.budgetNgn() != null
                ? new HouseSpec.BudgetSpec(lex.budgetNgn(), HouseSpec.Currency.NGN)
                : null;
        HouseSpec.Region region = lex.region() != null ? lex.region() : HouseSpec.Region.abeokuta;
        HouseSpec.FinishLevel finish = lex.finish() != null ? lex.finish() : HouseSpec.FinishLevel.standard;

        HouseSpec spec = new HouseSpec(
                plot,
                HouseSpec.HouseType.bungalow,
                new HouseSpec.RoomsSpec(bedrooms, 0, Boolean.TRUE.equals(lex.dining())),
                features,
                lex.roofing() != null ? lex.roofing() : HouseSpec.RoofingType.hip,
                finish,
                budget,
                region);
        specValidator.validate(spec);

        List<String> summary = new ArrayList<>();
        summary.add(bedrooms + " bedroom" + (bedrooms == 1 ? "" : "s"));
        summary.add("Bungalow");
        String plotLabel = fmtNum(plot.width()) + " × " + fmtNum(plot.depth())
                + (plot.unitConfirmed() ? " " + plot.unit().name() : " (feet?)");
        summary.add("Plot " + plotLabel);
        if (shop) summary.add("Shop at the front");
        if (bq) summary.add("Boys' quarters");
        if (budget != null) {
            summary.add("Budget ₦" + NumberFormat.getIntegerInstance(Locale.US).format(budget.amount()));
        }
        summary.add(capitalize(region.name()));

        boolean needsConfirm = !plot.unitConfirmed();
        List<InterpretResult.Confirmation> confirmations = needsConfirm
                ? List.of(new InterpretResult.Confirmation(
                        "plot.unit",
                        "Is your plot " + fmtNum(plot.width()) + " × " + fmtNum(plot.depth())
                                + " feet or " + fmtNum(plot.width()) + " × " + fmtNum(plot.depth()) + " metres?",
                        List.of("ft", "m"),
                        "ft"))
                : List.of();

        return new InterpretResult.SpecResult("rules", spec, summary, needsConfirm, confirmations);
    }

    private InterpretResult interpretPatch(NigerianLexicon.LexResult lex, String baseVersionId, HouseSpec base) {
        if (lex.makeCheaper()) {
            return resolveMakeCheaper(baseVersionId, base);
        }

        List<SpecPatchOp> ops = new ArrayList<>();
        List<String> summary = new ArrayList<>();

        if (lex.finish() != null && lex.finish() != base.finish()) {
            ops.add(new SpecPatchOp("set", "/finish", lex.finish().name()));
            summary.add("Finish: " + base.finish().name() + " → " + lex.finish().name());
        }
        if (lex.roofing() != null && lex.roofing() != base.roofing()) {
            ops.add(new SpecPatchOp("set", "/roofing", lex.roofing().name()));
            summary.add("Roofing: " + base.roofing().name() + " → " + lex.roofing().name());
        }
        if (lex.bedrooms() != null && lex.bedrooms() != base.rooms().bedrooms()) {
            ops.add(new SpecPatchOp("set", "/rooms/bedrooms", lex.bedrooms()));
            summary.add("Bedrooms: " + base.rooms().bedrooms() + " → " + lex.bedrooms());
        }
        if (lex.budgetNgn() != null) {
            ops.add(new SpecPatchOp("set", "/budget/amount", lex.budgetNgn()));
            summary.add("Budget: ₦" + NumberFormat.getIntegerInstance(Locale.US).format(lex.budgetNgn()));
        }
        for (String feat : lex.addFeatures()) {
            boolean already = switch (feat) {
                case "shop" -> base.features().shop();
                case "bq" -> base.features().bq();
                case "garage" -> base.features().garage();
                case "fence" -> base.features().fence();
                default -> false;
            };
            if (!already) {
                ops.add(new SpecPatchOp("set", "/features/" + feat, true));
                summary.add("Add " + featureLabel(feat));
            }
        }
        for (String feat : lex.removeFeatures()) {
            boolean already = switch (feat) {
                case "shop" -> base.features().shop();
                case "bq" -> base.features().bq();
                case "garage" -> base.features().garage();
                case "fence" -> base.features().fence();
                default -> false;
            };
            if (already) {
                ops.add(new SpecPatchOp("set", "/features/" + feat, false));
                summary.add("Remove " + featureLabel(feat));
            }
        }

        if (ops.isEmpty()) {
            return new InterpretResult.RejectedResult(
                    "rules",
                    "UNPARSEABLE",
                    "I didn't catch what you want to change. Try \"add a boys' quarters\" or \"make it premium finish\".");
        }

        HouseSpec updated = patchApplier.apply(base, ops);
        return new InterpretResult.PatchResult("rules", baseVersionId, ops, updated, summary, null);
    }

    private InterpretResult resolveMakeCheaper(String baseVersionId, HouseSpec base) {
        List<SpecPatchOp> ops = new ArrayList<>();
        List<String> summary = new ArrayList<>();

        if (base.finish() == HouseSpec.FinishLevel.premium) {
            ops.add(new SpecPatchOp("set", "/finish", "standard"));
            summary.add("Biggest saving: finish premium → standard");
        } else if (base.finish() == HouseSpec.FinishLevel.standard) {
            ops.add(new SpecPatchOp("set", "/finish", "basic"));
            summary.add("Biggest saving: finish standard → basic");
        } else if (base.roofing() == HouseSpec.RoofingType.hip) {
            ops.add(new SpecPatchOp("set", "/roofing", "gable"));
            summary.add("Saving: roofing hip → gable");
        } else if (base.roofing() == HouseSpec.RoofingType.gable) {
            ops.add(new SpecPatchOp("set", "/roofing", "flat"));
            summary.add("Saving: roofing gable → flat");
        } else if (base.features().garage()) {
            ops.add(new SpecPatchOp("set", "/features/garage", false));
            summary.add("Saving: remove garage");
        } else if (base.features().bq()) {
            ops.add(new SpecPatchOp("set", "/features/bq", false));
            summary.add("Saving: remove boys' quarters");
        } else {
            return new InterpretResult.RejectedResult(
                    "rules",
                    "UNPARSEABLE",
                    "Already at basic finish and simplest roof. You could reduce bedrooms or remove a feature.");
        }

        HouseSpec updated = patchApplier.apply(base, ops);
        return new InterpretResult.PatchResult("rules", baseVersionId, ops, updated, summary, "make_cheaper");
    }

    private static String featureLabel(String key) {
        return switch (key) {
            case "bq" -> "boys' quarters";
            case "shop" -> "shop";
            case "garage" -> "garage";
            case "fence" -> "perimeter fence";
            default -> key;
        };
    }

    private static String fmtNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

