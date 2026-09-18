package com.ditec.assistencia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP usado exclusivamente para chamar o roteador da Hugging Face.
 * Timeouts curtos para nao deixar o usuario esperando indefinidamente se a
 * IA externa estiver lenta/fora do ar (usamos SimpleClientHttpRequestFactory
 * por ser a opcao mais estavel entre versoes do Spring, sem dependencias
 * extras como Apache HttpClient).
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient huggingFaceRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return RestClient.builder().requestFactory(factory).build();
    }
}
