#!/usr/bin/env bash
# Fluxo completo da Ordem de Servico contra a API REAL (MySQL), com dados ficticios.
# Cria o proprio agendamento (horario livre) -> nao depende do estado do seed.
# Requer as contas de demonstracao do seed (perfil dev). Uso: bash teste-fluxo-os.sh
B=${DITEC_API_URL:-http://localhost:8080/api}
pass=0; fail=0
j() { curl -s -o /tmp/ditec_fluxo -w "%{http_code}" -H "Content-Type: application/json" "$@"; }
f() { python3 -c "import json,sys;d=json.load(open('/tmp/ditec_fluxo'));print(eval('d'+sys.argv[1]))" "$1"; }
ok() { if [ "$2" = "$3" ]; then echo "PASS  $1"; pass=$((pass+1)); else echo "FAIL  $1 (esperado '$2', obtido '$3') $(head -c 200 /tmp/ditec_fluxo)"; fail=$((fail+1)); fi; }
tok() { j -X POST $B/auth/login -d "{\"email\":\"$1\",\"senha\":\"$2\"}" >/dev/null; f "['token']"; }
ADM=$(tok admin@ditec.com.br admin123); TEC=$(tok tecnico@ditec.com.br tecnico123)
TEL="(11) 9$(printf '%04d' $((RANDOM%10000)))-$(printf '%04d' $((RANDOM%10000)))"
# 1-2. Cliente solicita atendimento / sistema registra
for d in $(seq 20 60); do D=$(python3 -c "import datetime as x;print(x.date.today()+x.timedelta(days=$d))"); [ "$(python3 -c "import datetime as x;print(x.date.fromisoformat('$D').weekday())")" = 6 ] && continue
  j "$B/agendamentos/disponibilidade?data=$D" >/dev/null; H=$(f "['horariosDisponiveis'][0] if d['horariosDisponiveis'] else ''"); [ -n "$H" ] && break; done
c=$(j -X POST $B/agendamentos -d "{\"nome\":\"Cliente Fluxo Teste\",\"telefone\":\"$TEL\",\"tipoAparelho\":\"Geladeira Consul\",\"descricaoProblema\":\"Nao gela\",\"endereco\":\"Rua Fluxo, 10\",\"bairro\":\"Pinheiros\",\"dataHora\":\"${D}T${H}:00\"}")
ok "1-2 cliente solicita; agendamento registrado (201)" 201 $c; P=$(f "['protocolo']"); AG=$(f "['id']")
# 3. Admin recebe a solicitacao
c=$(j -H "Authorization: Bearer $ADM" "$B/admin/agendamentos?size=200"); ok "3 admin ve a solicitacao pendente" "PENDENTE" "$(python3 -c "import json;d=json.load(open('/tmp/ditec_fluxo'));print([a['status'] for a in d['content'] if a['protocolo']=='$P'][0])")"
# 4. OS criada
c=$(j -H "Authorization: Bearer $ADM" "$B/admin/ordens-servico?protocolo=$P"); OS=$(f "[0]['id']"); ok "4 OS criada com o mesmo protocolo (AGENDADO)" "AGENDADO" "$(f "[0]['status']")"
c=$(j -X PUT -H "Authorization: Bearer $ADM" $B/admin/agendamentos/$AG/confirmar); ok "4b admin confirma a visita" 200 $c
# 5. Tecnico recebe a ordem
c=$(j -X PUT -H "Authorization: Bearer $ADM" $B/admin/ordens-servico/$OS/tecnico -d '{"tecnicoId":1}'); ok "5 admin atribui o tecnico" 200 $c
c=$(j -H "Authorization: Bearer $TEC" $B/ordens-servico); ok "5b tecnico ve a OS na sua lista" True "$(python3 -c "import json;print(any(o['id']==$OS for o in json.load(open('/tmp/ditec_fluxo'))))")"
# 6-7. Diagnostico e andamento
c=$(j -X PUT -H "Authorization: Bearer $TEC" $B/ordens-servico/$OS/status -d '{"status":"EM_DIAGNOSTICO","observacao":"Tecnico no local."}'); ok "6 tecnico inicia o diagnostico" 200 $c
PREV=$(python3 -c "import datetime as x;print(x.date.today()+x.timedelta(days=3))")
c=$(j -X PUT -H "Authorization: Bearer $TEC" $B/ordens-servico/$OS/diagnostico -d "{\"diagnostico\":\"Termostato com defeito.\",\"valorOrcamento\":180,\"previsaoConclusao\":\"$PREV\"}"); ok "6b diagnostico + orcamento + previsao" 200 $c
c=$(j -X PUT -H "Authorization: Bearer $TEC" $B/ordens-servico/$OS/status -d '{"status":"EM_MANUTENCAO","observacao":"Cliente aprovou por telefone."}'); ok "7 andamento: em manutencao" 200 $c
c=$(j -X POST -H "Authorization: Bearer $TEC" $B/ordens-servico/$OS/observacoes -d '{"texto":"Termostato substituido.","visivelCliente":true}'); ok "7b observacao registrada" 201 $c
c=$(j -X PUT -H "Authorization: Bearer $TEC" $B/ordens-servico/$OS/status -d '{"status":"EM_TESTES"}'); ok "7c andamento: em testes" 200 $c
# 8. Cliente acompanha pelo rastreamento
c=$(j -X POST $B/ordens-servico/rastreio -d "{\"protocolo\":\"$P\",\"telefone\":\"$TEL\"}"); ok "8 cliente rastreia: status atual EM_TESTES" "EM_TESTES" "$(f "['status']")"
ok "8b rastreio mostra diagnostico e previsao reais" "Termostato com defeito. $PREV" "$(f "['diagnostico']") $(f "['previsaoConclusao']")"
# 9. Tecnico registra a conclusao
c=$(j -X POST -H "Authorization: Bearer $TEC" $B/ordens-servico/$OS/finalizar -d '{"valor":180,"formaPagamento":"pix","descricaoFinal":"Termostato trocado; geladeira gelando.","pecasUtilizadas":"Termostato"}'); ok "9 tecnico conclui (5% Pix: 171.00)" "200 171.0" "$c $(f "['valor']")"
# 10. Admin finaliza o atendimento (entrega)
c=$(j -X PUT -H "Authorization: Bearer $ADM" $B/ordens-servico/$OS/status -d '{"status":"ENTREGUE","observacao":"Atendimento encerrado pelo administrador."}'); ok "10 admin finaliza o atendimento (ENTREGUE)" 200 $c
c=$(j -X POST $B/ordens-servico/rastreio -d "{\"protocolo\":\"$P\",\"telefone\":\"$TEL\"}")
ok "historico completo no rastreio (eventos >= 9) e etapa 5" "True 5" "$(python3 -c "import json;d=json.load(open('/tmp/ditec_fluxo'));print(len(d['timeline'])>=9, d['etapa'])")"
c=$(j -X PUT -H "Authorization: Bearer $ADM" $B/ordens-servico/$OS/status -d '{"status":"EM_MANUTENCAO"}'); ok "OS entregue nao muda mais (422)" 422 $c
echo "PROTOCOLO=$P TELEFONE=$TEL"
echo "== $pass aprovados, $fail falharam =="
