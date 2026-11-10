#!/usr/bin/env bash
# ============================================================================
# DITEC — Verificação independente da migração V2 (rastreamento da OS)
# sobre um banco que já contém dados da V1 (caminho de atualização real).
#
# Uso:  SOCK=/caminho/mdb.sock REPO=/caminho/do/repositorio ./testes_migracao_v2.sh
#
# LIMITAÇÃO DECLARADA: o servidor utilizado é MariaDB 10.11, e NÃO MySQL 8/9.
# A V2 original usa "ALTER TABLE ... DROP CHECK", sintaxe exclusiva do MySQL.
# Para executar a lógica de dados no MariaDB, a única alteração aplicada é a
# substituição de "DROP CHECK" por "DROP CONSTRAINT" em cópia temporária.
# O arquivo original do repositório não é modificado.
# ============================================================================
set -u
SOCK="${SOCK:?defina SOCK}"; REPO="${REPO:?defina REPO}"
MIGR="$REPO/Prototipos/backend/src/main/resources/db/migration"
README_BD="$REPO/Banco de Dados/database/README.md"
TMP="$(mktemp -d)"; S="mariadb --socket=$SOCK -uroot"
ok=0; falha=0

res() { # $1=APROVADO|REPROVADO  $2=descrição  $3=detalhe(opcional)
  [ "$1" = APROVADO ] && ok=$((ok+1)) || falha=$((falha+1))
  printf '%-10s %s%s\n' "$1" "$2" "${3:+ [$3]}"
}
t() { # descrição, esperado(ok|erro), sql, banco
  out=$($S "$4" -e "$3" 2>&1); rc=$?
  if [ "$2" = ok ]; then [ $rc -eq 0 ] && r=APROVADO || r=REPROVADO
  else [ $rc -ne 0 ] && r=APROVADO || r=REPROVADO; fi
  det=""; [ $rc -ne 0 ] && det="$(echo "$out" | grep -m1 -E '^ERROR' | cut -c1-110)"
  res "$r" "$1" "$det"
}
n() { $S "$1" -N -e "$2"; }
novo_banco() { $S -e "DROP DATABASE IF EXISTS $1; CREATE DATABASE $1 CHARACTER SET utf8mb4;"; $S "$1" < "$MIGR/V1__schema.sql"; }

# --- cópia adaptada da V2 (apenas DROP CHECK -> DROP CONSTRAINT) ---
sed 's/DROP CHECK/DROP CONSTRAINT/' "$MIGR/V2__rastreamento_os.sql" > "$TMP/V2_mariadb.sql"
echo "Servidor: $($S -N -e 'select version()')"
echo "Diferença entre a V2 original e a cópia usada:"; diff "$MIGR/V2__rastreamento_os.sql" "$TMP/V2_mariadb.sql" | grep -E '^[<>]'
echo

# --- semente: dados no formato gravado pela V1 ---
cat > "$TMP/seed.sql" <<'EOF'
INSERT INTO usuario (nome,email,senha_hash,tipo) VALUES
 ('Cliente Teste','cli.upg@teste.com','HASH_FICTICIO','CLIENTE'),
 ('Tecnico Teste','tec.upg@teste.com','HASH_FICTICIO','TECNICO');
INSERT INTO cliente (usuario_id) SELECT id FROM usuario WHERE email='cli.upg@teste.com';
INSERT INTO tecnico (usuario_id,nome,email) SELECT id,'Tecnico Teste','tec.upg@teste.com' FROM usuario WHERE email='tec.upg@teste.com';
INSERT INTO agendamento (protocolo,nome_contato,data_hora,tipo_aparelho,bairro,endereco,status,criado_em,atualizado_em) VALUES
 ('DITEC-2026-000101','Contato A','2026-09-01 08:00:00','Fogao','Moema','Rua A, 1','AGENDADO',NOW(),NOW()),
 ('DITEC-2026-000102','Contato B','2026-09-01 09:00:00','Geladeira','Moema','Rua B, 2','EM_ATENDIMENTO',NOW(),NOW()),
 ('DITEC-2026-000103','Contato C','2026-09-01 10:00:00','Lavadora','Moema','Rua C, 3','CONCLUIDO',NOW(),NOW()),
 ('DITEC-2026-000104','Contato D','2026-09-01 11:00:00','Microondas','Moema','Rua D, 4','CANCELADO',NOW(),NOW());
