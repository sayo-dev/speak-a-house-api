package ng.speakahouse.api.cost;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "price_item")
public class PriceItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_key", nullable = false, length = 60)
    private String priceKey;

    @Column(name = "grp", nullable = false, length = 30)
    private String grp;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, length = 10)
    private String unit;

    @Column(nullable = false, length = 20)
    private String region;

    @Column(nullable = false, length = 10)
    private String finish;

    @Column(name = "rate_ngn", nullable = false)
    private long rateNgn;

    @Column(nullable = false, length = 200)
    private String source;

    @Column(length = 120)
    private String supplier;

    @Column(name = "observed_on", nullable = false)
    private LocalDate observedOn;

    @Column(nullable = false)
    private boolean verified;

    public Long getId() { return id; }
    public String getPriceKey() { return priceKey; }
    public String getGrp() { return grp; }
    public String getDescription() { return description; }
    public String getUnit() { return unit; }
    public String getRegion() { return region; }
    public String getFinish() { return finish; }
    public long getRateNgn() { return rateNgn; }
    public String getSource() { return source; }
    public String getSupplier() { return supplier; }
    public LocalDate getObservedOn() { return observedOn; }
    public boolean isVerified() { return verified; }
}

