package pl.bienkowski.empik.core.service;

import org.junit.jupiter.api.Test;
import pl.bienkowski.empik.core.exception.CouponCodeAlreadyExistsException;
import pl.bienkowski.empik.core.exception.InvalidCountryCodeException;
import pl.bienkowski.empik.core.model.CouponEntity;
import pl.bienkowski.empik.core.repository.CouponRepository;
import pl.bienkowski.empik.core.validation.CountryCodeValidator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatabaseCouponServiceTest {

    private final CouponRepository repository = mock(CouponRepository.class);
    private final DatabaseCouponService service =
            new DatabaseCouponService(repository, new CountryCodeValidator());

    @Test
    void create_whenCountryIsValid_thenPersistsLazilyAndMapsCoupon() {
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(repository.insert(any(UUID.class), eq("SALE5"), eq("PL"), eq(3))).thenAnswer(call -> {
            UUID id = call.getArgument(0);
            assertThat(id.version()).isEqualTo(4);
            return Mono.just(new CouponEntity(id, "SALE5", createdAt, "PL", 3, 0));
        });

        var result = service.create("SALE5", "PL", 3);
        verifyNoInteractions(repository);
        StepVerifier.create(result)
                .assertNext(coupon -> {
                    assertThat(coupon.getCode()).isEqualTo("SALE5");
                    assertThat(coupon.getCountryCode()).isEqualTo("PL");
                    assertThat(coupon.getMaxUsages()).isEqualTo(3);
                    assertThat(coupon.getCurrentUsages()).isZero();
                    assertThat(coupon.getCreatedAt()).isEqualTo(createdAt);
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void create_whenCountryIsUnassigned_thenFailsWithoutWriting() {
        StepVerifier.create(service.create("SALE5", "ZZ", 3))
                .expectError(InvalidCountryCodeException.class)
                .verify(Duration.ofSeconds(5));
        verifyNoInteractions(repository);
    }

    @Test
    void create_whenCodeAlreadyExistsInCountry_thenFails() {
        when(repository.insert(any(UUID.class), eq("SALE5"), eq("PL"), eq(3))).thenReturn(Mono.empty());

        StepVerifier.create(service.create("SALE5", "PL", 3))
                .expectError(CouponCodeAlreadyExistsException.class)
                .verify(Duration.ofSeconds(5));
    }
}