INSERT INTO ordem_servico (agendamento_id,protocolo,status,valor,criado_em,atualizado_em)
 SELECT a.id,a.protocolo,
   CASE a.protocolo WHEN 'DITEC-2026-000101' THEN 'AGENDADO' WHEN 'DITEC-2026-000102' THEN 'EM_ATENDIMENTO'
                    WHEN 'DITEC-2026-000103' THEN 'CONCLUIDO' ELSE 'CANCELADO' END,
   CASE a.protocolo WHEN 'DITEC-2026-000103' THEN 190.00 ELSE NULL END, NOW(), NOW()
 FROM agendamento a;
INSERT INTO timeline_os (ordem_servico_id,status_anterior,status_novo,observacao,alterado_por,data_hora)
 SELECT id,NULL,'AGENDADO','OS aberta','Sistema',NOW() FROM ordem_servico;
INSERT INTO timeline_os (ordem_servico_id,status_anterior,status_novo,alterado_por,data_hora)
 SELECT id,'AGENDADO','EM_ATENDIMENTO','Tecnico Teste',NOW() FROM ordem_servico WHERE status IN ('EM_ATENDIMENTO','CONCLUIDO');
INSERT INTO timeline_os (ordem_servico_id,status_anterior,status_novo,alterado_por,data_hora)
 SELECT id,'EM_ATENDIMENTO','CONCLUIDO','Tecnico Teste',NOW() FROM ordem_servico WHERE status='CONCLUIDO';
INSERT INTO timeline_os (ordem_servico_id,status_anterior,status_novo,alterado_por,data_hora)
 SELECT id,'AGENDADO','CANCELADO','Administrador',NOW() FROM ordem_servico WHERE status='CANCELADO';
EOF

echo "=== MV-A  V2 original no servidor de teste (esperado: falha por sintaxe exclusiva do MySQL) ==="
novo_banco mv_a; $S mv_a < "$MIGR/V2__rastreamento_os.sql" > "$TMP/a.out" 2>&1
grep -q "ERROR 1064" "$TMP/a.out" && res APROVADO "MV-A  V2 original não é portável para MariaDB (DROP CHECK)" "$(grep -m1 ERROR "$TMP/a.out" | cut -c1-60)…" \
                                  || res REPROVADO "MV-A  comportamento inesperado da V2 original"
$S -e "DROP DATABASE mv_a"

echo; echo "=== MV-B  V2 (cópia adaptada) sobre dados da V1 ==="
novo_banco mv_b; $S mv_b < "$TMP/seed.sql"
res APROVADO "MV-B1 dados da V1 semeados" "OS: $(n mv_b 'SELECT COUNT(*) FROM ordem_servico'), histórico: $(n mv_b 'SELECT COUNT(*) FROM timeline_os')"
$S mv_b < "$TMP/V2_mariadb.sql" > "$TMP/b.out" 2>&1; [ $? -eq 0 ] && res APROVADO "MV-B2 V2 aplicada sem erro" || res REPROVADO "MV-B2 V2 falhou" "$(grep -m1 ERROR "$TMP/b.out")"
c() { [ "$(n mv_b "$2")" = "$3" ] && res APROVADO "$1" "obtido: $3" || res REPROVADO "$1" "esperado $3, obtido $(n mv_b "$2")"; }
c "MV-B3 nenhum EM_ATENDIMENTO restante em ordem_servico" "SELECT COUNT(*) FROM ordem_servico WHERE status='EM_ATENDIMENTO'" 0
c "MV-B4 OS EM_ATENDIMENTO convertida para EM_MANUTENCAO" "SELECT COUNT(*) FROM ordem_servico WHERE status='EM_MANUTENCAO'" 1
c "MV-B5 nenhum EM_ATENDIMENTO restante no histórico" "SELECT COUNT(*) FROM timeline_os WHERE status_anterior='EM_ATENDIMENTO' OR status_novo='EM_ATENDIMENTO'" 0
c "MV-B6 histórico convertido para EM_MANUTENCAO (3 linhas)" "SELECT COUNT(*) FROM timeline_os WHERE status_anterior='EM_MANUTENCAO' OR status_novo='EM_MANUTENCAO'" 3
c "MV-B7 nenhuma linha de histórico perdida" "SELECT COUNT(*) FROM timeline_os" 8
c "MV-B8 linhas sem status anterior viram tipo ABERTURA" "SELECT COUNT(*) FROM timeline_os WHERE tipo='ABERTURA'" 4
c "MV-B9 demais linhas recebem tipo STATUS" "SELECT COUNT(*) FROM timeline_os WHERE tipo='STATUS'" 4
c "MV-B10 histórico existente fica visível ao cliente" "SELECT COUNT(*) FROM timeline_os WHERE visivel_cliente=1" 8
c "MV-B11 valor da OS concluída preservado (190,00)" "SELECT COUNT(*) FROM ordem_servico WHERE valor=190.00" 1

