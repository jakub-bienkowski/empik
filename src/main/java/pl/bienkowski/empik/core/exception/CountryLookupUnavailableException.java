package pl.bienkowski.empik.core.exception;

public final class CountryLookupUnavailableException extends RuntimeException {

    private static final String MESSAGE = "IP country lookup is unavailable";

    public CountryLookupUnavailableException() {
        super(MESSAGE);
    }

    public CountryLookupUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
