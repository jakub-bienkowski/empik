package pl.bienkowski.empik.core.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pl.bienkowski.empik.core.model.CouponRedemption;
import pl.bienkowski.empik.core.model.CouponRedemptionId;
import reactor.core.publisher.Mono;

public interface CouponRedemptionRepository extends Repository<CouponRedemption, CouponRedemptionId> {

    Mono<CouponRedemption> findById(CouponRedemptionId id);

    @Query("""
            INSERT INTO coupon_redemptions (coupon_id, user_id)
            VALUES (:#{#id.getCouponId()}, :#{#id.getUserId()})
            RETURNING coupon_id, user_id, redeemed_at
            """)
    Mono<CouponRedemption> insert(@Param("id") CouponRedemptionId id);
}
