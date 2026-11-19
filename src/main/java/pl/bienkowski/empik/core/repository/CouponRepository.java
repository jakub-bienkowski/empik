package pl.bienkowski.empik.core.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import pl.bienkowski.empik.core.model.CouponEntity;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CouponRepository extends Repository<CouponEntity, UUID> {

    Mono<CouponEntity> findById(UUID id);

    Mono<CouponEntity> findByCode(UUID code);

    @Modifying
    @Query("""
            UPDATE coupons
            SET current_usages = current_usages + 1
            WHERE id = :id AND current_usages < max_usages
            """)
    Mono<Integer> claimUsage(@Param("id") UUID id);

    @Query("""
            INSERT INTO coupons (id, code, country_code, max_usages)
            VALUES (:id, :code, :countryCode, :maxUsages)
            RETURNING id, code, country_code, max_usages, current_usages, created_at
            """)
    Mono<CouponEntity> insert(
            @Param("id") UUID id,
            @Param("code") UUID code,
            @Param("countryCode") String countryCode,
            @Param("maxUsages") int maxUsages
    );
}
