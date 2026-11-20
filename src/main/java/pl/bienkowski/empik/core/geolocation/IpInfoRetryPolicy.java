package pl.bienkowski.empik.core.geolocation;

import io.netty.channel.ConnectTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Retries IPinfo timeouts and HTTP 503 responses up to three times with exponential backoff.
 * The last failure is propagated unchanged when retries are exhausted.
 */
final class IpInfoRetryPolicy {

    static final Retry LOOKUP_RETRY = Retry.backoff(3, Duration.ofMillis(100))
            .filter(IpInfoRetryPolicy::isRetryable)
            .doBeforeRetry(signal -> IpInfoLookupLog.logRetry(signal.totalRetries() + 2, signal.failure()))
            .onRetryExhaustedThrow((_, signal) -> signal.failure());

    private IpInfoRetryPolicy() {
    }

    private static boolean isRetryable(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            return response.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value();
        }

        Throwable cause = error instanceof WebClientRequestException ? error.getCause() : error;
        return cause instanceof TimeoutException
                || cause instanceof ConnectTimeoutException
                || cause instanceof io.netty.handler.timeout.TimeoutException;
    }
}
