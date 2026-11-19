package pl.bienkowski.empik.core.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.bienkowski.empik.core.model.CouponRedemptionId;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class CouponRepositoriesTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.7");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s"
                .formatted(postgres.getHost(), postgres.getMappedPort(5432), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.liquibase.url", postgres::getJdbcUrl);
    }

    @Autowired
    CouponRepository coupons;

    @Autowired
    CouponRedemptionRepository redemptions;

    @Test
    void insertsCouponWithDatabaseDefaultsAndFindsItByCodeAndId() {
        UUID id = UUID.randomUUID();
        UUID code = UUID.randomUUID();

        StepVerifier.create(coupons.insert(id, code, "PL", 3)
                        .flatMap(inserted -> coupons.findByCode(code).zipWith(coupons.findById(id))
                                .doOnNext(found -> {
                                    assertThat(found.getT1()).usingRecursiveComparison().isEqualTo(inserted);
                                    assertThat(found.getT2()).usingRecursiveComparison().isEqualTo(inserted);
                                })
                                .thenReturn(inserted)))
                .assertNext(coupon -> {
                    assertThat(coupon.getId()).isEqualTo(id);
                    assertThat(coupon.getCode()).isEqualTo(code);
                    assertThat(coupon.getCountryCode()).isEqualTo("PL");
                    assertThat(coupon.getMaxUsages()).isEqualTo(3);
                    assertThat(coupon.getCurrentUsages()).isZero();
                    assertThat(coupon.getCreatedAt()).isNotNull();
                })
                .expectComplete()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void readsRedemptionByCompositeIdAndRejectsDuplicate() {
        UUID couponId = UUID.randomUUID();
        var id = new CouponRedemptionId(couponId, "user-1");

        StepVerifier.create(coupons.insert(couponId, UUID.randomUUID(), "PL", 3)
                        .then(redemptions.insert(id))
                        .flatMap(inserted -> redemptions.findById(id).doOnNext(found ->
                                assertThat(found).usingRecursiveComparison().isEqualTo(inserted)))
                        .concatWith(redemptions.insert(id)))
                .assertNext(redemption -> {
                    assertThat(redemption.getId()).isEqualTo(id);
                    assertThat(redemption.getRedeemedAt()).isNotNull();
                })
                .expectError(DuplicateKeyException.class)
                .verify(Duration.ofSeconds(10));
    }
}
