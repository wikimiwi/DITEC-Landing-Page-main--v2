#!/bin/bash
# Testes de integridade do banco DITEC (schema V1__schema.sql).
# Executados em MariaDB 10.11 (NAO e MySQL 8). Registros referenciados por chave natural (e-mail/protocolo),
# nunca por id fixo, porque o AUTO_INCREMENT avanca mesmo em INSERTs rejeitados.
DB=ditec_teste
q() { mariadb -uroot -N "$DB" -e "$1" 2>&1; }
pass=0; fail=0
OS1="(SELECT id FROM ordem_servico WHERE protocolo='DITEC-2026-000001')"
OS2="(SELECT id FROM ordem_servico WHERE protocolo='DITEC-2026-000010')"
CLI="(SELECT c.id FROM cliente c JOIN usuario u ON u.id=c.usuario_id WHERE u.email='cli@teste.com')"
TEC="(SELECT t.id FROM tecnico t WHERE t.email='tec@teste.com')"
AG1="(SELECT id FROM agendamento WHERE protocolo='DITEC-2026-000001')"
errline() { echo "$1" | grep -m1 "^ERROR" | sed -E 's/ at line [0-9]+//' | cut -c1-110; }
ok()  { local id="$1" desc="$2" sql="$3"; out=$(q "$sql"); if echo "$out" | grep -q "^ERROR"; then echo "REPROVADO $id  $desc :: $(errline "$out")"; fail=$((fail+1)); else echo "APROVADO  $id  $desc"; pass=$((pass+1)); fi; }
err() { local id="$1" desc="$2" sql="$3"; out=$(q "$sql"); if echo "$out" | grep -q "^ERROR"; then echo "APROVADO  $id  $desc [rejeitado: $(errline "$out")]"; pass=$((pass+1)); else echo "REPROVADO $id  $desc :: deveria ter sido rejeitado"; fail=$((fail+1)); fi; }
eq()  { local id="$1" desc="$2" sql="$3" want="$4"; out=$(q "$sql" | tr -d '\n'); if [ "$out" = "$want" ]; then echo "APROVADO  $id  $desc [obtido: $out]"; pass=$((pass+1)); else echo "REPROVADO $id  $desc :: esperado=$want obtido=$out"; fail=$((fail+1)); fi; }
q "SET FOREIGN_KEY_CHECKS=0; TRUNCATE timeline_os; TRUNCATE avaliacao; TRUNCATE chat_log; TRUNCATE ordem_servico; TRUNCATE agendamento; TRUNCATE servico; TRUNCATE tecnico; TRUNCATE cliente; TRUNCATE usuario; SET FOREIGN_KEY_CHECKS=1;" >/dev/null
ok  BD-01 "inserir usuário CLIENTE válido" "INSERT INTO usuario(nome,email,senha_hash,tipo) VALUES('Cli Teste','cli@teste.com','x','CLIENTE');"
ok  BD-02 "inserir usuário TECNICO válido" "INSERT INTO usuario(nome,email,senha_hash,tipo) VALUES('Tec Teste','tec@teste.com','x','TECNICO');"
err BD-03 "e-mail duplicado em usuario (UNIQUE)" "INSERT INTO usuario(nome,email,senha_hash,tipo) VALUES('Outro','cli@teste.com','x','CLIENTE');"
err BD-04 "tipo de usuário inválido (CHECK)" "INSERT INTO usuario(nome,email,senha_hash,tipo) VALUES('Z','z@teste.com','x','VISITANTE');"
ok  BD-05 "inserir cliente vinculado ao usuário" "INSERT INTO cliente(usuario_id,bairro) SELECT id,'Moema' FROM usuario WHERE email='cli@teste.com';"
err BD-06 "cliente com usuario_id inexistente (FK)" "INSERT INTO cliente(usuario_id) VALUES(999999);"
err BD-07 "segundo cliente para o mesmo usuário (UNIQUE)" "INSERT INTO cliente(usuario_id) SELECT id FROM usuario WHERE email='cli@teste.com';"
ok  BD-08 "inserir técnico vinculado ao usuário" "INSERT INTO tecnico(usuario_id,nome,email) SELECT id,'Tec Teste','tec@teste.com' FROM usuario WHERE email='tec@teste.com';"
ok  BD-09 "agendamento de visitante (cliente_id nulo)" "INSERT INTO agendamento(protocolo,nome_contato,telefone_contato,data_hora,tipo_aparelho,bairro,endereco) VALUES('DITEC-2026-000001','Visitante','11999990000','2026-11-10 10:00:00','Geladeira','Moema','Rua A, 1');"
err BD-10 "protocolo de agendamento duplicado (UNIQUE)" "INSERT INTO agendamento(protocolo,data_hora,tipo_aparelho,bairro,endereco) VALUES('DITEC-2026-000001','2026-11-10 11:30:00','Fogao','Moema','Rua B, 2');"
err BD-11 "status de agendamento inválido (CHECK)" "INSERT INTO agendamento(protocolo,data_hora,tipo_aparelho,bairro,endereco,status) VALUES('DITEC-2026-000002','2026-11-10 13:00:00','Fogao','Moema','Rua B, 2','ABERTO');"
err BD-12 "agendamento sem bairro (NOT NULL)" "INSERT INTO agendamento(protocolo,data_hora,tipo_aparelho,endereco) VALUES('DITEC-2026-000003','2026-11-10 14:30:00','Fogao','Rua B, 2');"
err BD-13 "OS para agendamento inexistente (FK)" "INSERT INTO ordem_servico(agendamento_id,protocolo) VALUES(999999,'DITEC-2026-000099');"
ok  BD-14 "OS vinculada ao agendamento 1" "INSERT INTO ordem_servico(agendamento_id,cliente_id,tecnico_id,protocolo) VALUES($AG1,$CLI,$TEC,'DITEC-2026-000001');"
err BD-15 "segunda OS para o mesmo agendamento (UNIQUE 1:1)" "INSERT INTO ordem_servico(agendamento_id,protocolo) VALUES($AG1,'DITEC-2026-000777');"
err BD-16 "status de OS inválido (CHECK)" "UPDATE ordem_servico SET status='PAUSADA' WHERE protocolo='DITEC-2026-000001';"
err BD-17 "garantia_dias negativa (CHECK)" "UPDATE ordem_servico SET garantia_dias=-1 WHERE protocolo='DITEC-2026-000001';"
eq  BD-18 "garantia padrão = 90 dias" "SELECT garantia_dias FROM ordem_servico WHERE protocolo='DITEC-2026-000001';" "90"
eq  BD-19 "status padrão da OS = AGENDADO" "SELECT status FROM ordem_servico WHERE protocolo='DITEC-2026-000001';" "AGENDADO"
ok  BD-20 "avaliação nota 5" "INSERT INTO avaliacao(ordem_servico_id,cliente_id,nota,comentario) VALUES($OS1,$CLI,5,'Otimo');"
err BD-21 "segunda avaliação para a mesma OS (UNIQUE)" "INSERT INTO avaliacao(ordem_servico_id,nota) VALUES($OS1,4);"
ok  BD-22 "criar OS 2 para testar notas inválidas" "INSERT INTO agendamento(protocolo,data_hora,tipo_aparelho,bairro,endereco) VALUES('DITEC-2026-000010','2026-11-11 10:00:00','Micro-ondas','Moema','Rua C, 3'); INSERT INTO ordem_servico(agendamento_id,protocolo) SELECT id,'DITEC-2026-000010' FROM agendamento WHERE protocolo='DITEC-2026-000010';"
err BD-23 "nota 6 (acima do limite, CHECK)" "INSERT INTO avaliacao(ordem_servico_id,nota) VALUES($OS2,6);"
err BD-24 "nota 0 (abaixo do limite, CHECK)" "INSERT INTO avaliacao(ordem_servico_id,nota) VALUES($OS2,0);"
ok  BD-25 "registro de timeline da OS" "INSERT INTO timeline_os(ordem_servico_id,status_anterior,status_novo,alterado_por) VALUES($OS1,'AGENDADO','EM_ATENDIMENTO','Tec Teste');"
err BD-26 "timeline para OS inexistente (FK)" "INSERT INTO timeline_os(ordem_servico_id,status_novo,alterado_por) VALUES(999999,'CONCLUIDO','x');"
ok  BD-27 "registro de chat_log" "INSERT INTO chat_log(cliente_id,sessao_id,pergunta,resposta) VALUES($CLI,'sess-1','Oi','Ola');"
ok  BD-28 "excluir técnico (ON DELETE SET NULL)" "DELETE FROM tecnico WHERE email='tec@teste.com';"
eq  BD-29 "OS permanece com tecnico_id nulo após excluir técnico" "SELECT IFNULL(tecnico_id,'NULO') FROM ordem_servico WHERE protocolo='DITEC-2026-000001';" "NULO"
ok  BD-30 "excluir OS 1 (CASCADE em timeline e avaliação)" "DELETE FROM ordem_servico WHERE protocolo='DITEC-2026-000001';"
eq  BD-31 "timeline removida em cascata" "SELECT COUNT(*) FROM timeline_os;" "0"
eq  BD-32 "avaliação da OS 1 removida em cascata" "SELECT COUNT(*) FROM avaliacao;" "0"
ok  BD-33 "excluir usuário cliente (CASCADE no cliente)" "DELETE FROM usuario WHERE email='cli@teste.com';"
eq  BD-34 "cliente removido em cascata" "SELECT COUNT(*) FROM cliente;" "0"
eq  BD-35 "chat_log mantido com cliente_id nulo (SET NULL)" "SELECT IFNULL(cliente_id,'NULO') FROM chat_log WHERE sessao_id='sess-1';" "NULO"
echo; echo "Total: $((pass+fail)) testes | aprovados: $pass | reprovados: $fail"
