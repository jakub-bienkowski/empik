package pl.bienkowski.empik.core.geolocation;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pl.bienkowski.empik.core.config.IpInfoProperties;
import pl.bienkowski.empik.core.exception.CountryLookupUnavailableException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Resolves countries using IPinfo Lite, applying a timeout to each request attempt.
 */
@Component
public final class IpGeolocationResolver implements GeolocationResolver {

    private static final String IP_ENDPOINT = "/lite/{ip}";

    private final WebClient client;
    private final String token;
    private final Duration lookupTimeout;

    public IpGeolocationResolver(@Qualifier("ipInfoWebClient") WebClient client,
                                 IpInfoProperties properties) {
        this.client = client;
        this.token = properties.token();
        this.lookupTimeout = Duration.ofMillis(properties.lookupTimeoutMs());
    }

    @Override
    public Mono<String> resolve(String ip) {
        if (token.isBlank()) {
            IpInfoLookupLog.missingToken();
            return Mono.error(new CountryLookupUnavailableException());
        }

        return Mono.defer(() -> lookup(ip));
    }

    private Mono<String> lookup(String ip) {
        long started = System.nanoTime();
        return requestCountry(ip)
                .retryWhen(IpInfoRetryPolicy.LOOKUP_RETRY)
                .doOnError(error -> IpInfoLookupLog.logFailure(error, started))
                .switchIfEmpty(Mono.defer(() -> {
                    IpInfoLookupLog.logMissingCountry(started);
                    return Mono.empty();
                }))
                .onErrorMap(Exception.class, CountryLookupUnavailableException::new);
    }

    private Mono<String> requestCountry(String ip) {
        return Mono.defer(() -> client.get()
                    .uri(IP_ENDPOINT, ip)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .bodyToMono(IpInfoResponse.class))
                .mapNotNull(IpInfoResponse::countryCode)
                .timeout(lookupTimeout);
    }

    private record IpInfoResponse(@JsonProperty("country_code") @Nullable String countryCode) {
    }
}
