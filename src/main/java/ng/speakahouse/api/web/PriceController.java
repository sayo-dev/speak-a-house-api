package ng.speakahouse.api.web;

import java.util.List;
import ng.speakahouse.api.cost.PriceItemEntity;
import ng.speakahouse.api.cost.PriceItemRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/prices")
public class PriceController {

    public record PriceDto(
            String key,
            String group,
            String description,
            String unit,
            long rate,
            String region,
            String finish,
            String source,
            String observedOn,
            boolean verified) {}

    private final PriceItemRepository priceRepo;

    public PriceController(PriceItemRepository priceRepo) {
        this.priceRepo = priceRepo;
    }

    @GetMapping
    public List<PriceDto> list(
            @RequestParam(defaultValue = "abeokuta") String region,
            @RequestParam(defaultValue = "standard") String finish) {
        return priceRepo.findByRegionAndFinishIn(region.toLowerCase(), List.of(finish.toLowerCase(), "any"))
                .stream()
                .map(PriceController::toDto)
                .toList();
    }

    private static PriceDto toDto(PriceItemEntity p) {
        return new PriceDto(
                p.getPriceKey(),
                p.getGrp(),
                p.getDescription(),
                p.getUnit(),
                p.getRateNgn(),
                p.getRegion(),
                p.getFinish(),
                p.getSource(),
                p.getObservedOn().toString(),
                p.isVerified());
    }
}

