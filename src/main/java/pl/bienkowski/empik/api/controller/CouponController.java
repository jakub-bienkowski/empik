package pl.bienkowski.empik.api.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.bienkowski.empik.api.dto.CreateCouponRequest;
import pl.bienkowski.empik.api.dto.RedeemCouponRequest;
import pl.bienkowski.empik.core.exception.ClientCountryUnavailableException;
import pl.bienkowski.empik.core.model.Coupon;
import pl.bienkowski.empik.core.service.CouponRedeemService;
import pl.bienkowski.empik.core.service.CouponService;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/coupons")
public class CouponController {

    private final CouponService coupons;
    private final CouponRedeemService redeemService;
    private final String clientIpHeader;

    public CouponController(CouponService coupons, CouponRedeemService redeemService,
                            @Value("${app.client-ip-header}") String clientIpHeader) {
        this.coupons = coupons;
        this.redeemService = redeemService;
        this.clientIpHeader = clientIpHeader;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Coupon> create(@Valid @RequestBody CreateCouponRequest request) {
        return coupons.create(request.countryCode(), request.maxUsages());
    }

    @PostMapping("/redeem")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> redeem(@Valid @RequestBody RedeemCouponRequest request, @RequestHeader HttpHeaders headers) {
        var addresses = headers.get(clientIpHeader);
        if (addresses == null || addresses.size() != 1) {
            throw new ClientCountryUnavailableException();
        }

        return redeemService.redeem(request.code(), request.userId(), addresses.getFirst());
    }
}
