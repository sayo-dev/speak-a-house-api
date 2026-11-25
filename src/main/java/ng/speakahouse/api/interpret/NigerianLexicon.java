package ng.speakahouse.api.interpret;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ng.speakahouse.api.spec.HouseSpec;

public final class NigerianLexicon {

    private static final Map<String, Integer> NUMBER_WORDS = Map.of(
            "one", 1, "two", 2, "three", 3, "four", 4,
            "five", 5, "six", 6, "seven", 7, "eight", 8
    );

    private static final Map<String, Pattern> FEATURE_PATTERNS = new LinkedHashMap<>();
    static {
        FEATURE_PATTERNS.put("bq", Pattern.compile("\\b(b\\.?\\s?q|boys?'?\\s*quarters?|boy'?s\\s*quarters?|servants?'?\\s*quarters?)\\b"));
        FEATURE_PATTERNS.put("shop", Pattern.compile("\\b(shop|store\\s+(in|at)\\s+(the\\s+)?front|corner\\s+shop|kiosk)\\b"));
        FEATURE_PATTERNS.put("garage", Pattern.compile("\\b(garage|car\\s?port|car\\s?park|parking)\\b"));
        FEATURE_PATTERNS.put("fence", Pattern.compile("\\b(fence|fenced|perimeter\\s+wall|gate\\s?house|compound\\s+wall)\\b"));
    }

    private static final Pattern REMOVE_VERB = Pattern.compile(
            "\\b(remove|comot|commot|take\\s+(out|away)|drop|no\\s+need|don'?t\\s+want|without)\\b");

    private static final Pattern INJECTION = Pattern.compile(
            "\\b(ignore|disregard|forget|override|bypass)\\b.{0,40}\\b(rules?|instructions?|prompt|system|setbacks?)\\b"
                    + "|\\b(set|make)\\b.{0,30}\\b(price|cost|cement|blocks?)\\b.{0,20}\\b(zero|free|0)\\b"
                    + "|\\bsystem\\s*prompt\\b|\\byou\\s+are\\s+now\\b");

    private static final Pattern OUT_OF_SCOPE = Pattern.compile(
            "\\b(duplex|storey|story|two[\\s-]?storey|2[\\s-]?storey|three[\\s-]?storey|penthouse|mansion|block\\s+of\\s+flats|upstairs)\\b");

    private static final Pattern BAR_PATTERN = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*bar\\b");
    private static final Pattern MILLION_PATTERN = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\s*(m|mil|milli|million|mn)\\b(?!\\s*(by|x)\\b)");
    private static final Pattern METRE_WORD = Pattern.compile("\\b(metre|meter)s?\\b");
    private static final Pattern BUDGET_CTX = Pattern.compile("\\b(budget|naira|ngn|cost|spend|have|get)\\b");
    private static final Pattern MILLION_WORD = Pattern.compile("(million|mil|milli|mn)");
    private static final Pattern PLAIN_BUDGET_1 = Pattern.compile("\\b(?:budget|naira|ngn)\\s*(?:of|is|:)?\\s*(\\d{1,3}(?:,\\d{3})+|\\d{6,})\\b");
    private static final Pattern PLAIN_BUDGET_2 = Pattern.compile("\\b(\\d{1,3}(?:,\\d{3})+|\\d{6,})\\s*(?:naira|ngn)\\b");
    private static final Pattern PLOT_PATTERN = Pattern.compile(
            "\\b(\\d{2,3}(?:\\.\\d+)?)\\s*(?:ft|feet|foot|m|metres?|meters?)?\\s*(?:by|x)\\s*(\\d{2,3}(?:\\.\\d+)?)\\s*(ft|feet|foot|m\\b|metres?|meters?)?");
    private static final Pattern VAGUE_PLOT = Pattern.compile("\\b(half|full|one|a)\\s+plot\\b");
    private static final Pattern SELF_CON = Pattern.compile("\\bself[\\s-]?con(tain(ed)?)?\\b");
    private static final Pattern ROOM_PARLOUR = Pattern.compile("\\broom\\s+(and|&|n)\\s+parlou?r\\b");
    private static final Pattern BEDROOM_PATTERN = Pattern.compile("\\b(\\d|one|two|three|four|five|six|seven|eight)[\\s-]*(bed(room)?s?|br)\\b");
    private static final Pattern PREMIUM_FINISH = Pattern.compile("\\b(premium|luxury|stone[\\s-]?coated|gerard|p\\.?o\\.?p\\.?|high[\\s-]?end|executive)\\b");
    private static final Pattern BASIC_FINISH = Pattern.compile("\\b(basic|simple|low[\\s-]?cost|economy)\\b");
    private static final Pattern STANDARD_FINISH = Pattern.compile("\\b(standard|normal|regular)\\s+finish\\b");
    private static final Pattern CHEAPER = Pattern.compile(
            "\\b(make\\s+(it|am|e)\\s+cheap(er)?|cheaper|reduce\\s+(the\\s+)?(cost|price|am)|cut\\s+(the\\s+)?cost|too\\s+(expensive|cost)|e\\s+too\\s+cost|lower\\s+(the\\s+)?(cost|price)|bring\\s+(it|am)\\s+down)\\b");

    private NigerianLexicon() {}

    public record Clarification(String field, String question, List<String> options) {}

