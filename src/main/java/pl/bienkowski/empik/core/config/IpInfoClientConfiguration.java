package pl.bienkowski.empik.core.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class IpInfoClientConfiguration {

    @Bean
    WebClient ipInfoWebClient(@Value("${ipinfo.base-url}") String baseUrl,
                             @Value("${ipinfo.connect-timeout-ms}") int connectTimeoutMs,
                             @Value("${ipinfo.response-timeout-ms}") long responseTimeoutMs) {

        var httpClient = HttpClient.create()
                .disableRetry(true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
