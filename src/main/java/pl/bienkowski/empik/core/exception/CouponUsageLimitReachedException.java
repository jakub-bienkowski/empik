package pl.bienkowski.empik.core.exception;

public final class CouponUsageLimitReachedException extends RuntimeException {

    public CouponUsageLimitReachedException() {
        super("Coupon usage limit reached");
    }
}
