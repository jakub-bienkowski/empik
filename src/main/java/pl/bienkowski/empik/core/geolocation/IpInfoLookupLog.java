package pl.bienkowski.empik.core.geolocation;

import io.netty.channel.ConnectTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

final class IpInfoLookupLog {

    private static final Logger log = LoggerFactory.getLogger(IpGeolocationResolver.class);

    private IpInfoLookupLog() {
    }

    static void missingToken() {
        log.warn("IPinfo lookup unavailable: token not configured");
    }

    static void logRetry(long attempt, Throwable error) {
        log.debug("Retrying IPinfo lookup: attempt={}, reason={}", attempt, getFailureReason(error));
    }

    static void logFailure(Throwable error, long started) {
        log.warn("IPinfo lookup failed: reason={}, durationMs={}", getFailureReason(error), getElapsedTime(started));
    }

    static void logMissingCountry(long started) {
        log.warn("IPinfo lookup returned no country code: durationMs={}", getElapsedTime(started));
    }

    private static long getElapsedTime(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private static String getFailureReason(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            return "HTTP " + response.getStatusCode().value();
        }
        Throwable cause = error instanceof WebClientRequestException ? error.getCause() : error;
        if (cause instanceof ConnectTimeoutException) {
            return "connection timeout";
        }
        if (cause instanceof io.netty.handler.timeout.TimeoutException) {
            return "response timeout";
        }
        if (cause instanceof TimeoutException) {
            return "lookup timeout";
        }
        if (error instanceof WebClientRequestException) {
            return "network error";
        }
        return "provider response or client error";
    }
}
