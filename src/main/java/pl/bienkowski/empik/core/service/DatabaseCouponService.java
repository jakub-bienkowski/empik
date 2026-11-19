package pl.bienkowski.empik.core.service;

import org.springframework.stereotype.Service;
import pl.bienkowski.empik.core.model.Coupon;
import pl.bienkowski.empik.core.model.CouponEntity;
import pl.bienkowski.empik.core.repository.CouponRepository;
import pl.bienkowski.empik.core.validation.CountryCodeValidator;
import pl.bienkowski.empik.utils.uuid.IdFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class DatabaseCouponService implements CouponService {

    private final CouponRepository couponRepository;
    private final IdFactory<UUID> uuidFactory;
    private final CountryCodeValidator countryCodeValidator;

    public DatabaseCouponService(CouponRepository couponRepository,
                                 IdFactory<UUID> uuidFactory,
                                 CountryCodeValidator countryCodeValidator) {
        this.couponRepository = couponRepository;
        this.uuidFactory = uuidFactory;
        this.countryCodeValidator = countryCodeValidator;
    }

    @Override
    public Mono<Coupon> create(String countryCode, int maxUsages) {
        return Mono.fromRunnable(() -> countryCodeValidator.validate(countryCode))
                .then(Mono.defer(() -> insertEntity(countryCode, maxUsages)))
                .map(Coupon::fromEntity);
    }

    private Mono<CouponEntity> insertEntity(String countryCode, int maxUsages) {
        return couponRepository.insert(
                uuidFactory.generateId(),
                UUID.randomUUID(),
                countryCode,
                maxUsages
        );
    }
}
