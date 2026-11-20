package pl.bienkowski.empik.core.exception;

public final class CouponNotFoundException extends RuntimeException {

    public CouponNotFoundException() {
        super("Coupon not found");
    }
}
