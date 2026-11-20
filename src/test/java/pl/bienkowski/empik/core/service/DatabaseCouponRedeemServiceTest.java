package pl.bienkowski.empik.core.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.reactive.TransactionalOperator;
import pl.bienkowski.empik.core.exception.CountryLookupUnavailableException;
import pl.bienkowski.empik.core.exception.CouponCountryMismatchException;
import pl.bienkowski.empik.core.exception.CouponNotFoundException;
import pl.bienkowski.empik.core.model.CouponEntity;
import pl.bienkowski.empik.core.model.CouponRedemptionEntity;
import pl.bienkowski.empik.core.model.CouponRedemptionId;
import pl.bienkowski.empik.core.repository.CouponRedemptionRepository;
import pl.bienkowski.empik.core.repository.CouponRepository;
import pl.bienkowski.empik.core.validation.CountryCodeValidator;
import pl.bienkowski.empik.core.validation.CouponRedeemValidator;
import pl.bienkowski.empik.core.geolocation.GeolocationResolver;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatabaseCouponRedeemServiceTest {

    @Test
    void redeem_whenCodeExistsInMultipleCountries_thenRedeemsForClientCountry() {
        var coupons = mock(CouponRepository.class);
        var redemptions = mock(CouponRedemptionRepository.class);
        var resolver = mock(GeolocationResolver.class);
        var transactions = mock(TransactionalOperator.class);
        var id = UUID.randomUUID();
        var redemptionId = new CouponRedemptionId(id, "user-1");
        when(resolver.resolve("1.1.1.1")).thenReturn(Mono.just("PL"));
        when(transactions.transactional(any(Mono.class))).thenAnswer(call -> call.getArgument(0));
        when(coupons.findByCodeAndCountryCode("SALE5", "PL"))
                .thenReturn(Mono.just(new CouponEntity(id, "SALE5", Instant.EPOCH, "PL", 2, 0)));
        when(redemptions.insert(redemptionId))
                .thenReturn(Mono.just(new CouponRedemptionEntity(redemptionId, Instant.EPOCH)));
        when(coupons.claimUsage(id)).thenReturn(Mono.just(1));
        var service = new DatabaseCouponRedeemService(
                coupons, redemptions, new CouponRedeemValidator(new CountryCodeValidator()), resolver, transactions);

        StepVerifier.create(service.redeem("SALE5", "user-1", "1.1.1.1"))
                .verifyComplete();

        verify(redemptions).insert(redemptionId);
        verify(coupons).claimUsage(id);
    }

    @Test
    void redeem_whenCodeIsNotInClientCountry_thenDistinguishesMismatchFromMissing() {
        var coupons = mock(CouponRepository.class);
        var redemptions = mock(CouponRedemptionRepository.class);
        var resolver = mock(GeolocationResolver.class);
        var transactions = mock(TransactionalOperator.class);
        when(resolver.resolve("1.1.1.1")).thenReturn(Mono.just("PL"));
        when(transactions.transactional(any(Mono.class))).thenAnswer(call -> call.getArgument(0));
        when(coupons.findByCodeAndCountryCode("SALE5", "PL")).thenReturn(Mono.empty());
        when(coupons.existsByCode("SALE5")).thenReturn(Mono.just(true), Mono.just(false));
        var service = new DatabaseCouponRedeemService(
                coupons, redemptions, new CouponRedeemValidator(new CountryCodeValidator()), resolver, transactions);

        StepVerifier.create(service.redeem("SALE5", "user-1", "1.1.1.1"))
                .expectError(CouponCountryMismatchException.class).verify(Duration.ofSeconds(5));
        StepVerifier.create(service.redeem("SALE5", "user-1", "1.1.1.1"))
                .expectError(CouponNotFoundException.class).verify(Duration.ofSeconds(5));
        verifyNoInteractions(redemptions);
    }

    @Test
    void redeem_whenLookupIsPendingOrFails_thenDoesNotStartDatabaseWork() {
        var coupons = mock(CouponRepository.class);
        var redemptions = mock(CouponRedemptionRepository.class);
        var resolver = mock(GeolocationResolver.class);
        var transactions = mock(TransactionalOperator.class);
        var lookup = Sinks.<String>one();
        String clientIp = "1.1.1.1";
        when(resolver.resolve(clientIp)).thenReturn(lookup.asMono());
        var service = new DatabaseCouponRedeemService(
                coupons, redemptions, new CouponRedeemValidator(new CountryCodeValidator()), resolver, transactions);

        StepVerifier.create(service.redeem("SALE5", "user-1", clientIp))
                .then(() -> {
                    verify(resolver).resolve(clientIp);
                    verifyNoInteractions(transactions, coupons, redemptions);
                    lookup.emitError(new CountryLookupUnavailableException(), Sinks.EmitFailureHandler.FAIL_FAST);
                })
                .expectError(CountryLookupUnavailableException.class)
                .verify(Duration.ofSeconds(5));

        verifyNoInteractions(transactions, coupons, redemptions);
    }
}
