package pl.bienkowski.empik.core.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.bienkowski.empik.core.exception.ClientCountryUnavailableException;
import pl.bienkowski.empik.core.exception.InvalidCountryCodeException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponRedeemValidatorTest {

    private final CountryCodeValidator countryCodes = new CountryCodeValidator();
    private final CouponRedeemValidator validator = new CouponRedeemValidator(countryCodes);

    @ParameterizedTest
    @ValueSource(strings = {"1.1.1.1", "2001:4860:4860::8888"})
    void validateIp_whenIpIsALiteral_thenAcceptsIt(String ip) {
        assertThatCode(() -> validator.validateIp(ip)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "localhost", "999.1.1.1", "1.1.1.1,8.8.8.8", "[::1]", "fe80::1%1"})
    void validateIp_whenIpIsInvalidOrAmbiguous_thenRejectsIt(String ip) {
        assertThatThrownBy(() -> validator.validateIp(ip)).isInstanceOf(ClientCountryUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PL", "DE", "US"})
    void validateCountry_whenCountryCodeIsAssigned_thenAcceptsIt(String country) {
        assertThatCode(() -> validator.validateCountry(country)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZ", "XX", "pl", "POL", ""})
    void validateCountry_whenCountryCodeIsInvalid_thenWrapsInvalidCountryCodeException(String country) {
        assertThatThrownBy(() -> countryCodes.validate(country)).isInstanceOf(InvalidCountryCodeException.class);
        assertThatThrownBy(() -> validator.validateCountry(country))
                .isInstanceOf(ClientCountryUnavailableException.class)
                .hasCauseInstanceOf(InvalidCountryCodeException.class);
    }
}
