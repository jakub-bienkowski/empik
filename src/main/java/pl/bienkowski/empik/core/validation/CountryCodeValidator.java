package pl.bienkowski.empik.core.validation;

import org.springframework.stereotype.Component;
import pl.bienkowski.empik.core.exception.InvalidCountryCodeException;

import java.util.Locale;
import java.util.Set;

@Component
public final class CountryCodeValidator implements Validator<String> {

    private static final Set<String> COUNTRY_CODES = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

    @Override
    public void validate(String countryCode) {
        if (!COUNTRY_CODES.contains(countryCode)) {
            throw new InvalidCountryCodeException();
        }
    }
}