echo; echo "=== MV-C  Restrições introduzidas pela V2 ==="
OSID=$(n mv_b "SELECT id FROM ordem_servico WHERE protocolo='DITEC-2026-000101'")
for s in EM_DIAGNOSTICO AGUARDANDO_APROVACAO AGUARDANDO_PECAS EM_MANUTENCAO EM_TESTES AGUARDANDO_RETIRADA ENTREGUE; do
  t "MV-C status '$s' aceito" ok "UPDATE ordem_servico SET status='$s' WHERE id=$OSID" mv_b; done
t "MV-C status antigo 'EM_ATENDIMENTO' rejeitado" erro "UPDATE ordem_servico SET status='EM_ATENDIMENTO' WHERE id=$OSID" mv_b
t "MV-C status inexistente rejeitado" erro "UPDATE ordem_servico SET status='XPTO' WHERE id=$OSID" mv_b
t "MV-C valor_orcamento negativo rejeitado" erro "UPDATE ordem_servico SET valor_orcamento=-1 WHERE id=$OSID" mv_b
t "MV-C valor negativo rejeitado" erro "UPDATE ordem_servico SET valor=-1 WHERE id=$OSID" mv_b
t "MV-C valor_orcamento = 0 aceito" ok "UPDATE ordem_servico SET valor_orcamento=0 WHERE id=$OSID" mv_b
t "MV-C tipo de evento inválido rejeitado" erro "INSERT INTO timeline_os (ordem_servico_id,tipo,status_novo,alterado_por,data_hora) VALUES ($OSID,'INVALIDO','AGENDADO','x',NOW())" mv_b
for tp in DIAGNOSTICO ORCAMENTO OBSERVACAO ATRIBUICAO PREVISAO; do
  t "MV-C tipo de evento '$tp' aceito" ok "INSERT INTO timeline_os (ordem_servico_id,tipo,status_novo,alterado_por,data_hora) VALUES ($OSID,'$tp','AGENDADO','x',NOW())" mv_b; done
t "MV-C usuario_id inexistente na timeline (FK) rejeitado" erro "INSERT INTO timeline_os (ordem_servico_id,usuario_id,status_novo,alterado_por,data_hora) VALUES ($OSID,999999,'AGENDADO','x',NOW())" mv_b
UT=$(n mv_b "SELECT id FROM usuario WHERE email='tec.upg@teste.com'")
$S mv_b -e "INSERT INTO timeline_os (ordem_servico_id,tipo,usuario_id,status_novo,alterado_por,data_hora) VALUES ($OSID,'OBSERVACAO',$UT,'AGENDADO','resp',NOW()); DELETE FROM tecnico WHERE usuario_id=$UT; DELETE FROM usuario WHERE id=$UT;" >/dev/null 2>&1
[ "$(n mv_b "SELECT COUNT(*) FROM timeline_os WHERE alterado_por='resp' AND usuario_id IS NULL")" = 1 ] \
  && res APROVADO "MV-C excluir o responsável: evento permanece e usuario_id vira NULL (ON DELETE SET NULL)" \
  || res REPROVADO "MV-C ON DELETE SET NULL em timeline_os.usuario_id"
$S -e "DROP DATABASE mv_b"

echo; echo "=== MV-D  Caso-limite: OS com valor negativo já existente (a V1 não tinha CHECK em valor) ==="
novo_banco mv_d
$S mv_d -e "INSERT INTO agendamento (protocolo,nome_contato,data_hora,tipo_aparelho,bairro,endereco,status,criado_em,atualizado_em) VALUES ('DITEC-2026-000201','N','2026-09-01 08:00:00','Fogao','Moema','R','CONCLUIDO',NOW(),NOW()); INSERT INTO ordem_servico (agendamento_id,protocolo,status,valor,criado_em,atualizado_em) SELECT id,protocolo,'CONCLUIDO',-50.00,NOW(),NOW() FROM agendamento;"
$S mv_d < "$TMP/V2_mariadb.sql" > "$TMP/d.out" 2>&1
grep -q "ck_os_valor" "$TMP/d.out" && res APROVADO "MV-D1 a V2 aborta ao encontrar OS com valor < 0" "$(grep -m1 ERROR "$TMP/d.out" | cut -c1-95)"
res APROVADO "MV-D2 estado parcial após a falha (DDL não transacional)" \
  "coluna 'diagnostico' criada: $(n mv_d "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='mv_d' AND TABLE_NAME='ordem_servico' AND COLUMN_NAME='diagnostico'"); ck_os_status presente: $(n mv_d "SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='mv_d' AND CONSTRAINT_NAME='ck_os_status'")"
