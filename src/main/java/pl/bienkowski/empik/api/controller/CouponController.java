package pl.bienkowski.empik.api.controller;

import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pl.bienkowski.empik.api.dto.CreateCouponRequest;
import pl.bienkowski.empik.api.dto.CreateCouponResponse;
import pl.bienkowski.empik.api.dto.RedeemCouponRequest;
import pl.bienkowski.empik.core.model.CouponRedemptionId;
import pl.bienkowski.empik.core.repository.CouponRedemptionRepository;
import pl.bienkowski.empik.core.repository.CouponRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/coupons")
public class CouponController {

    private final CouponRepository coupons;
    private final CouponRedemptionRepository redemptions;

    public CouponController(CouponRepository coupons, CouponRedemptionRepository redemptions) {
        this.coupons = coupons;
        this.redemptions = redemptions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CreateCouponResponse> create(@Valid @RequestBody CreateCouponRequest request) {
        return coupons.insert(UUID.randomUUID(), UUID.randomUUID(), request.countryCode(), request.maxUsages())
                .map(coupon -> new CreateCouponResponse(coupon.getCode(), coupon.getCountryCode(),
                        coupon.getMaxUsages(), coupon.getCurrentUsages(), coupon.getCreatedAt()));
    }

    @PostMapping("/redemptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public Mono<Void> redeem(@Valid @RequestBody RedeemCouponRequest request) {
        return coupons.findByCode(request.code())
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found")))
                .flatMap(coupon -> {
                    if (!coupon.getCountryCode().equals(request.countryCode())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Country not allowed"));
                    }
                    var redemptionId = new CouponRedemptionId(coupon.getId(), request.userId());
                    return redemptions.insert(redemptionId)
                            .onErrorMap(DuplicateKeyException.class, error -> new ResponseStatusException(
                                    HttpStatus.CONFLICT, "Coupon already redeemed by this user", error))
                            .then(Mono.defer(() -> coupons.claimUsage(coupon.getId())))
                            .flatMap(updated -> updated == 1
                                    ? Mono.<Void>empty()
                                    : Mono.error(new ResponseStatusException(
                                            HttpStatus.CONFLICT, "Coupon usage limit reached")));
                });
    }
}
