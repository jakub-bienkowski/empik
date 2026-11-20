package pl.bienkowski.empik.utils.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;

import java.util.concurrent.CompletableFuture;
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
        return Mono.defer(() -> {
            var sharedLoad = cache.get(key, (ignored, _) -> loadAsSignal(loader));
            // Evict after completion even if every subscriber has cancelled.
            sharedLoad.thenAccept(signal -> removeUncacheableResult(key, sharedLoad, signal));

            // Also evict before delivering the result, so immediate retries start a new load.
            return Mono.fromFuture(sharedLoad, true)
                    .doOnNext(signal -> removeUncacheableResult(key, sharedLoad, signal))
                    .dematerialize();
        });
    }

    private CompletableFuture<Signal<V>> loadAsSignal(Supplier<Mono<V>> loader) {
        // Materializing avoids Caffeine logging potentially sensitive loader exceptions.
        return Mono.defer(loader).materialize().toFuture();
    }

    private void removeUncacheableResult(K key, CompletableFuture<Signal<V>> sharedLoad, Signal<V> result) {
        if (!result.isOnNext()) {
            cache.asMap().remove(key, sharedLoad);
        }
    }
}
