package pl.bienkowski.empik.core.service;

import org.junit.jupiter.api.Test;
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
    private final UUID id = UUID.randomUUID();
    private final DatabaseCouponService service =
            new DatabaseCouponService(repository, () -> id, new CountryCodeValidator());

    @Test
    void create_whenCountryIsValid_thenPersistsLazilyAndMapsCoupon() {
        var createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(repository.insert(eq(id), any(UUID.class), eq("PL"), eq(3))).thenAnswer(call -> {
            UUID code = call.getArgument(1);
            assertThat(code.version()).isEqualTo(4);
            return Mono.just(new CouponEntity(id, code, createdAt, "PL", 3, 0));
        });

        var result = service.create("PL", 3);
        verifyNoInteractions(repository);
        StepVerifier.create(result)
                .assertNext(coupon -> {
                    assertThat(coupon.getCode().version()).isEqualTo(4);
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
        StepVerifier.create(service.create("ZZ", 3))
                .expectError(InvalidCountryCodeException.class)
                .verify(Duration.ofSeconds(5));
        verifyNoInteractions(repository);
    }
}
