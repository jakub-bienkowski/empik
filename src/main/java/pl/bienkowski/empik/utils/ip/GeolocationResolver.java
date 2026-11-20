package pl.bienkowski.empik.utils.ip;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface GeolocationResolver {

    /**
     * Resolves the country for a given input. An empty result means no country was found.
     */
    Mono<String> resolve(String ip);
}
