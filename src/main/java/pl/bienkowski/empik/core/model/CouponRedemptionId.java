package pl.bienkowski.empik.core.model;

import org.jspecify.annotations.Nullable;
import org.springframework.data.relational.core.mapping.Column;

import java.util.Objects;
import java.util.UUID;

public final class CouponRedemptionId {

    @Column("coupon_id")
    private final UUID couponId;
    @Column("user_id")
    private final String userId;

    public CouponRedemptionId(UUID couponId, String userId) {
        this.couponId = Objects.requireNonNull(couponId, "couponId");
        this.userId = Objects.requireNonNull(userId, "userId");
    }

    public UUID getCouponId() {
        return couponId;
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof CouponRedemptionId id
                && couponId.equals(id.couponId)
                && userId.equals(id.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(couponId, userId);
    }
}
