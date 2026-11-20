package pl.bienkowski.empik.core.service;

import org.springframework.stereotype.Service;
import pl.bienkowski.empik.core.domain.Coupon;
import pl.bienkowski.empik.core.exception.CouponCodeAlreadyExistsException;
import pl.bienkowski.empik.core.model.CouponEntity;
import pl.bienkowski.empik.core.repository.CouponRepository;
import pl.bienkowski.empik.core.validation.CountryCodeValidator;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class DatabaseCouponService implements CouponService {

    private final CouponRepository couponRepository;
    private final CountryCodeValidator countryCodeValidator;

    public DatabaseCouponService(CouponRepository couponRepository,
                                 CountryCodeValidator countryCodeValidator) {
        this.couponRepository = couponRepository;
        this.countryCodeValidator = countryCodeValidator;
    }

    @Override
    public Mono<Coupon> create(String code, String countryCode, int maxUsages) {
        return Mono.fromRunnable(() -> countryCodeValidator.validate(countryCode))
                .then(Mono.defer(() -> insertEntity(code, countryCode, maxUsages)))
                .map(Coupon::fromEntity);
    }

    private Mono<CouponEntity> insertEntity(String code, String countryCode, int maxUsages) {
        return couponRepository.insert(
                UUID.randomUUID(),
                code,
                countryCode,
                maxUsages
        ).switchIfEmpty(Mono.error(CouponCodeAlreadyExistsException::new));
    }
}
