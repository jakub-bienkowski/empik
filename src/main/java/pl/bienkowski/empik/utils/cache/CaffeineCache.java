package pl.bienkowski.empik.utils.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.util.function.Supplier;

/**
 * Shares in-flight loads between callers without letting one cancellation cancel the shared
 * load. Empty and failed results are removed so subsequent calls can retry.
 *
 * @param <K> cache key type
 * @param <V> cached value type
 */
public final class CaffeineCache<K, V> implements Cacheable<K, V> {

    private final AsyncCache<K, Signal<V>> cache;

    public CaffeineCache(AsyncCache<K, Signal<V>> cache) {
        this.cache = cache;
    }

    @Override
    public Mono<V> getOrLoad(K key, Supplier<Mono<V>> loader) {
        // One caller cancelling must not cancel an in-flight lookup shared by others.
        return Mono.defer(() -> {
            // Materializing failures prevents Caffeine from logging loader exceptions with sensitive details.
            var future = cache.get(key, (ignored, _) -> Mono.defer(loader).materialize().toFuture());
            future.thenAccept(signal -> {
                if (!signal.isOnNext()) {
                    cache.asMap().remove(key, future);
                }
            });
            return Mono.fromFuture(future, true)
                    .doOnNext(signal -> {
                        if (!signal.isOnNext()) {
                            cache.asMap().remove(key, future);
                        }
                    })
                    .dematerialize();
        });
    }
}
