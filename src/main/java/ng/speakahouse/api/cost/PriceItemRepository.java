package ng.speakahouse.api.cost;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceItemRepository extends JpaRepository<PriceItemEntity, Long> {
    List<PriceItemEntity> findByRegionAndFinishIn(String region, List<String> finishes);
    Optional<PriceItemEntity> findByPriceKeyAndRegionAndFinish(String priceKey, String region, String finish);
}

