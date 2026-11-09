package com.ditec.assistencia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Cliente HTTP usado exclusivamente para chamar a Google Gemini API.
 *
 * Uma unica instancia do java.net.http.HttpClient (JDK) reaproveita as conexoes
 * TLS (keep-alive / HTTP/2), reduzindo a latencia das chamadas seguintes.
 * Timeouts configuraveis: a interface nunca fica esperando indefinidamente.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient geminiRestClient(GeminiProperties props) {
        long connect = props.connectTimeoutMs() != null ? props.connectTimeoutMs() : 5_000L;
        long read = props.readTimeoutMs() != null ? props.readTimeoutMs() : 15_000L;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connect))
                .version(HttpClient.Version.HTTP_2)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(read));
        return RestClient.builder().requestFactory(factory).build();
    }
}
