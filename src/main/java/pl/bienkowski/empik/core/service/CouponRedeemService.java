package pl.bienkowski.empik.core.service;

import reactor.core.publisher.Mono;

public interface CouponRedeemService {

    /**
     * Redeems the coupon matching the client's resolved country.
     *
     * @param code case-sensitive coupon code
     * @param userId identifier of the user redeeming the coupon
     * @param clientIp IPv4 or IPv6 address used to resolve the client's country
     * @return completion after redemption, or an error if the coupon cannot be redeemed
     */
    Mono<Void> redeem(String code, String userId, String clientIp);
}
