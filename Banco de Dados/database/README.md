# Banco de dados — DITEC Assistencia Tecnica

Este diretorio existe para facilitar a inspecao do schema fora do backend.
Voce **nao precisa rodar `schema.sql` manualmente** — ao iniciar o backend
Spring Boot, o Flyway aplica automaticamente as migracoes de `Prototipos/backend/src/main/resources/db/migration/`
(`V1__schema.sql` e `V2__rastreamento_os.sql`; `schema.sql` e `V2__rastreamento_os.sql` desta pasta sao copias so' para leitura).

## Passo a passo

1. Instale e rode o MySQL 8 localmente.
2. Crie o banco e o usuario:
   ```bash
   mysql -u root -p < database/00-criar-banco-e-usuario.sql
   ```
3. Configure `backend/.env` (copie de `backend/.env.example`) com as
   credenciais que voce definiu no passo 2.
4. Rode o backend (`mvn spring-boot:run` dentro de `backend/`) — na
   primeira execucao, o Flyway cria todas as tabelas e o `DataSeeder`
   popula os dados de teste (admin/tecnico/cliente/OS de exemplo).

## Arquivos

- `00-criar-banco-e-usuario.sql` — cria o database e o usuario de acesso.
- `schema.sql` — copia de leitura do schema (as tabelas reais sao criadas
  pelo Flyway, nao por este arquivo).

## V2 — rastreamento completo da OS (09/11/2026)

`V2__rastreamento_os.sql` e' incremental (nao apaga dados):

- `ordem_servico`: novas colunas `diagnostico`, `valor_orcamento`, `orcamento_aprovado`, `previsao_conclusao`,
  `data_entrega`; dominio de `status` ampliado para 10 valores (AGENDADO, EM_DIAGNOSTICO, AGUARDANDO_APROVACAO,
  AGUARDANDO_PECAS, EM_MANUTENCAO, EM_TESTES, CONCLUIDO, AGUARDANDO_RETIRADA, ENTREGUE, CANCELADO); registros
  antigos `EM_ATENDIMENTO` viram `EM_MANUTENCAO`; CHECKs de valores nao negativos; indice em `criado_em`.
- `timeline_os`: novas colunas `tipo` (ABERTURA, STATUS, DIAGNOSTICO, ORCAMENTO, OBSERVACAO, ATRIBUICAO, PREVISAO),
  `usuario_id` (FK para `usuario`, responsavel pela alteracao) e `visivel_cliente`; indice (`ordem_servico_id`, `data_hora`).

Reversao manual, se necessario (perde os dados das colunas novas):

```sql
ALTER TABLE timeline_os DROP FOREIGN KEY fk_timeline_usuario, DROP CHECK ck_timeline_tipo,
  DROP COLUMN tipo, DROP COLUMN usuario_id, DROP COLUMN visivel_cliente;
ALTER TABLE ordem_servico DROP CHECK ck_os_status, DROP CHECK ck_os_valor_orcamento, DROP CHECK ck_os_valor;
UPDATE ordem_servico SET status='EM_ATENDIMENTO' WHERE status IN ('EM_DIAGNOSTICO','AGUARDANDO_APROVACAO','AGUARDANDO_PECAS','EM_MANUTENCAO','EM_TESTES');
UPDATE ordem_servico SET status='CONCLUIDO' WHERE status IN ('AGUARDANDO_RETIRADA','ENTREGUE');
ALTER TABLE ordem_servico ADD CONSTRAINT ck_os_status CHECK (status IN ('AGENDADO','EM_ATENDIMENTO','CONCLUIDO','CANCELADO')),
  DROP COLUMN diagnostico, DROP COLUMN valor_orcamento, DROP COLUMN orcamento_aprovado,
  DROP COLUMN previsao_conclusao, DROP COLUMN data_entrega;
DELETE FROM flyway_schema_history WHERE version = '2';
```
