# Banco de dados — DITEC Assistencia Tecnica

Este diretorio existe para facilitar a inspecao do schema fora do backend.
Voce **nao precisa rodar `schema.sql` manualmente** — ao iniciar o backend
Spring Boot, o Flyway aplica automaticamente `backend/src/main/resources/db/migration/V1__schema.sql`
(este arquivo aqui e' uma copia identica, so' para leitura/referencia).

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