    public record LexResult(
            String normalized,
            boolean injection,
            boolean outOfScope,
            HouseSpec.PlotSpec plot,
            Integer bedrooms,
            Long budgetNgn,
            HouseSpec.FinishLevel finish,
            HouseSpec.RoofingType roofing,
            HouseSpec.Region region,
            Boolean dining,
            List<String> addFeatures,
            List<String> removeFeatures,
            boolean makeCheaper,
            List<Clarification> clarify) {}

    public static String normalize(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT)
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace("₦", " naira ")
                .replace("×", " x ")
                .replace("✕", " x ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static LexResult parse(String raw) {
        String t = normalize(raw);
        boolean injection = INJECTION.matcher(t).find();
        boolean outOfScope = OUT_OF_SCOPE.matcher(t).find();
        List<Clarification> clarify = new ArrayList<>();

        HouseSpec.PlotSpec plot = null;
        Matcher pm = PLOT_PATTERN.matcher(t);
        if (pm.find()) {
            String unitWord = pm.group(3) != null ? pm.group(3) : "";
            HouseSpec.PlotUnit unit = unitWord.startsWith("m") ? HouseSpec.PlotUnit.m : HouseSpec.PlotUnit.ft;
            plot = new HouseSpec.PlotSpec(
                    Double.parseDouble(pm.group(1)),
                    Double.parseDouble(pm.group(2)),
                    unit,
                    !unitWord.isEmpty());
        } else if (VAGUE_PLOT.matcher(t).find()) {
            clarify.add(new Clarification(
                    "plot",
                    "Plot sizes differ by area. What size is your plot?",
                    List.of("50 × 100 ft", "60 × 120 ft", "100 × 100 ft", "Let me type it")));
        }

        Long budgetNgn = null;
        Matcher bm = BAR_PATTERN.matcher(t);
        if (bm.find()) {
            double n = Double.parseDouble(bm.group(1));
            long amount = Math.round(n * 1_000_000L);
            String formatted = "₦" + NumberFormat.getIntegerInstance(Locale.US).format(amount);
            clarify.add(new Clarification(
                    "budget",
                    "By \"" + bm.group(1) + " bar\", do you mean " + formatted + "?",
                    List.of(formatted, "Let me type the amount")));
        } else {
            Matcher mm = MILLION_PATTERN.matcher(t);
            if (mm.find()) {
                int start = mm.start();
                String after = t.substring(start, Math.min(t.length(), start + 12));
                if (!METRE_WORD.matcher(after).find()) {
                    String ctx = t.substring(Math.max(0, start - 25), Math.min(t.length(), mm.end() + 10));
                    if (BUDGET_CTX.matcher(ctx).find() || MILLION_WORD.matcher(mm.group(2)).find()) {
                        budgetNgn = Math.round(Double.parseDouble(mm.group(1)) * 1_000_000L);
                    }
                }
            }
            if (budgetNgn == null) {
                Matcher p1 = PLAIN_BUDGET_1.matcher(t);
                Matcher p2 = PLAIN_BUDGET_2.matcher(t);
                if (p1.find()) {
                    budgetNgn = Long.parseLong(p1.group(1).replace(",", ""));
                } else if (p2.find()) {
                    budgetNgn = Long.parseLong(p2.group(1).replace(",", ""));
                }
            }
        }

        Integer bedrooms = null;
        if (SELF_CON.matcher(t).find() || ROOM_PARLOUR.matcher(t).find()) {
            bedrooms = 1;
        } else {
            Matcher bedM = BEDROOM_PATTERN.matcher(t);
            if (bedM.find()) {
                String tok = bedM.group(1);
                bedrooms = NUMBER_WORDS.containsKey(tok) ? NUMBER_WORDS.get(tok) : Integer.parseInt(tok);
            }
        }

        HouseSpec.FinishLevel finish = null;
        if (PREMIUM_FINISH.matcher(t).find()) {
            finish = HouseSpec.FinishLevel.premium;
        } else if (BASIC_FINISH.matcher(t).find()) {
            finish = HouseSpec.FinishLevel.basic;
        } else if (STANDARD_FINISH.matcher(t).find()) {
            finish = HouseSpec.FinishLevel.standard;
        }

        HouseSpec.RoofingType roofing = null;
        if (t.contains("flat roof")) roofing = HouseSpec.RoofingType.flat;
        else if (t.contains("gable")) roofing = HouseSpec.RoofingType.gable;
        else if (t.contains("hip")) roofing = HouseSpec.RoofingType.hip;

        HouseSpec.Region region = null;
        if (t.contains("lagos")) region = HouseSpec.Region.lagos;
        else if (t.contains("abeokuta")) region = HouseSpec.Region.abeokuta;

        Boolean dining = t.contains("dining") ? Boolean.TRUE : null;
        boolean makeCheaper = CHEAPER.matcher(t).find();

        List<String> addFeatures = new ArrayList<>();
        List<String> removeFeatures = new ArrayList<>();
        for (var entry : FEATURE_PATTERNS.entrySet()) {
            Matcher fm = entry.getValue().matcher(t);
            if (fm.find()) {
                String before = t.substring(Math.max(0, fm.start() - 30), fm.start());
                if (REMOVE_VERB.matcher(before).find()) {
                    removeFeatures.add(entry.getKey());
                } else {
                    addFeatures.add(entry.getKey());
                }
            }
        }

        return new LexResult(
                t, injection, outOfScope, plot, bedrooms, budgetNgn,
                finish, roofing, region, dining, addFeatures, removeFeatures, makeCheaper, clarify);
    }
}

