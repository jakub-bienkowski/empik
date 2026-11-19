package pl.bienkowski.empik.core.service;

import reactor.core.publisher.Mono;

import java.util.UUID;

public interface CouponRedeemService {

    Mono<Void> redeem(UUID code, String userId, String clientIp);
}
