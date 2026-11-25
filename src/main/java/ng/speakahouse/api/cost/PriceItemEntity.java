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

    public PriceItemEntity() {}

    public PriceItemEntity(
            String priceKey,
            String grp,
            String description,
            String unit,
            String region,
            String finish,
            long rateNgn,
            String source,
            String supplier,
            LocalDate observedOn,
            boolean verified) {
        this.priceKey = priceKey;
        this.grp = grp;
        this.description = description;
        this.unit = unit;
        this.region = region;
        this.finish = finish;
        this.rateNgn = rateNgn;
        this.source = source;
        this.supplier = supplier;
        this.observedOn = observedOn;
        this.verified = verified;
    }

    public Long getId() { return id; }
    public String getPriceKey() { return priceKey; }
    public String getGrp() { return grp; }
    public void setGrp(String grp) { this.grp = grp; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getRegion() { return region; }
    public String getFinish() { return finish; }
    public long getRateNgn() { return rateNgn; }
    public void setRateNgn(long rateNgn) { this.rateNgn = rateNgn; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public LocalDate getObservedOn() { return observedOn; }
    public void setObservedOn(LocalDate observedOn) { this.observedOn = observedOn; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}

