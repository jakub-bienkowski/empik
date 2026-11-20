package pl.bienkowski.empik.core.domain;

import pl.bienkowski.empik.core.model.CouponEntity;

import java.time.Instant;

public final class Coupon {

    private final String code;
    private final String countryCode;
    private final int maxUsages;
    private final int currentUsages;
    private final Instant createdAt;

    private Coupon(String code, String countryCode, int maxUsages, int currentUsages, Instant createdAt) {
        this.code = code;
        this.countryCode = countryCode;
        this.maxUsages = maxUsages;
        this.currentUsages = currentUsages;
        this.createdAt = createdAt;
    }

    public static Coupon fromEntity(CouponEntity entity) {
        return new Coupon(entity.getCode(), entity.getCountryCode(), entity.getMaxUsages(),
                entity.getCurrentUsages(), entity.getCreatedAt());
    }

    public String getCode() {
        return code;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
