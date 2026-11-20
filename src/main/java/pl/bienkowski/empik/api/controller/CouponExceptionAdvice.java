package pl.bienkowski.empik.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import pl.bienkowski.empik.core.exception.ClientCountryUnavailableException;
import pl.bienkowski.empik.core.exception.CouponAlreadyRedeemedException;
import pl.bienkowski.empik.core.exception.CouponCodeAlreadyExistsException;
import pl.bienkowski.empik.core.exception.CouponCountryMismatchException;
import pl.bienkowski.empik.core.exception.CouponNotFoundException;
import pl.bienkowski.empik.core.exception.CouponUsageLimitReachedException;
import pl.bienkowski.empik.core.exception.InvalidCountryCodeException;
import pl.bienkowski.empik.core.exception.CountryLookupUnavailableException;

@RestControllerAdvice(assignableTypes = CouponController.class)
public class CouponExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(CouponExceptionAdvice.class);

    @ExceptionHandler(InvalidCountryCodeException.class)
    public ProblemDetail invalidCountry(InvalidCountryCodeException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    @ExceptionHandler({ClientCountryUnavailableException.class, CouponCountryMismatchException.class})
    public ProblemDetail forbidden(RuntimeException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, error.getMessage());
    }

    @ExceptionHandler(CouponNotFoundException.class)
    public ProblemDetail notFound(CouponNotFoundException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler({
            CouponAlreadyRedeemedException.class,
            CouponCodeAlreadyExistsException.class,
            CouponUsageLimitReachedException.class
    })
    public ProblemDetail conflict(RuntimeException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
    }

    @ExceptionHandler(CountryLookupUnavailableException.class)
    public ProblemDetail lookupUnavailable() {
        return ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ProblemDetail invalidRequest(ServerWebInputException error) {
        return error.getBody();
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail exception(Exception error) {
        log.error("Unexpected coupon request failure", error);
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
