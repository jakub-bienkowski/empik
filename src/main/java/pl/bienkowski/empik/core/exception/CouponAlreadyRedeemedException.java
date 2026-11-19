package pl.bienkowski.empik.core.exception;

public final class CouponAlreadyRedeemedException extends RuntimeException {

    public CouponAlreadyRedeemedException(Throwable cause) {
        super("Coupon already redeemed by this user", cause);
    }
}
