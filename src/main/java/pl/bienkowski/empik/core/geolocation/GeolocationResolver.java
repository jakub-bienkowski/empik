package pl.bienkowski.empik.core.geolocation;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface GeolocationResolver {

    /**
     * Resolves the country for an IP address.
     *
     * @param ip IPv4 or IPv6 address to look up
     * @return the country code, an empty result if no country was found, or an error if lookup fails
     */
    Mono<String> resolve(String ip);
}
