package pl.bienkowski.empik.core.exception;

public final class CountryLookupUnavailableException extends RuntimeException {

    public CountryLookupUnavailableException() {
        super("IP country lookup is unavailable");
    }

    public CountryLookupUnavailableException(Throwable cause) {
        super("IP country lookup is unavailable", cause);
    }
}