res APROVADO "MV-D3 consulta de pré-checagem identifica o problema antes da migração" "OS com valor < 0: $(n mv_d 'SELECT COUNT(*) FROM ordem_servico WHERE valor < 0')"
$S -e "DROP DATABASE mv_d"

echo; echo "=== MV-E  Roteiro de reversão do README (aplicado a partir do próprio README) ==="
python3 - "$README_BD" "$TMP" <<'EOF'
import sys
t = open(sys.argv[1], encoding="utf-8").read()
bloco = t.split("Reversao manual, se necessario")[1].split("```sql")[1].split("```")[0]
bloco = bloco.replace("DROP CHECK", "DROP CONSTRAINT")
bloco = "\n".join(l for l in bloco.splitlines() if "flyway_schema_history" not in l)
open(sys.argv[2] + "/reversao.sql", "w").write(bloco + "\n")
open(sys.argv[2] + "/reversao_corrigida.sql", "w").write(bloco + "\nDROP INDEX idx_os_criado_em ON ordem_servico;\nDROP INDEX idx_timeline_os_data ON timeline_os;\n")
EOF
# E1: roteiro como está
novo_banco mv_e; $S mv_e < "$TMP/seed.sql"; $S mv_e < "$TMP/V2_mariadb.sql"
$S mv_e < "$TMP/reversao.sql" > "$TMP/e1.out" 2>&1 && res APROVADO "MV-E1 reversão do README executa sem erro" || res REPROVADO "MV-E1 reversão do README falhou" "$(grep -m1 ERROR "$TMP/e1.out")"
c2() { [ "$(n mv_e "$2")" = "$3" ] && res APROVADO "$1" "obtido: $3" || res REPROVADO "$1" "esperado $3, obtido $(n mv_e "$2")"; }
c2 "MV-E2 status voltam ao domínio da V1 (nenhum EM_MANUTENCAO)" "SELECT COUNT(*) FROM ordem_servico WHERE status NOT IN ('AGENDADO','EM_ATENDIMENTO','CONCLUIDO','CANCELADO')" 0
c2 "MV-E3 índices criados pela V2 permanecem após a reversão (2)" "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='mv_e' AND INDEX_NAME IN ('idx_os_criado_em','idx_timeline_os_data')" 2
$S mv_e < "$TMP/V2_mariadb.sql" > "$TMP/e4.out" 2>&1
grep -q "Duplicate key name" "$TMP/e4.out" && res APROVADO "MV-E4 DEFEITO CONFIRMADO: reaplicar a V2 após a reversão do README falha" "$(grep -m1 ERROR "$TMP/e4.out" | cut -c1-80)" \
                                           || res REPROVADO "MV-E4 reaplicação após reversão não falhou (defeito não reproduzido)"
$S -e "DROP DATABASE mv_e"
# E5: reversão corrigida (com os dois DROP INDEX), em banco limpo
novo_banco mv_f; $S mv_f < "$TMP/seed.sql"; $S mv_f < "$TMP/V2_mariadb.sql"
$S mv_f < "$TMP/reversao_corrigida.sql" > "$TMP/f1.out" 2>&1 && res APROVADO "MV-E5 reversão corrigida (com DROP INDEX) executa sem erro" || res REPROVADO "MV-E5 reversão corrigida falhou" "$(grep -m1 ERROR "$TMP/f1.out")"
$S mv_f < "$TMP/V2_mariadb.sql" > "$TMP/f2.out" 2>&1 && res APROVADO "MV-E6 V2 reaplicada com sucesso após a reversão corrigida" || res REPROVADO "MV-E6 reaplicação falhou" "$(grep -m1 ERROR "$TMP/f2.out")"
[ "$(n mv_f "SELECT COUNT(*) FROM ordem_servico WHERE status='EM_ATENDIMENTO'")" = 0 ] && res APROVADO "MV-E7 estado final consistente (nenhum EM_ATENDIMENTO)" || res REPROVADO "MV-E7 estado final inconsistente"
$S -e "DROP DATABASE mv_f"

rm -rf "$TMP"
echo; echo "Total: $((ok+falha)) verificações | aprovadas: $ok | reprovadas: $falha"
