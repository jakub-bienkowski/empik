package pl.bienkowski.empik.core.service;

import org.jspecify.annotations.NullMarked;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import pl.bienkowski.empik.core.exception.ClientCountryUnavailableException;
import pl.bienkowski.empik.core.exception.CouponAlreadyRedeemedException;
import pl.bienkowski.empik.core.exception.CouponCountryMismatchException;
import pl.bienkowski.empik.core.exception.CouponNotFoundException;
import pl.bienkowski.empik.core.exception.CouponUsageLimitReachedException;
import pl.bienkowski.empik.core.model.CouponEntity;
import pl.bienkowski.empik.core.model.CouponRedemptionId;
import pl.bienkowski.empik.core.repository.CouponRedemptionRepository;
import pl.bienkowski.empik.core.repository.CouponRepository;
import pl.bienkowski.empik.core.validation.CouponRedeemValidator;
import pl.bienkowski.empik.utils.ip.CountryResolver;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@NullMarked
public class DatabaseCouponRedeemService implements CouponRedeemService {

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;
    private final CouponRedeemValidator validator;
    private final CountryResolver countryResolver;
    private final TransactionalOperator transactions;

    public DatabaseCouponRedeemService(CouponRepository coupons,
                                       CouponRedemptionRepository redemptions,
                                       CouponRedeemValidator validator,
                                       CountryResolver countryResolver,
                                       TransactionalOperator transactions) {
        this.coupons = coupons;
        this.redemptions = redemptions;
        this.validator = validator;
        this.countryResolver = countryResolver;
        this.transactions = transactions;
    }

    @Override
    public Mono<Void> redeem(UUID code, String userId, String clientIp) {
        return Mono.fromRunnable(() -> validator.validateIp(clientIp))
                .then(Mono.defer(() -> countryResolver.resolve(clientIp)))
                .switchIfEmpty(Mono.error(ClientCountryUnavailableException::new))
                .flatMap(country -> Mono.fromRunnable(() -> validator.validateCountry(country))
                        .then(Mono.defer(() -> transactions.transactional(redeemInDatabase(code, userId, country)))));
    }

    private Mono<Void> redeemInDatabase(UUID code, String userId, String countryCode) {
        return coupons.findByCode(code)
                .switchIfEmpty(Mono.error(CouponNotFoundException::new))
                .flatMap(coupon -> updateUsage(userId, countryCode, coupon));
    }

    private Mono<Void> updateUsage(String userId, String countryCode, CouponEntity coupon) {
        if (!coupon.getCountryCode().equals(countryCode)) {
            return Mono.error(CouponCountryMismatchException::new);
        }

        var redemptionId = new CouponRedemptionId(coupon.getId(), userId);
        return redemptions.insert(redemptionId)
                .onErrorMap(DuplicateKeyException.class, CouponAlreadyRedeemedException::new)
                .then(Mono.defer(() -> coupons.claimUsage(coupon.getId())))
                .flatMap(updated -> updated == 1 ? Mono.empty() : Mono.error(CouponUsageLimitReachedException::new));
    }
}
