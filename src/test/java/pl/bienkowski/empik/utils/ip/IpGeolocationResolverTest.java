package pl.bienkowski.empik.utils.ip;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import pl.bienkowski.empik.core.config.IpInfoProperties;
import pl.bienkowski.empik.core.exception.CountryLookupUnavailableException;
import pl.bienkowski.empik.core.geolocation.IpGeolocationResolver;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IpGeolocationResolverTest {

    @Test
    void resolve_whenProviderRejectsToken_thenFails() {
        var client = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.UNAUTHORIZED).build())).build();
        var resolver = new IpGeolocationResolver(client, properties("private-token"));

        StepVerifier.create(resolver.resolve("8.8.8.8"))
                .expectError(CountryLookupUnavailableException.class)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void resolve_whenProviderOmitsCountry_thenReturnsEmpty() {
        var client = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                        .body("{}").build())).build();
        var resolver = new IpGeolocationResolver(client, properties("test-token"));

        StepVerifier.create(resolver.resolve("8.8.8.8")).verifyComplete();
    }

    @Test
    void resolve_sendsRequestedIpAndConfiguredToken() {
        var client = WebClient.builder().baseUrl("https://example.test")
                .exchangeFunction(request -> {
                    assertThat(request.url().getPath()).isEqualTo("/lite/8.8.8.8");
                    assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer configured-token");
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("{\"country_code\":\"US\"}").build());
                }).build();

        var resolver = new IpGeolocationResolver(client, properties("configured-token"));

        StepVerifier.create(resolver.resolve("8.8.8.8"))
                .expectNext("US")
                .verifyComplete();
    }

    @Test
    void resolve_whenTokenIsBlank_thenDoesNotMakeRequest() {
        var attempts = new AtomicInteger();
        var client = WebClient.builder().exchangeFunction(request -> {
            attempts.incrementAndGet();
            return Mono.error(new AssertionError("Unexpected provider request"));
        }).build();
        var resolver = new IpGeolocationResolver(client, properties(""));

        StepVerifier.create(resolver.resolve("8.8.8.8"))
                .expectError(CountryLookupUnavailableException.class)
                .verify(Duration.ofSeconds(5));
        assertThat(attempts.get()).isZero();
    }

    @Test
    void resolve_whenProviderRecoversAfterThree503Responses_thenReturnsCountry() {
        var attempts = new AtomicInteger();
        var client = WebClient.builder().exchangeFunction(request -> Mono.just(
                attempts.incrementAndGet() <= 3
                        ? ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()
                        : ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body("{\"country_code\":\"PL\"}").build())).build();
        var resolver = new IpGeolocationResolver(client, properties("test-token"));

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
        var resolver = new IpGeolocationResolver(client, properties("test-token"));

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
        var resolver = new IpGeolocationResolver(client, properties("test-token"));

        StepVerifier.create(resolver.resolve("1.1.1.1"))
                .expectError(CountryLookupUnavailableException.class)
                .verify(Duration.ofSeconds(5));
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void resolve_whenResubscribed_retriesIndependently() {
        var requests = new AtomicInteger();
        var client = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(requests.incrementAndGet() <= 4
                        ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED).build())).build();
        var resolver = new IpGeolocationResolver(client, properties("test-token"));
        var lookup = resolver.resolve("1.1.1.1");

        StepVerifier.withVirtualTime(() -> lookup)
                .thenAwait(Duration.ofSeconds(2))
                .expectError(CountryLookupUnavailableException.class)
                .verify(Duration.ofSeconds(5));
        StepVerifier.create(lookup)
                .expectError(CountryLookupUnavailableException.class)
                .verify(Duration.ofSeconds(5));

        assertThat(requests.get()).isEqualTo(5);
    }

    private static IpInfoProperties properties(String token) {
        return new IpInfoProperties("https://example.test", token, 1000, 2000, 1000,
                new IpInfoProperties.Cache(10000, Duration.ofHours(1)));
    }
}
