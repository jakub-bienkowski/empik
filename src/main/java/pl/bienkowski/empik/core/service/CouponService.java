package pl.bienkowski.empik.core.service;

import pl.bienkowski.empik.core.domain.Coupon;
import reactor.core.publisher.Mono;

public interface CouponService {

    Mono<Coupon> create(String code, String countryCode, int maxUsages);
}
