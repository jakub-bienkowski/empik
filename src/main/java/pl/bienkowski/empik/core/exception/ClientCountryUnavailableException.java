package pl.bienkowski.empik.core.exception;

public final class ClientCountryUnavailableException extends RuntimeException {

    public ClientCountryUnavailableException() {
        super("Client country unavailable");
    }

    public ClientCountryUnavailableException(InvalidCountryCodeException cause) {
        super("Client country unavailable", cause);
    }
}
