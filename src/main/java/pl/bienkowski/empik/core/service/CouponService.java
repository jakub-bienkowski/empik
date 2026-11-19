package pl.bienkowski.empik.core.service;

import pl.bienkowski.empik.core.model.Coupon;
import reactor.core.publisher.Mono;

public interface CouponService {

    Mono<Coupon> create(String countryCode, int maxUsages);
}
