package com.ditec.assistencia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Sobe o contexto completo do Spring com H2 (perfil "test") — falha cedo se algum bean estiver mal configurado. */
@SpringBootTest
@ActiveProfiles("test")
class DitecApplicationTests {

    @Test
    void contextLoads() {
        // se o contexto nao subir, o teste falha aqui — cobre erros de
        // configuracao (beans, propriedades obrigatorias, mapeamento JPA).
    }
}
