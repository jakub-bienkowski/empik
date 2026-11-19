package pl.bienkowski.empik.core.exception;

public final class CouponCountryMismatchException extends RuntimeException {

    public CouponCountryMismatchException() {
        super("Country not allowed");
    }
}
