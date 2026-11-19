package pl.bienkowski.empik.core.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

@Table("coupons")
public final class CouponEntity {

    @Id
    @Column("id")
    private final UUID id;
    @Column("code")
    private final UUID code;
    @Column("created_at")
    private final Instant createdAt;
    @Column("country_code")
    private final String countryCode;
    @Column("max_usages")
    private final int maxUsages;
    @Column("current_usages")
    private final int currentUsages;

    public CouponEntity(UUID id, UUID code, Instant createdAt, String countryCode, int maxUsages, int currentUsages) {
        this.id = requireNonNull(id, "id");
        this.code = requireNonNull(code, "code");
        this.createdAt = requireNonNull(createdAt, "createdAt");
        this.countryCode = requireNonNull(countryCode, "countryCode");
        this.maxUsages = maxUsages;
        this.currentUsages = currentUsages;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCode() {
        return code;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public int getMaxUsages() {
        return maxUsages;
    }

    public int getCurrentUsages() {
        return currentUsages;
    }
}
