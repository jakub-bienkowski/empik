package pl.bienkowski.empik.core.service;

import reactor.core.publisher.Mono;

public interface CouponRedeemService {

    Mono<Void> redeem(String code, String userId, String clientIp);
}
