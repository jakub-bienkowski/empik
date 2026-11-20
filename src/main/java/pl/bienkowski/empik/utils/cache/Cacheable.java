package pl.bienkowski.empik.utils.cache;

import reactor.core.publisher.Mono;

import java.util.function.Supplier;

@FunctionalInterface
public interface Cacheable<K, V> {

    /**
     * Retrieves a cached value or invokes the loader when none is available.
     *
     * @param key lookup key
     * @param loader supplies the value when needed
     * @return the cached or loaded value, an empty result, or the loader's error
     */
    Mono<V> getOrLoad(K key, Supplier<Mono<V>> loader);
}
