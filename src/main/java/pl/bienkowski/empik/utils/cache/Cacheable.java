package pl.bienkowski.empik.utils.cache;

import reactor.core.publisher.Mono;

import java.util.function.Supplier;

@FunctionalInterface
public interface Cacheable<K, V> {

    Mono<V> getOrLoad(K key, Supplier<Mono<V>> loader);
}
