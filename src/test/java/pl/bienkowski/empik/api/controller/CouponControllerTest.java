package pl.bienkowski.empik.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import pl.bienkowski.empik.core.exception.CountryLookupUnavailableException;
import pl.bienkowski.empik.core.exception.CouponAlreadyRedeemedException;
import pl.bienkowski.empik.core.exception.CouponCodeAlreadyExistsException;
import pl.bienkowski.empik.core.exception.CouponCountryMismatchException;
import pl.bienkowski.empik.core.exception.CouponNotFoundException;
import pl.bienkowski.empik.core.exception.CouponUsageLimitReachedException;
import pl.bienkowski.empik.core.exception.InvalidCountryCodeException;
import pl.bienkowski.empik.core.domain.Coupon;
import pl.bienkowski.empik.core.model.CouponEntity;
import pl.bienkowski.empik.core.service.CouponRedeemService;
import pl.bienkowski.empik.core.service.CouponService;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CouponControllerTest {

    private static final String IP_HEADER = "X-Test-Client-IP";
    private static final String IP = "1.1.1.1";
    private static final String CODE = "SALE5";

    private final CouponService coupons = mock(CouponService.class);
    private final CouponRedeemService redemptions = mock(CouponRedeemService.class);
    private final WebTestClient client = WebTestClient.bindToController(new CouponController(coupons, redemptions, IP_HEADER))
            .controllerAdvice(new CouponExceptionAdvice())
            .build();

    @Test
    void create_whenRequestIsValid_thenReturnsCouponWithoutInternalId() {
        var entity = new CouponEntity(UUID.randomUUID(), CODE, Instant.EPOCH, "PL", 2, 0);
        when(coupons.create(CODE, "PL", 2)).thenReturn(Mono.just(Coupon.fromEntity(entity)));

        create("PL", 2).expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.code").isEqualTo(CODE)
                .jsonPath("$.countryCode").isEqualTo("PL")
                .jsonPath("$.maxUsages").isEqualTo(2)
                .jsonPath("$.currentUsages").isEqualTo(0)
                .jsonPath("$.createdAt").exists()
                .jsonPath("$.id").doesNotExist();
    }

    @Test
    void create_whenRequestIsInvalid_thenReturnsBadRequestWithoutCallingService() {
        create("PL", 0).expectStatus().isBadRequest();
        verifyNoInteractions(coupons);
    }

    @Test
    void create_whenCountryIsUnassigned_thenReturnsBadRequestProblemDetail() {
        when(coupons.create(CODE, "ZZ", 2)).thenReturn(Mono.error(new InvalidCountryCodeException()));
        create("ZZ", 2).expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void redeem_whenConfiguredHeaderIsPresent_thenReturnsNoContent() {
        when(redemptions.redeem(CODE, "user-1", IP)).thenReturn(Mono.empty());

        redeem(IP).expectStatus().isNoContent().expectBody().isEmpty();
        verify(redemptions).redeem(CODE, "user-1", IP);
    }

    @Test
    void create_whenCodeAlreadyExists_thenReturnsConflict() {
        when(coupons.create(CODE, "PL", 2)).thenReturn(Mono.error(new CouponCodeAlreadyExistsException()));

        create("PL", 2).expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.detail").isEqualTo("Coupon code already exists for this country");
    }

    @ParameterizedTest
    @MethodSource("invalidCouponCodes")
    void create_whenCodeIsMissingBlankOrShort_thenReturnsBadRequest(Map<String, Object> body) {
        client.post().uri("/coupons").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body).exchange().expectStatus().isBadRequest();
        verifyNoInteractions(coupons);
    }

    private static Stream<Map<String, Object>> invalidCouponCodes() {
        return Stream.of(
                Map.of("countryCode", "PL", "maxUsages", 2),
                Map.of("code", "    ", "countryCode", "PL", "maxUsages", 2),
                Map.of("code", "abcd", "countryCode", "PL", "maxUsages", 2));
    }

    @Test
    void redeem_whenIpHeaderIsMissingOrRepeated_thenReturnsForbiddenWithoutCallingService() {
        redeem().expectStatus().isForbidden();
        redeem(IP, "8.8.8.8").expectStatus().isForbidden();
        verifyNoInteractions(redemptions);
    }

    @ParameterizedTest
    @MethodSource("redemptionErrors")
    void redeem_whenServiceRejectsRedemption_thenReturnsMatchingProblemDetail(RuntimeException error, HttpStatus status) {
        when(redemptions.redeem(CODE, "user-1", IP)).thenReturn(Mono.error(error));

        redeem(IP).expectStatus().isEqualTo(status)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(status.value())
                .jsonPath("$.detail").isEqualTo(error.getMessage());
    }

    private static Stream<Arguments> redemptionErrors() {
        return Stream.of(
                Arguments.of(new CouponNotFoundException(), HttpStatus.NOT_FOUND),
                Arguments.of(new CouponCountryMismatchException(), HttpStatus.FORBIDDEN),
                Arguments.of(new CouponAlreadyRedeemedException(new IllegalStateException()), HttpStatus.CONFLICT),
                Arguments.of(new CouponUsageLimitReachedException(), HttpStatus.CONFLICT),
                Arguments.of(new CountryLookupUnavailableException(), HttpStatus.SERVICE_UNAVAILABLE));
    }

    private WebTestClient.ResponseSpec create(String country, int maxUsages) {
        return client.post().uri("/coupons").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", CODE, "countryCode", country, "maxUsages", maxUsages))
                .exchange();
    }

    private WebTestClient.ResponseSpec redeem(String... addresses) {
        return client.post().uri("/coupons/redeem").header(IP_HEADER, addresses)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", CODE, "userId", "user-1"))
                .exchange();
    }
}
