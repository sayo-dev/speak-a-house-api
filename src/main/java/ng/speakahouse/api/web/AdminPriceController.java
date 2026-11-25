package ng.speakahouse.api.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import ng.speakahouse.api.common.ApiException;
import ng.speakahouse.api.common.ErrorCode;
import ng.speakahouse.api.config.AppProperties;
import ng.speakahouse.api.cost.PriceItemEntity;
import ng.speakahouse.api.cost.PriceItemRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/prices")
public class AdminPriceController {

    private static final Set<String> VALID_REGIONS = Set.of("abeokuta", "lagos");
    private static final Set<String> VALID_FINISHES = Set.of("basic", "standard", "premium", "any");

    public record RejectedRow(int row, String reason) {}
    public record ImportResponse(int imported, List<RejectedRow> rejected) {}

    private final AppProperties props;
    private final PriceItemRepository priceRepo;

    public AdminPriceController(AppProperties props, PriceItemRepository priceRepo) {
        this.props = props;
        this.priceRepo = priceRepo;
    }

    @PostMapping(value = "/import", consumes = {"text/csv", "text/plain"})
    @Transactional
    public ImportResponse importCsv(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
            @RequestBody String csv) {
        if (props.adminKey() == null || props.adminKey().isBlank()
                || adminKey == null || !props.adminKey().equals(adminKey)) {
            throw new ApiException(ErrorCode.INVALID_SESSION_TOKEN, "Invalid or missing admin key");
        }

        String[] lines = csv.split("\\r?\\n");
        int imported = 0;
        List<RejectedRow> rejected = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i].trim();
            if (raw.isEmpty()) continue;
            if (i == 0 && raw.toLowerCase().startsWith("price_key,")) continue;

            List<String> cols = parseCsvLine(raw);
            if (cols.size() < 11) {
                rejected.add(new RejectedRow(i + 1, "Expected 11 columns, got " + cols.size()));
                continue;
            }
            try {
                String priceKey = cols.get(0).trim();
                String grp = cols.get(1).trim();
                String desc = cols.get(2).trim();
                String unit = cols.get(3).trim();
                String region = cols.get(4).trim().toLowerCase();
                String finish = cols.get(5).trim().toLowerCase();
                long rate = Long.parseLong(cols.get(6).trim());
                String source = cols.get(7).trim();
                String supplier = cols.get(8).trim();
                LocalDate observedOn = LocalDate.parse(cols.get(9).trim());
                boolean verified = Boolean.parseBoolean(cols.get(10).trim());

                if (priceKey.isEmpty() || grp.isEmpty() || desc.isEmpty() || unit.isEmpty() || source.isEmpty()) {
                    rejected.add(new RejectedRow(i + 1, "Required text field is blank"));
                    continue;
                }
                if (!VALID_REGIONS.contains(region)) {
                    rejected.add(new RejectedRow(i + 1, "Invalid region: " + region));
                    continue;
                }
                if (!VALID_FINISHES.contains(finish)) {
                    rejected.add(new RejectedRow(i + 1, "Invalid finish: " + finish));
                    continue;
                }
                if (rate <= 0) {
                    rejected.add(new RejectedRow(i + 1, "rate_ngn must be > 0"));
                    continue;
                }

                PriceItemEntity entity = priceRepo.findByPriceKeyAndRegionAndFinish(priceKey, region, finish)
                        .orElseGet(() -> new PriceItemEntity(
                                priceKey, grp, desc, unit, region, finish,
                                rate, source, supplier.isEmpty() ? null : supplier, observedOn, verified));
                entity.setGrp(grp);
                entity.setDescription(desc);
                entity.setUnit(unit);
                entity.setRateNgn(rate);
                entity.setSource(source);
                entity.setSupplier(supplier.isEmpty() ? null : supplier);
                entity.setObservedOn(observedOn);
                entity.setVerified(verified);
                priceRepo.save(entity);
                imported++;
            } catch (Exception ex) {
                rejected.add(new RejectedRow(i + 1, ex.getMessage()));
            }
        }
        return new ImportResponse(imported, rejected);
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}

