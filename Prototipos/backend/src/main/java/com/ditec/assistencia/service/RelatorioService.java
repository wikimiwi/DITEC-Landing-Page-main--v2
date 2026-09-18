package com.ditec.assistencia.service;

import com.ditec.assistencia.entity.Agendamento;
import com.ditec.assistencia.entity.Avaliacao;
import com.ditec.assistencia.entity.ChatLog;
import com.ditec.assistencia.entity.OrdemServico;
import com.ditec.assistencia.repository.AgendamentoRepository;
import com.ditec.assistencia.repository.AvaliacaoRepository;
import com.ditec.assistencia.repository.ChatLogRepository;
import com.ditec.assistencia.repository.OrdemServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Relatorios administrativos exportaveis em CSV (secao 14 do prompt mestre).
 *
 * Cuidado LGPD (secao 14/20): os relatorios trazem apenas os dados
 * operacionais necessarios para a gestao do atendimento — nenhum documento
 * de identificacao (o sistema nem coleta CPF/RG, fora do escopo do DRS) e
 * nenhuma senha/hash e' exportada em momento algum.
 */
@Service
@RequiredArgsConstructor
public class RelatorioService {

    private final AgendamentoRepository agendamentoRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final ChatLogRepository chatLogRepository;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));

    public byte[] agendamentosCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtils.linha("Protocolo", "Cliente", "Telefone", "Aparelho", "Bairro",
                "Data/Hora", "Status", "Prioridade Gas", "Tecnico"));
        for (Agendamento a : agendamentoRepository.findAll()) {
            sb.append(CsvUtils.linha(
                    a.getProtocolo(), a.getNomeExibicao(), a.getTelefoneExibicao(), a.getTipoAparelho(),
                    a.getBairro(), a.getDataHora() != null ? DT_FMT.format(a.getDataHora()) : "",
                    a.getStatus(), a.isPrioridadeGas() ? "SIM" : "NAO",
                    a.getTecnico() != null ? a.getTecnico().getNome() : ""
            ));
        }
        return CsvUtils.bytesComBom(sb.toString());
    }

    public byte[] ordensServicoCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtils.linha("Protocolo", "Cliente", "Tecnico", "Status", "Valor", "Forma Pagamento",
                "Desconto", "Data Conclusao", "Garantia (dias)"));
        for (OrdemServico os : ordemServicoRepository.findAll()) {
            String cliente = os.getCliente() != null && os.getCliente().getUsuario() != null
                    ? os.getCliente().getUsuario().getNome() : os.getAgendamento().getNomeExibicao();
            sb.append(CsvUtils.linha(
                    os.getProtocolo(), cliente, os.getTecnico() != null ? os.getTecnico().getNome() : "",
                    os.getStatus(), os.getValor(), os.getFormaPagamento(), os.getDesconto(),
                    os.getDataConclusao() != null ? DT_FMT.format(os.getDataConclusao()) : "",
                    os.getGarantiaDias()
            ));
        }
        return CsvUtils.bytesComBom(sb.toString());
    }

    public byte[] avaliacoesCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtils.linha("Protocolo", "Cliente", "Nota", "Comentario", "Data"));
        for (Avaliacao av : avaliacaoRepository.findAllByOrderByCriadoEmDesc()) {
            String cliente = av.getCliente() != null && av.getCliente().getUsuario() != null
                    ? av.getCliente().getUsuario().getNome() : "";
            sb.append(CsvUtils.linha(
                    av.getOrdemServico().getProtocolo(), cliente, av.getNota(), av.getComentario(),
                    DT_FMT.format(av.getCriadoEm())
            ));
        }
        return CsvUtils.bytesComBom(sb.toString());
    }

    public byte[] chatbotCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtils.linha("Sessao", "Cliente", "Pergunta", "Resposta", "Data/Hora"));
        for (ChatLog log : chatLogRepository.findAllByOrderByDataHoraDesc()) {
            String cliente = log.getCliente() != null && log.getCliente().getUsuario() != null
                    ? log.getCliente().getUsuario().getNome() : "visitante";
            sb.append(CsvUtils.linha(log.getSessaoId(), cliente, log.getPergunta(), log.getResposta(),
                    DT_FMT.format(log.getDataHora())));
        }
        return CsvUtils.bytesComBom(sb.toString());
    }

    public long totalSessoesChatbot() {
        return chatLogRepository.countSessoesDistintas();
    }

    public long totalInteracoesChatbot() {
        return chatLogRepository.count();
    }
}
