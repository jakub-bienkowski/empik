package pl.bienkowski.empik.core.validation;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.bienkowski.empik.core.exception.ClientCountryUnavailableException;
import pl.bienkowski.empik.core.exception.InvalidCountryCodeException;

import java.net.InetAddress;
import java.util.regex.Pattern;

@Component
public final class CouponRedeemValidator {

    private static final Logger log = LoggerFactory.getLogger(CouponRedeemValidator.class);
    private static final Pattern IP_LITERAL_CHARS = Pattern.compile("[0-9A-Fa-f:.]{1,45}");

    private final CountryCodeValidator countryCodeValidator;

    public CouponRedeemValidator(CountryCodeValidator countryCodeValidator) {
        this.countryCodeValidator = countryCodeValidator;
    }

    public void validateIp(String ip) {
        if (!IP_LITERAL_CHARS.matcher(ip).matches()) {
            throw new ClientCountryUnavailableException();
        }
        try {
            InetAddress.ofLiteral(ip);
        } catch (IllegalArgumentException error) {
            throw new ClientCountryUnavailableException();
        }
    }

    public void validateCountry(String countryCode) {
        try {
            countryCodeValidator.validate(countryCode);
        } catch (InvalidCountryCodeException error) {
            log.warn("IPinfo lookup returned an invalid country code");
            throw new ClientCountryUnavailableException(error);
        }
    }
}
