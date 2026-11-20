package pl.bienkowski.empik.utils.ip;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import pl.bienkowski.empik.core.exception.CountryLookupUnavailableException;
import pl.bienkowski.empik.core.geolocation.IpGeolocationResolver;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IpGeolocationResolverTest {

    @Test
    void resolve_whenProviderFails_logsSafeReasonWithoutRequestDetails() {
        var logger = (Logger) LoggerFactory.getLogger(IpGeolocationResolver.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var client = WebClient.builder().exchangeFunction(request -> Mono.just(
                    ClientResponse.create(HttpStatus.UNAUTHORIZED).build())).build();
            var resolver = new IpGeolocationResolver(client, "private-token", 1000);
            StepVerifier.create(resolver.resolve("8.8.8.8"))
                    .expectError(CountryLookupUnavailableException.class)
                    .verify(Duration.ofSeconds(5));

            assertThat(appender.list).hasSize(1);
            var event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).contains("HTTP 401", "attempts=1", "durationMs=")
                    .doesNotContain("8.8.8.8", "private-token");
            assertThat(event.getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void resolve_whenProviderOmitsCountry_logsWithoutChangingEmptyResult() {
        var logger = (Logger) LoggerFactory.getLogger(IpGeolocationResolver.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var client = WebClient.builder().exchangeFunction(request -> Mono.just(
                    ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                            .body("{}").build())).build();
            var resolver = new IpGeolocationResolver(client, "test-token", 1000);
            StepVerifier.create(resolver.resolve("8.8.8.8")).verifyComplete();
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .singleElement().asString().contains("returned no country code");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
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
        var resolver = new IpGeolocationResolver(client, "configured-token", 1000);

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
        var resolver = new IpGeolocationResolver(client, "", 1000);

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
        var resolver = new IpGeolocationResolver(client, "test-token", 1000);

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
        var resolver = new IpGeolocationResolver(client, "test-token", 1000);

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
        var resolver = new IpGeolocationResolver(client, "test-token", 1000);

        StepVerifier.create(resolver.resolve("1.1.1.1"))
                .expectError(CountryLookupUnavailableException.class)
                .verify(Duration.ofSeconds(5));
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void resolve_whenResubscribed_tracksAttemptsPerSubscription() {
        var logger = (Logger) LoggerFactory.getLogger(IpGeolocationResolver.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var requests = new AtomicInteger();
            var client = WebClient.builder().exchangeFunction(request -> Mono.just(
                    ClientResponse.create(requests.incrementAndGet() <= 4
                            ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.UNAUTHORIZED).build())).build();
            var resolver = new IpGeolocationResolver(client, "test-token", 1000);
            var lookup = resolver.resolve("1.1.1.1");

            StepVerifier.withVirtualTime(() -> lookup)
                    .thenAwait(Duration.ofSeconds(2))
                    .expectError(CountryLookupUnavailableException.class)
                    .verify(Duration.ofSeconds(5));
            StepVerifier.create(lookup)
                    .expectError(CountryLookupUnavailableException.class)
                    .verify(Duration.ofSeconds(5));

            assertThat(requests.get()).isEqualTo(5);
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .filteredOn(message -> message.startsWith("IPinfo lookup failed"))
                    .satisfiesExactly(
                            message -> assertThat(message).contains("HTTP 503", "attempts=4"),
                            message -> assertThat(message).contains("HTTP 401", "attempts=1"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
