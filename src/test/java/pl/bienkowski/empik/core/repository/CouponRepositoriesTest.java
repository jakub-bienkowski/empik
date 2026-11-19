package pl.bienkowski.empik.core.repository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import pl.bienkowski.empik.core.exception.CouponAlreadyRedeemedException;
import pl.bienkowski.empik.core.exception.CouponUsageLimitReachedException;
import pl.bienkowski.empik.core.model.CouponRedemptionId;
import pl.bienkowski.empik.core.service.CouponRedeemService;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.webflux.base-path=/empik")
@Testcontainers
class CouponRepositoriesTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.7");
    private static final String POLISH_IP = "1.1.1.1";
    private static final String GERMAN_IP = "2001:4860:4860::8888";
    private static DisposableServer ipInfo;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        ipInfo = HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> {
                    if (!"Bearer integration-test".equals(request.requestHeaders().get("Authorization"))) {
                        return response.status(401).send();
                    }
                    if ("lite/4.2.2.2".equals(request.path())) {
                        return Mono.never();
                    }
                    String countryResponse = switch (request.path()) {
                        case "lite/" + POLISH_IP -> "{\"country_code\":\"PL\"}";
                        case "lite/" + GERMAN_IP -> "{\"country_code\":\"DE\"}";
                        case "lite/8.8.8.8" -> "{}";
                        default -> null;
                    };
                    return countryResponse == null
                            ? response.status(500).send()
                            : response.header("Content-Type", "application/json").sendString(Mono.just(countryResponse));
                })
                .bindNow();
        registry.add("ipinfo.base-url", () -> "http://127.0.0.1:" + ipInfo.port());
        registry.add("ipinfo.token", () -> "integration-test");
        registry.add("ipinfo.response-timeout-ms", () -> 10000);
        registry.add("ipinfo.lookup-timeout-ms", () -> 1000);
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s"
                .formatted(postgres.getHost(), postgres.getMappedPort(5432), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.liquibase.url", postgres::getJdbcUrl);
    }

    @AfterAll
    static void stopIpInfo() {
        if (ipInfo != null) {
            ipInfo.disposeNow();
        }
    }

    @Autowired
    CouponRepository coupons;

    @Autowired
    CouponRedemptionRepository redemptions;

    @Autowired
    CouponRedeemService redeemService;

    @Autowired
    DatabaseClient database;

    @LocalServerPort
    int port;

    @Test
    void healthProbes_whenAppAndDatabaseAreReady_thenReturnOk() {
        var client = httpClient();
        client.get().uri("/health/liveness").exchange().expectStatus().isOk();
        client.get().uri("/health/readiness").exchange().expectStatus().isOk();
    }

    @Test
    void redeem_whenCouponHasNoSlotsLeft_thenDoesNotPersistFailedRedemption() {
        var client = httpClient();
        client.post().uri("/coupons").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("countryCode", "PL", "maxUsages", 0))
                .exchange().expectStatus().isBadRequest();
        client.post().uri("/coupons").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("countryCode", "ZZ", "maxUsages", 1))
                .exchange().expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(400);
        var response = client.post().uri("/coupons").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("countryCode", "PL", "maxUsages", 1))
                .exchange().expectStatus().isCreated().expectBody(Map.class)
                .returnResult().getResponseBody();
        assertThat(response).isNotNull();
        assertThat(response.get("countryCode")).isEqualTo("PL");
        assertThat(response.get("currentUsages")).isEqualTo(0);
        assertThat(response.get("createdAt")).isNotNull();
        assertThat(response.containsKey("id")).isFalse();
        String code = (String) response.get("code");
        assertThat(UUID.fromString(code).version()).isEqualTo(4);

        client.post().uri("/coupons/redeem").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", POLISH_IP)
                .bodyValue(Map.of("userId", "user-1"))
                .exchange().expectStatus().isBadRequest();
        client.post().uri("/coupons/redeem").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", code, "userId", "user-1"))
                .exchange().expectStatus().isForbidden()
                .expectBody().jsonPath("$.detail").isEqualTo("Client country unavailable");
        redeem(client, code, "user-1", "8.8.8.8").expectStatus().isForbidden()
                .expectBody().jsonPath("$.detail").isEqualTo("Client country unavailable");
        redeem(client, code, "user-1", POLISH_IP, GERMAN_IP).expectStatus().isForbidden();
        redeem(client, code, "user-1", "1.1.1.1,8.8.8.8").expectStatus().isForbidden();
        redeem(client, code, "user-1", "999.1.1.1").expectStatus().isForbidden();
        redeem(client, code, "user-1", "9.9.9.9").expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.detail").isEqualTo("IP country lookup is unavailable");
        redeem(client, code, "user-1", "4.2.2.2").expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.detail").isEqualTo("IP country lookup is unavailable");
        redeem(client, UUID.randomUUID().toString(), "user-1", POLISH_IP).expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("Coupon not found");
        redeem(client, code, "user-1", GERMAN_IP).expectStatus().isForbidden()
                .expectBody().jsonPath("$.detail").isEqualTo("Country not allowed");
        redeem(client, code.toUpperCase(Locale.ROOT), "user-1", POLISH_IP).expectStatus().isNoContent();
        redeem(client, code, "user-1", POLISH_IP).expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.detail").isEqualTo("Coupon already redeemed by this user");
        redeem(client, code, "user-2", POLISH_IP).expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.detail").isEqualTo("Coupon usage limit reached");

        StepVerifier.create(coupons.findByCode(UUID.fromString(code))
                        .doOnNext(coupon -> assertThat(coupon.getId().version()).isEqualTo(7))
                        .flatMap(coupon -> redemptions.findById(new CouponRedemptionId(coupon.getId(), "user-2"))
                                .hasElement()
                                .doOnNext(exists -> assertThat(exists).isFalse())
                                .thenReturn(coupon.getCurrentUsages())))
                .expectNext(1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));
    }

    @Test
    void redeem_whenTwoUsersRaceForLastSlot_thenPersistsOnlyOneRedemption() {
        assertConcurrentRedemptions(1, "user-2", CouponUsageLimitReachedException.class);
    }

    @Test
    void redeem_whenSameUserRedeemsConcurrentlyWithSpareCapacity_thenCountsOnlyOneRedemption() {
        assertConcurrentRedemptions(2, "user-1", CouponAlreadyRedeemedException.class);
    }

    private void assertConcurrentRedemptions(int maxUsages, String secondUser, Class<? extends Throwable> failureType) {
        UUID id = UUID.randomUUID();
        UUID code = UUID.randomUUID();
        var first = redeemService.redeem(code, "user-1", POLISH_IP).materialize();
        var second = redeemService.redeem(code, secondUser, POLISH_IP).materialize();

        StepVerifier.create(coupons.insert(id, code, "PL", maxUsages)
                        .then(Mono.zip(first, second))
                        .flatMap(outcomes -> {
                            var signals = List.of(outcomes.getT1(), outcomes.getT2());
                            assertThat(signals).filteredOn(Signal::isOnComplete).hasSize(1);
                            assertThat(signals).filteredOn(Signal::isOnError).singleElement()
                                    .satisfies(signal -> assertThat(signal.getThrowable()).isInstanceOf(failureType));

                            String winner = outcomes.getT1().isOnComplete() ? "user-1" : secondUser;
                            return coupons.findById(id).zipWith(database.sql("""
                                            SELECT user_id FROM coupon_redemptions WHERE coupon_id = :id
                                            """)
                                    .bind("id", id)
                                    .map((row, _) -> row.get("user_id", String.class))
                                    .all().collectList())
                                    .doOnNext(state -> {
                                        assertThat(state.getT1().getCurrentUsages()).isEqualTo(1);
                                        assertThat(state.getT2()).containsExactly(winner);
                                    });
                        }))
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(10));
    }

    private WebTestClient httpClient() {
        return WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port + "/empik")
                .responseTimeout(Duration.ofSeconds(10)).build();
    }

    private static WebTestClient.ResponseSpec redeem(WebTestClient client, String code, String userId,
                                                     String... addresses) {
        return client.post().uri("/coupons/redeem")
                .header("X-Forwarded-For", addresses)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", code, "userId", userId))
                .exchange();
    }

    @Test
    void insert_whenCouponIsNew_thenAppliesDatabaseDefaultsAndSupportsLookupByCodeAndId() {
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
    void insert_whenRedemptionKeyAlreadyExists_thenRejectsDuplicateAndPreservesOriginal() {
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
