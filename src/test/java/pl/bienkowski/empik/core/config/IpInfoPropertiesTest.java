package pl.bienkowski.empik.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pl.bienkowski.empik.core.geolocation.CachedGeolocationResolver;
import pl.bienkowski.empik.core.geolocation.IpGeolocationResolver;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class IpInfoPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(IpInfoClientConfiguration.class,
                    IpGeolocationResolver.class, CachedGeolocationResolver.class);

    @Test
    void bindsPropertiesAndCreatesGeolocationBeans() {
        contextRunner.withPropertyValues(
                "ipinfo.base-url=https://example.test",
                "ipinfo.token=test-token",
                "ipinfo.connect-timeout-ms=1234",
                "ipinfo.response-timeout-ms=2345",
                "ipinfo.lookup-timeout-ms=3456",
                "ipinfo.cache.maximum-size=42",
                "ipinfo.cache.ttl=15m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IpInfoProperties.class);
                    assertThat(context).hasSingleBean(IpGeolocationResolver.class);
                    assertThat(context).hasSingleBean(CachedGeolocationResolver.class);
                    var properties = context.getBean(IpInfoProperties.class);
                    assertThat(properties.baseUrl()).isEqualTo("https://example.test");
                    assertThat(properties.token()).isEqualTo("test-token");
                    assertThat(properties.connectTimeoutMs()).isEqualTo(1234);
                    assertThat(properties.responseTimeoutMs()).isEqualTo(2345);
                    assertThat(properties.lookupTimeoutMs()).isEqualTo(3456);
                    assertThat(properties.cache().maximumSize()).isEqualTo(42);
                    assertThat(properties.cache().ttl()).isEqualTo(Duration.ofMinutes(15));
                });
    }

    @Test
    void loadsApplicationConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(IpInfoProperties.class);
            assertThat(properties.baseUrl()).isNotBlank();
            assertThat(properties.token()).isNotNull();
            assertThat(properties.connectTimeoutMs()).isPositive();
            assertThat(properties.responseTimeoutMs()).isPositive();
            assertThat(properties.lookupTimeoutMs()).isPositive();
            assertThat(properties.cache()).isNotNull();
            assertThat(properties.cache().ttl()).isNotNull();
        });
    }
}
