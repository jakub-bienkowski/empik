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
import pl.bienkowski.empik.core.exception.CouponCountryMismatchException;
import pl.bienkowski.empik.core.exception.CouponNotFoundException;
import pl.bienkowski.empik.core.exception.CouponUsageLimitReachedException;
import pl.bienkowski.empik.core.exception.InvalidCountryCodeException;
import pl.bienkowski.empik.core.model.Coupon;
import pl.bienkowski.empik.core.model.CouponEntity;
import pl.bienkowski.empik.core.service.CouponRedeemService;
import pl.bienkowski.empik.core.service.CouponService;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Locale;
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
    private static final UUID CODE = UUID.fromString("abcdef01-abcd-4abc-8abc-abcdefabcdef");

    private final CouponService coupons = mock(CouponService.class);
    private final CouponRedeemService redemptions = mock(CouponRedeemService.class);
    private final WebTestClient client = WebTestClient.bindToController(new CouponController(coupons, redemptions, IP_HEADER))
            .controllerAdvice(new CouponExceptionAdvice())
            .build();

    @Test
    void create_whenRequestIsValid_thenReturnsCouponWithoutInternalId() {
        var entity = new CouponEntity(UUID.randomUUID(), CODE, Instant.EPOCH, "PL", 2, 0);
        when(coupons.create("PL", 2)).thenReturn(Mono.just(Coupon.fromEntity(entity)));

        create("PL", 2).expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.code").isEqualTo(CODE.toString())
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
        when(coupons.create("ZZ", 2)).thenReturn(Mono.error(new InvalidCountryCodeException()));
        create("ZZ", 2).expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void redeem_whenCodeIsUppercaseAndConfiguredHeaderIsPresent_thenReturnsNoContent() {
        when(redemptions.redeem(CODE, "user-1", IP)).thenReturn(Mono.empty());

        redeem(IP).expectStatus().isNoContent().expectBody().isEmpty();
        verify(redemptions).redeem(CODE, "user-1", IP);
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
                .bodyValue(Map.of("countryCode", country, "maxUsages", maxUsages))
                .exchange();
    }

    private WebTestClient.ResponseSpec redeem(String... addresses) {
        return client.post().uri("/coupons/redeem").header(IP_HEADER, addresses)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", CODE.toString().toUpperCase(Locale.ROOT), "userId", "user-1"))
                .exchange();
    }
}
