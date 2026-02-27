package com.familyhobbies.associationservice.config;

import com.familyhobbies.common.config.HelloAssoProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configures the {@link WebClient.Builder} bean used by HelloAsso adapters.
 * Applies connect and read timeouts from {@link HelloAssoProperties}.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder helloAssoWebClientBuilder(HelloAssoProperties properties) {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout())
            .responseTimeout(Duration.ofMillis(properties.getReadTimeout()))
            .doOnConnected(conn ->
                conn.addHandlerLast(
                    new ReadTimeoutHandler(properties.getReadTimeout(), TimeUnit.MILLISECONDS)
                )
            );

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
