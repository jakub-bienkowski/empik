package pl.bienkowski.empik.core.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.reactive.TransactionalOperator;
import pl.bienkowski.empik.core.exception.CountryLookupUnavailableException;
import pl.bienkowski.empik.core.repository.CouponRedemptionRepository;
import pl.bienkowski.empik.core.repository.CouponRepository;
import pl.bienkowski.empik.core.validation.CountryCodeValidator;
import pl.bienkowski.empik.core.validation.CouponRedeemValidator;
import pl.bienkowski.empik.utils.ip.CountryResolver;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatabaseCouponRedeemServiceTest {

    @Test
    void redeem_whenLookupIsPendingOrFails_thenDoesNotStartDatabaseWork() {
        var coupons = mock(CouponRepository.class);
        var redemptions = mock(CouponRedemptionRepository.class);
        var resolver = mock(CountryResolver.class);
        var transactions = mock(TransactionalOperator.class);
        var lookup = Sinks.<String>one();
        String clientIp = "1.1.1.1";
        when(resolver.resolve(clientIp)).thenReturn(lookup.asMono());
        var service = new DatabaseCouponRedeemService(
                coupons, redemptions, new CouponRedeemValidator(new CountryCodeValidator()), resolver, transactions);

        StepVerifier.create(service.redeem(UUID.randomUUID(), "user-1", clientIp))
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
