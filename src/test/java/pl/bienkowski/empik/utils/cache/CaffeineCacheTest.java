package pl.bienkowski.empik.utils.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import pl.bienkowski.empik.core.geolocation.GeolocationResolver;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CaffeineCacheTest {

    private static final String IP = "1.1.1.1";
    private static final Duration TTL = Duration.ofHours(1);
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(5);

    private final AtomicLong time = new AtomicLong();
    private final CaffeineCache<String, String> cache = new CaffeineCache<>(Caffeine.newBuilder()
            .ticker(time::get)
            .expireAfterWrite(TTL)
            .maximumSize(10)
            .buildAsync());
    private final GeolocationResolver loader = mock(GeolocationResolver.class);

    @Test
    void getOrLoad_whenValueExpires_thenLoadsItAgain() {
        when(loader.resolve(IP)).thenReturn(Mono.just("PL"), Mono.just("DE"));

        assertCountry("PL");
        time.addAndGet(TTL.minusSeconds(1).toNanos());
        assertCountry("PL");
        verify(loader).resolve(IP);

        time.addAndGet(Duration.ofSeconds(2).toNanos());
        assertCountry("DE");
    }

    @Test
    void getOrLoad_whenLookupIsInFlight_thenSharesItEvenIfOneSubscriberCancels() {
        var result = Sinks.<String>one();
        when(loader.resolve(IP)).thenReturn(result.asMono());
        var first = lookup().subscribe();

        StepVerifier.create(lookup())
                .then(() -> {
                    first.dispose();
                    assertThat(result.tryEmitValue("PL")).isEqualTo(Sinks.EmitResult.OK);
                })
                .expectNext("PL")
                .expectComplete()
                .verify(TEST_TIMEOUT);

        verify(loader).resolve(IP);
        verifyNoMoreInteractions(loader);
    }

    @Test
    void getOrLoad_whenLookupFailsOrReturnsEmpty_thenDoesNotRetainTheResult() {
        var failure = new IllegalStateException("lookup failed");
        when(loader.resolve(IP)).thenReturn(Mono.error(failure), Mono.empty(), Mono.just("PL"));

        StepVerifier.create(lookup()).expectErrorMatches(error -> error == failure).verify(TEST_TIMEOUT);
        StepVerifier.create(lookup()).expectComplete().verify(TEST_TIMEOUT);
        assertCountry("PL");
    }

    @Test
    void getOrLoad_whenAllSubscribersCancelAndLoadFails_thenDoesNotRetainFailure() {
        var result = Sinks.<String>one();
        when(loader.resolve(IP)).thenReturn(result.asMono(), Mono.just("PL"));
        var subscription = lookup().subscribe();
        subscription.dispose();
        result.tryEmitError(new IllegalStateException("lookup failed"));

        assertCountry("PL");
    }

    @Test
    void getOrLoad_whenLookupFails_thenCaffeineDoesNotLogException() {
        var logger = Logger.getLogger("com.github.benmanes.caffeine.cache.LocalAsyncCache");
        var records = new java.util.ArrayList<LogRecord>();
        var handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            var failure = new IllegalStateException("private provider URL");
            when(loader.resolve(IP)).thenReturn(Mono.error(failure));
            StepVerifier.create(lookup()).expectErrorMatches(error -> error == failure).verify(TEST_TIMEOUT);
            assertThat(records).isEmpty();
        } finally {
            logger.removeHandler(handler);
        }
    }

    private Mono<String> lookup() {
        return cache.getOrLoad(IP, () -> loader.resolve(IP));
    }

    private void assertCountry(String country) {
        StepVerifier.create(lookup()).expectNext(country).expectComplete().verify(TEST_TIMEOUT);
    }
}
