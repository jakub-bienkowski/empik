package pl.bienkowski.empik.utils.ip;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.channel.ConnectTimeoutException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import pl.bienkowski.empik.core.exception.CountryLookupUnavailableException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public final class IpResolver {

    private static final String IP_ENDPOINT = "/lite/{ip}";
    private static final Retry LOOKUP_RETRY = Retry.backoff(3, Duration.ofMillis(100))
            .filter(IpResolver::isRetryable)
            .onRetryExhaustedThrow((_, signal) -> signal.failure());

    private final WebClient client;
    private final String token;
    private final Duration lookupTimeout;

    public IpResolver(@Qualifier("ipInfoWebClient") WebClient client,
                      @Value("${ipinfo.token}") String token,
                      @Value("${ipinfo.lookup-timeout-ms}") long lookupTimeoutMs) {
        this.client = client;
        this.token = token;
        this.lookupTimeout = Duration.ofMillis(lookupTimeoutMs);
    }

    public Mono<String> resolve(String ip) {
        if (token.isBlank()) {
            return Mono.error(CountryLookupUnavailableException::new);
        }

        return Mono.defer(() -> client.get()
                .uri(IP_ENDPOINT, ip)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .bodyToMono(IpInfoResponse.class))
                .mapNotNull(IpInfoResponse::countryCode)
                .timeout(lookupTimeout)
                .retryWhen(LOOKUP_RETRY)
                .onErrorMap(Exception.class, CountryLookupUnavailableException::new);
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

    private record IpInfoResponse(@JsonProperty("country_code") @Nullable String countryCode) {
    }
}
