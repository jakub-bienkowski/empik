package pl.bienkowski.empik.core.exception;

public final class ClientCountryUnavailableException extends RuntimeException {

    private static final String MESSAGE = "Client country unavailable";

    public ClientCountryUnavailableException() {
        super(MESSAGE);
    }

    public ClientCountryUnavailableException(InvalidCountryCodeException cause) {
        super(MESSAGE, cause);
    }
}
