package pl.bienkowski.empik.core.exception;

public final class InvalidCountryCodeException extends IllegalArgumentException {

    public InvalidCountryCodeException() {
        super("countryCode must be an officially assigned ISO 3166-1 alpha-2 code");
    }
}
