package pl.bienkowski.empik.core.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.bienkowski.empik.core.model.CouponRedemptionId;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.webflux.base-path=/empik")
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

    @LocalServerPort
    int port;

    @Test
    void servesHealthProbes() {
        var client = httpClient();
        client.get().uri("/health/liveness").exchange().expectStatus().isOk();
        client.get().uri("/health/readiness").exchange().expectStatus().isOk();
    }

    @Test
    void createsAndRedeemsCouponWithoutLeavingARecordForAFailedRedemption() {
        var client = httpClient();
        client.post().uri("/coupons").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("countryCode", "PL", "maxUsages", 0))
                .exchange().expectStatus().isBadRequest();
        var response = client.post().uri("/coupons").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("countryCode", "PL", "maxUsages", 1))
                .exchange().expectStatus().isCreated().expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(response).isNotNull();
        assertThat(response.get("countryCode")).isEqualTo("PL");
        assertThat(response.get("currentUsages")).isEqualTo(0);
        assertThat(response.get("createdAt")).isNotNull();
        String code = (String) response.get("code");

        client.post().uri("/coupons/redemptions").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("userId", "user-1", "countryCode", "PL"))
                .exchange().expectStatus().isBadRequest();
        redeem(client, UUID.randomUUID().toString(), "user-1", "PL").expectStatus().isNotFound();
        redeem(client, code, "user-1", "DE").expectStatus().isForbidden();
        redeem(client, code, "user-1", "PL").expectStatus().isNoContent();
        redeem(client, code, "user-1", "PL").expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.detail").isEqualTo("Coupon already redeemed by this user");
        redeem(client, code, "user-2", "PL").expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.detail").isEqualTo("Coupon usage limit reached");

        StepVerifier.create(coupons.findByCode(UUID.fromString(code))
                        .flatMap(coupon -> redemptions.findById(new CouponRedemptionId(coupon.getId(), "user-2"))
                                .hasElement()
                                .doOnNext(exists -> assertThat(exists).isFalse())
                                .thenReturn(coupon.getCurrentUsages())))
                .expectNext(1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));
    }

    private WebTestClient httpClient() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/empik").build();
    }

    private static WebTestClient.ResponseSpec redeem(WebTestClient client, String code, String userId, String country) {
        return client.post().uri("/coupons/redemptions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", code, "userId", userId, "countryCode", country))
                .exchange();
    }

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
