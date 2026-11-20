package pl.bienkowski.empik.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ipinfo")
public record IpInfoProperties(
        String baseUrl,
        String token,
        int connectTimeoutMs,
        long responseTimeoutMs,
        long lookupTimeoutMs,
        Cache cache
) {
    public record Cache(long maximumSize, Duration ttl) {
    }
}
