package pl.bienkowski.empik.core.geolocation;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pl.bienkowski.empik.core.config.IpInfoProperties;
import pl.bienkowski.empik.utils.cache.Cacheable;
import pl.bienkowski.empik.utils.cache.CaffeineCache;
import reactor.core.publisher.Mono;

/**
 * Caches successful country lookups; empty and failed lookups are not retained.
 */
@Primary
@Component
public final class CachedGeolocationResolver implements GeolocationResolver {

    private final GeolocationResolver delegate;
    private final Cacheable<String, String> cache;

    public CachedGeolocationResolver(IpGeolocationResolver delegate,
                                     IpInfoProperties properties) {
        this.delegate = delegate;
        this.cache = new CaffeineCache<>(Caffeine.newBuilder()
                .maximumSize(properties.cache().maximumSize())
                .expireAfterWrite(properties.cache().ttl())
                .buildAsync());
    }

    @Override
    public Mono<String> resolve(String ip) {
        return cache.getOrLoad(ip, () -> delegate.resolve(ip));
    }
}
