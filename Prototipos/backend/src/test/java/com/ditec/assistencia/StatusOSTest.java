package com.ditec.assistencia;

import com.ditec.assistencia.enums.StatusAgendamento;
import com.ditec.assistencia.enums.StatusOS;
import org.junit.jupiter.api.Test;

import static com.ditec.assistencia.enums.StatusOS.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Matriz de transicoes do ciclo de vida da OS (teste unitario, sem Spring). */
class StatusOSTest {

    @Test
    void fluxoPrincipalPermitido() {
        assertThat(AGENDADO.podeTransicionarPara(EM_DIAGNOSTICO)).isTrue();
        assertThat(EM_DIAGNOSTICO.podeTransicionarPara(AGUARDANDO_APROVACAO)).isTrue();
        assertThat(AGUARDANDO_APROVACAO.podeTransicionarPara(EM_MANUTENCAO)).isTrue();
        assertThat(EM_MANUTENCAO.podeTransicionarPara(AGUARDANDO_PECAS)).isTrue();
        assertThat(AGUARDANDO_PECAS.podeTransicionarPara(EM_MANUTENCAO)).isTrue();
        assertThat(EM_MANUTENCAO.podeTransicionarPara(EM_TESTES)).isTrue();
        assertThat(EM_TESTES.podeTransicionarPara(EM_MANUTENCAO)).isTrue(); // reprovado no teste
        assertThat(EM_TESTES.podeTransicionarPara(CONCLUIDO)).isTrue();
        assertThat(CONCLUIDO.podeTransicionarPara(AGUARDANDO_RETIRADA)).isTrue();
        assertThat(AGUARDANDO_RETIRADA.podeTransicionarPara(ENTREGUE)).isTrue();
    }

    @Test
    void transicoesInvalidas() {
        assertThat(AGENDADO.podeTransicionarPara(CONCLUIDO)).isFalse();
        assertThat(AGENDADO.podeTransicionarPara(EM_TESTES)).isFalse();
        assertThat(EM_MANUTENCAO.podeTransicionarPara(EM_DIAGNOSTICO)).isFalse();
        assertThat(CONCLUIDO.podeTransicionarPara(CANCELADO)).isFalse();
        assertThat(EM_TESTES.podeTransicionarPara(CANCELADO)).isFalse();
        assertThat(AGENDADO.podeTransicionarPara(AGENDADO)).isFalse();
        assertThat(AGENDADO.podeTransicionarPara(null)).isFalse();
    }

    @Test
    void estadosTerminaisNaoMudam() {
        for (StatusOS s : StatusOS.values()) {
            assertThat(ENTREGUE.podeTransicionarPara(s)).isFalse();
            assertThat(CANCELADO.podeTransicionarPara(s)).isFalse();
        }
    }

    @Test
    void sincronizaComStatusDoAgendamento() {
        assertThat(AGENDADO.paraStatusAgendamento()).isEqualTo(StatusAgendamento.AGENDADO);
        assertThat(EM_DIAGNOSTICO.paraStatusAgendamento()).isEqualTo(StatusAgendamento.EM_ATENDIMENTO);
        assertThat(EM_TESTES.paraStatusAgendamento()).isEqualTo(StatusAgendamento.EM_ATENDIMENTO);
        assertThat(ENTREGUE.paraStatusAgendamento()).isEqualTo(StatusAgendamento.CONCLUIDO);
        assertThat(CANCELADO.paraStatusAgendamento()).isEqualTo(StatusAgendamento.CANCELADO);
    }
}
