package pl.bienkowski.empik.utils.ip;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface CountryResolver {

    /**
     * Resolves the country for an IP address. An empty result means no country was found.
     */
    Mono<String> resolve(String ip);
}
