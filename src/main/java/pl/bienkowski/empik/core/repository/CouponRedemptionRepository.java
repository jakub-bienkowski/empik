package pl.bienkowski.empik.core.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pl.bienkowski.empik.core.model.CouponRedemptionEntity;
import pl.bienkowski.empik.core.model.CouponRedemptionId;
import reactor.core.publisher.Mono;

public interface CouponRedemptionRepository extends Repository<CouponRedemptionEntity, CouponRedemptionId> {

    Mono<CouponRedemptionEntity> findById(CouponRedemptionId id);

    @Query("""
            INSERT INTO coupon_redemptions (coupon_id, user_id)
            VALUES (:#{#id.getCouponId()}, :#{#id.getUserId()})
            RETURNING coupon_id, user_id, redeemed_at
            """)
    Mono<CouponRedemptionEntity> insert(@Param("id") CouponRedemptionId id);
}
