package com.ditec.assistencia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Ponto de entrada da API da DITEC Assistencia Tecnica.
 *
 * Este backend substitui os dados simulados (localStorage / OS_DATA / OCUPADOS)
 * do frontend por persistencia real em MySQL, autenticacao real (Spring Security
 * + JWT + BCrypt) e um proxy seguro para o chatbot de IA (Hugging Face).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DitecApplication {
    public static void main(String[] args) {
        SpringApplication.run(DitecApplication.class, args);
    }
}
