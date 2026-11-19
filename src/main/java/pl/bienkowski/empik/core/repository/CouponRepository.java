package pl.bienkowski.empik.core.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pl.bienkowski.empik.core.model.Coupon;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CouponRepository extends Repository<Coupon, UUID> {

    Mono<Coupon> findById(UUID id);

    Mono<Coupon> findByCode(UUID code);

    @Query("""
            INSERT INTO coupons (id, code, country_code, max_usages)
            VALUES (:id, :code, :countryCode, :maxUsages)
            RETURNING id, code, country_code, max_usages, current_usages, created_at
            """)
    Mono<Coupon> insert(
            @Param("id") UUID id,
            @Param("code") UUID code,
            @Param("countryCode") String countryCode,
            @Param("maxUsages") int maxUsages
    );
}
