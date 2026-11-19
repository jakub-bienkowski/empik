package pl.bienkowski.empik.core.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

@Table("coupon_redemptions")
public final class CouponRedemption {

    @Id
    @Embedded(onEmpty = Embedded.OnEmpty.USE_NULL)
    private final CouponRedemptionId id;
    @Column("redeemed_at")
    private final Instant redeemedAt;

    public CouponRedemption(CouponRedemptionId id, Instant redeemedAt) {
        this.id = requireNonNull(id, "id");
        this.redeemedAt = requireNonNull(redeemedAt, "redeemedAt");
    }

    public CouponRedemptionId getId() {
        return id;
    }

    public Instant getRedeemedAt() {
        return redeemedAt;
    }
}
