package pl.bienkowski.empik.utils.ip;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import pl.bienkowski.empik.utils.cache.Cacheable;
import pl.bienkowski.empik.utils.cache.CaffeineCache;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Primary
@Component
public final class CachedCountryResolver implements CountryResolver {

    private final CountryResolver delegate;
    private final Cacheable<String, String> cache;

    public CachedCountryResolver(IpResolver delegate,
                                 @Value("${ipinfo.cache.maximum-size}") long maximumSize,
                                 @Value("${ipinfo.cache.ttl}") Duration ttl) {
        this.delegate = delegate;
        this.cache = new CaffeineCache<>(Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .buildAsync());
    }

    @Override
    public Mono<String> resolve(String ip) {
        return cache.getOrLoad(ip, () -> delegate.resolve(ip));
    }
}
