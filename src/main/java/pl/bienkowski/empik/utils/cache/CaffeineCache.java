package pl.bienkowski.empik.utils.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

public final class CaffeineCache<K, V> implements Cacheable<K, V> {

    private final AsyncCache<K, V> cache;

    public CaffeineCache(AsyncCache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public Mono<V> getOrLoad(K key, Supplier<Mono<V>> loader) {
        // One caller cancelling must not cancel an in-flight lookup shared by others.
        return Mono.fromFuture(() -> cache.get(key, (ignored, _) -> Mono.defer(loader).toFuture()), true);
    }
}
