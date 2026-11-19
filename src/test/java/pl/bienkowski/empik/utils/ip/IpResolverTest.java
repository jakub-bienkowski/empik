package pl.bienkowski.empik.utils.ip;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import pl.bienkowski.empik.core.exception.CountryLookupUnavailableException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IpResolverTest {

    @Test
    void resolve_whenProviderRecoversAfterThree503Responses_thenReturnsCountry() {
        var attempts = new AtomicInteger();
        var client = WebClient.builder().exchangeFunction(request -> Mono.just(
                attempts.incrementAndGet() <= 3
                        ? ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()
                        : ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body("{\"country_code\":\"PL\"}").build())).build();
        var resolver = new IpResolver(client, "test-token", 1000);

        StepVerifier.withVirtualTime(() -> resolver.resolve("1.1.1.1"))
                .thenAwait(Duration.ofSeconds(2))
                .expectNext("PL")
                .expectComplete()
                .verify(Duration.ofSeconds(5));
        assertThat(attempts.get()).isEqualTo(4);
    }

    @Test
    void resolve_whenProviderKeepsTimingOut_thenFailsAfterThreeRetries() {
        var attempts = new AtomicInteger();
        var client = WebClient.builder().exchangeFunction(request -> {
            attempts.incrementAndGet();
            return Mono.never();
        }).build();
        var resolver = new IpResolver(client, "test-token", 1000);

        StepVerifier.withVirtualTime(() -> resolver.resolve("1.1.1.1"))
                .thenAwait(Duration.ofSeconds(6))
                .expectError(CountryLookupUnavailableException.class)
                .verify(Duration.ofSeconds(5));
        assertThat(attempts.get()).isEqualTo(4);
    }

    @Test
    void resolve_whenProviderReturns500_thenFailsWithoutRetrying() {
        var attempts = new AtomicInteger();
        var client = WebClient.builder().exchangeFunction(request -> {
            attempts.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());
        }).build();
        var resolver = new IpResolver(client, "test-token", 1000);

        StepVerifier.create(resolver.resolve("1.1.1.1"))
                .expectError(CountryLookupUnavailableException.class)
                .verify(Duration.ofSeconds(5));
        assertThat(attempts.get()).isEqualTo(1);
    }
}
