package pl.bienkowski.empik.core.exception;

public final class CouponCodeAlreadyExistsException extends RuntimeException {

    public CouponCodeAlreadyExistsException() {
        super("Coupon code already exists for this country");
    }
}
