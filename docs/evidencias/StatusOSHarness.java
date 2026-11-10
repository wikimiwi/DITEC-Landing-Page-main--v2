import com.ditec.assistencia.enums.*;
import java.util.*;
import static com.ditec.assistencia.enums.StatusOS.*;
public class Harness {
    static int ok=0, falha=0;
    static void chk(String n, boolean c){ if(c) ok++; else {falha++; System.out.println("FALHOU: "+n);} }
    public static void main(String[] a){
        // --- replica dos 4 testes de StatusOSTest ---
        chk("fluxo1", AGENDADO.podeTransicionarPara(EM_DIAGNOSTICO));
        chk("fluxo2", EM_DIAGNOSTICO.podeTransicionarPara(AGUARDANDO_APROVACAO));
        chk("fluxo3", AGUARDANDO_APROVACAO.podeTransicionarPara(EM_MANUTENCAO));
        chk("fluxo4", EM_MANUTENCAO.podeTransicionarPara(AGUARDANDO_PECAS));
        chk("fluxo5", AGUARDANDO_PECAS.podeTransicionarPara(EM_MANUTENCAO));
        chk("fluxo6", EM_MANUTENCAO.podeTransicionarPara(EM_TESTES));
        chk("fluxo7", EM_TESTES.podeTransicionarPara(EM_MANUTENCAO));
        chk("fluxo8", EM_TESTES.podeTransicionarPara(CONCLUIDO));
        chk("fluxo9", CONCLUIDO.podeTransicionarPara(AGUARDANDO_RETIRADA));
        chk("fluxo10", AGUARDANDO_RETIRADA.podeTransicionarPara(ENTREGUE));
        chk("inv1", !AGENDADO.podeTransicionarPara(CONCLUIDO));
        chk("inv2", !AGENDADO.podeTransicionarPara(EM_TESTES));
        chk("inv3", !EM_MANUTENCAO.podeTransicionarPara(EM_DIAGNOSTICO));
        chk("inv4", !CONCLUIDO.podeTransicionarPara(CANCELADO));
        chk("inv5", !EM_TESTES.podeTransicionarPara(CANCELADO));
        chk("inv6", !AGENDADO.podeTransicionarPara(AGENDADO));
        chk("inv7", !AGENDADO.podeTransicionarPara(null));
        for (StatusOS s: values()) { chk("term-E-"+s, !ENTREGUE.podeTransicionarPara(s)); chk("term-C-"+s, !CANCELADO.podeTransicionarPara(s)); }
        chk("sync1", AGENDADO.paraStatusAgendamento()==StatusAgendamento.AGENDADO);
        chk("sync2", EM_DIAGNOSTICO.paraStatusAgendamento()==StatusAgendamento.EM_ATENDIMENTO);
        chk("sync3", EM_TESTES.paraStatusAgendamento()==StatusAgendamento.EM_ATENDIMENTO);
        chk("sync4", ENTREGUE.paraStatusAgendamento()==StatusAgendamento.CONCLUIDO);
        chk("sync5", CANCELADO.paraStatusAgendamento()==StatusAgendamento.CANCELADO);
        System.out.println("Replica dos 4 testes do grupo: "+ok+" verificações OK, "+falha+" falhas");

        // --- análise do grafo (propriedades de projeto, não presentes nos testes do grupo) ---
        Set<StatusOS> alc = new LinkedHashSet<>(); Deque<StatusOS> f = new ArrayDeque<>(List.of(AGENDADO));
        while(!f.isEmpty()){ StatusOS s=f.poll(); if(alc.add(s)) f.addAll(s.proximos()); }
        Set<StatusOS> inalc = new LinkedHashSet<>(List.of(values())); inalc.removeAll(alc);
        System.out.println("Alcançáveis a partir de AGENDADO: "+alc.size()+"/10; inalcançáveis: "+inalc);
        List<String> semSaida = new ArrayList<>();
        for (StatusOS s: values()) if(!s.isTerminal() && s.proximos().isEmpty()) semSaida.add(s.name());
        System.out.println("Estados não-terminais sem saída (becos): "+semSaida);
        int arestas=0; for (StatusOS s: values()) arestas+=s.proximos().size();
        System.out.println("Total de transições permitidas: "+arestas);
        // AGENDADO -> CONCLUIDO sem passar por /finalizar?  (CONCLUIDO só entra por EM_TESTES)
        List<String> entradasConcluido = new ArrayList<>();
        for (StatusOS s: values()) if (s.podeTransicionarPara(CONCLUIDO)) entradasConcluido.add(s.name());
        System.out.println("Quem pode ir para CONCLUIDO: "+entradasConcluido);
        List<String> cancelaveis = new ArrayList<>();
        for (StatusOS s: values()) if (s.podeTransicionarPara(CANCELADO)) cancelaveis.add(s.name());
        System.out.println("Onde CANCELADO é permitido: "+cancelaveis);
        System.exit(falha==0?0:1);
    }
}
