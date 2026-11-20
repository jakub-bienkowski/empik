package pl.bienkowski.empik.core.geolocation;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pl.bienkowski.empik.core.exception.CountryLookupUnavailableException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public final class IpGeolocationResolver implements GeolocationResolver {

    private static final String IP_ENDPOINT = "/lite/{ip}";
    private final WebClient client;
    private final String token;
    private final Duration lookupTimeout;

    public IpGeolocationResolver(@Qualifier("ipInfoWebClient") WebClient client,
                                 @Value("${ipinfo.token}") String token,
                                 @Value("${ipinfo.lookup-timeout-ms}") long lookupTimeoutMs) {
        this.client = client;
        this.token = token;
        this.lookupTimeout = Duration.ofMillis(lookupTimeoutMs);
    }

    @Override
    public Mono<String> resolve(String ip) {
        return Mono.defer(() -> resolveOnSubscription(ip));
    }

    private Mono<String> resolveOnSubscription(String ip) {
        if (token.isBlank()) {
            IpInfoLookupLog.missingToken();
            return Mono.error(new CountryLookupUnavailableException());
        }

        var attempts = new AtomicInteger();
        long started = System.nanoTime();
        return requestCountry(ip, attempts)
                .retryWhen(IpInfoRetryPolicy.LOOKUP_RETRY)
                .doOnError(error -> IpInfoLookupLog.logFailure(error, attempts.get(), started))
                .switchIfEmpty(Mono.defer(() -> {
                    IpInfoLookupLog.logMissingCountry(attempts.get(), started);
                    return Mono.empty();
                }))
                .onErrorMap(Exception.class, CountryLookupUnavailableException::new);
    }

    private Mono<String> requestCountry(String ip, AtomicInteger attempts) {
        return Mono.defer(() -> {
            attempts.incrementAndGet();
            return client.get()
                    .uri(IP_ENDPOINT, ip)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .bodyToMono(IpInfoResponse.class);
                })
                .mapNotNull(IpInfoResponse::countryCode)
                .timeout(lookupTimeout);
    }

    private record IpInfoResponse(@JsonProperty("country_code") @Nullable String countryCode) {
    }
}
