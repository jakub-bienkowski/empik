package pl.bienkowski.empik.core.service;

import pl.bienkowski.empik.core.domain.Coupon;
import reactor.core.publisher.Mono;

public interface CouponService {

    /**
     * Creates a coupon whose code is unique within its country.
     *
     * @param code case-sensitive coupon code
     * @param countryCode uppercase ISO country code
     * @param maxUsages maximum number of redemptions
     * @return the created coupon, or an error if the country is invalid or the code already exists
     */
    Mono<Coupon> create(String code, String countryCode, int maxUsages);
}
