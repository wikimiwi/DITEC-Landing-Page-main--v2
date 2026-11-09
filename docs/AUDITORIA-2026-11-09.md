# Auditoria técnica, correções e testes — DITEC (09/11/2026)

Trabalho executado localmente (macOS, MySQL 9.7.1, JDK Temurin 21.0.13, Maven 3.9.9, Chromium do app).
Ponto de partida: código do repositório + `correcoes-projeto.patch` da auditoria anterior (aplicado).
Nenhum commit ou push foi feito. Backup do projeto original guardado antes de qualquer alteração.

## 1. Diagnóstico inicial

| Área | Situação encontrada |
|---|---|
| Compilação | **Quebrada.** `SecurityConfig` não compilava (`permissionsPolicy(...)` no Spring Security 6.3 retorna outro tipo e quebra o encadeamento). O patch anterior removia o `.and()`, mas continuava sem compilar. |
| Testes | `mvn test` executava **1** teste: o Surefire não inclui classes `*IT`, então os 13 testes de integração nunca rodaram. Ao incluí-los, **2 falhavam**. |
| Rastreamento de OS | **Quebrado em execução:** HTTP 500 (`LazyInitializationException`; serviços de leitura sem transação com `open-in-view=false`). Erro engolido sem log pelo handler genérico. |
| Ciclo da OS | Só 4 status (AGENDADO, EM_ATENDIMENTO, CONCLUIDO, CANCELADO); sem diagnóstico, orçamento, previsão, entrega, observações nem responsável (FK) no histórico. `CONCLUIDO` alcançável sem valor/descrição. |
| Rastreamento público | Só pelo protocolo sequencial (enumerável). |
| Perfil do cliente | Alterar nome/telefone **não persistia** (entidade `Usuario` desanexada não era salva). |
| Cadastro | Vinculava o agendamento do visitante, mas **não a OS** — o cliente não via a própria OS. Vínculo por telefone sem limite de tempo. |
| Segurança | Rate limit burlável trocando `X-Forwarded-For`; token de usuário desativado continuava válido; requisições sem login retornavam 403 (front não limpava sessão expirada); prompt de sistema e modelo do chatbot vinham do navegador; CORS com `allowCredentials=true` sem uso de cookies. |
| Chatbot | Sem `HF_API_KEY` respondia sempre 502 (sem atendimento 24h). |
| Front-end | Logos das marcas quebrados (`../assetslogos` fora da pasta publicada); rolagem horizontal no celular; senha "mínimo 6" no front × 8 no back; diretiva `frame-ancestors` em `<meta>` gerando erro no console. |
| `.env` | Exemplo com `&` sem aspas quebra `source` no shell. |

Funcionava (verificado): login/JWT, cadastro, agendamento com protocolo, conflito de horário (409), bairro fora da área (422), CRUD de técnicos/serviços, relatórios CSV, migração V1 no MySQL.

## 2. Banco de dados

- Banco usado: `ditec_assistencia` (criado nesta auditoria — não existia; nenhum outro banco do MySQL foi tocado). Usuário `ditec_user` com senha aleatória local guardada só em `Prototipos/backend/.env` (ignorado pelo Git).
- **Migração nova `V2__rastreamento_os.sql`** (incremental, sem `DROP TABLE`/`TRUNCATE`; reversão documentada em `Banco de Dados/database/README.md`): colunas de diagnóstico, orçamento, aprovação, previsão e entrega; 10 status; CHECKs de valores não negativos; histórico com `tipo`, `usuario_id` (FK) e `visivel_cliente`; índices novos.
- Verificação final no MySQL: 2 migrações com sucesso, 9 tabelas de negócio, **13 FKs, 11 CHECKs**; consultas de integridade com **0 ocorrências** para: OS sem agendamento, agendamento sem OS, protocolo divergente, OS sem evento de abertura, status atual ≠ último status do histórico, status OS × agendamento inconsistente, OS concluída sem valor/descrição/data, técnico OS × agendamento divergente.
- Entidades JPA × tabelas: conferidas na prática (todas as rotas exercitadas contra o MySQL sem erro de mapeamento).

## 3. Rastreamento de OS (implementado)

- **Ciclo de vida** (`StatusOS`): AGENDADO (aguardando avaliação) → EM_DIAGNOSTICO → [AGUARDANDO_APROVACAO] → [AGUARDANDO_PECAS] → EM_MANUTENCAO → EM_TESTES → CONCLUIDO → [AGUARDANDO_RETIRADA] → ENTREGUE; CANCELADO. Matriz explícita de transições; ENTREGUE/CANCELADO finais.
- **Regras:** atendimento só inicia com agendamento confirmado; sair de diagnóstico exige diagnóstico; aguardar aprovação exige orçamento; conclusão só por `/finalizar` (valor, pagamento, descrição obrigatória; 5% Pix/dinheiro; garantia 90 dias e data-limite calculada); cancelamento só pelo admin com motivo (ou cliente: cancelamento do agendamento / recusa do orçamento, sem custo); técnico só atua em OS própria ou sem técnico (assume); técnico inativo não recebe OS.
- **Histórico:** todo evento grava status anterior/novo, data e hora, nome e FK do responsável, observação e visibilidade. Nunca é apagado.
- **Consulta pública:** `POST /api/ordens-servico/rastreio` com protocolo + telefone do agendamento (mesmo 404 para "não existe" e "telefone não confere"); rate limit 20/5 min. O antigo `GET /protocolo/{p}` foi removido.
- **Telas:** rastreamento com indicador de 6 etapas, alerta de orçamento, dados organizados e linha do tempo real (index); "Minhas Ordens de Serviço" com aprovar/recusar orçamento (conta); painel de ações compartilhado `os-equipe.js` (técnico e admin) com transições válidas, diagnóstico/orçamento, previsão, observação interna/pública, finalização, atribuição e cancelamento; filtros por protocolo, cliente, status e técnico no admin; aparelhos a gás no topo.

## 4. Outras correções de back-end e segurança

`SecurityConfig` (compila; `Permissions-Policy` via header estático; 401 JSON para não autenticado; CORS sem credenciais) · `@Transactional(readOnly)` nos serviços de leitura · log de erros inesperados (sem expor ao cliente) · `ClienteService` persiste nome/telefone · `AuthService` vincula agendamento **e OS** (só últimas 24h) · `JwtAuthenticationFilter` barra conta inativa · `RateLimitFilter` só confia em `X-Forwarded-For` com `DITEC_TRUST_PROXY=true` · `ChatService` com prompt/modelo no servidor, descarta mensagens `system` do cliente, histórico limitado · `ChatFallbackService` (respostas automáticas identificadas como tal) · dashboard conta no banco em vez de carregar tudo em memória · CSV de OS com novas colunas · `pom.xml` executa os `*IT` · `run-local.sh` e `.env.example` com aspas.

## 5. Testes executados (resultados reais)

**Automatizados — `mvn test`: 29 executados, 29 aprovados, 0 falhas** (AgendamentoFlowIT 7, AuthFlowIT 3, OrdemServicoFlowIT 10, RateLimitIT 2, StatusOSTest 4, RegrasAuxiliaresTest 2, DitecApplicationTests 1).

**API real (curl → Spring Boot → MySQL), 1ª execução com dados limpos: 52/55; após a correção do 401: os 3 casos restantes aprovados** (a 2ª/3ª execução mostra 5 "falhas" que são apenas de estado — o roteiro supõe o seed intacto: OS já aprovada/avaliada e uma OS a mais da cliente).

| Teste | Esperado | Obtido | Status |
|---|---|---|---|
| Back-end compila e inicia no MySQL; Flyway V1+V2 | Sobe sem erro | Iniciou em ~2,3 s; 2 migrações aplicadas | ✅ |
| Front-end servido e consumindo a API | Páginas carregam | 200; chamadas à API OK no navegador | ✅ |
| Login admin/técnico/cliente | Token JWT | OK | ✅ |
| Sem token / token inválido | 401 | 401 (antes: 403) | ✅ |
| Cliente em rota de admin; técnico em rota de admin | 403 | 403 | ✅ |
| Criar agendamento (visitante) via API e via formulário do site | 201 + protocolo + OS + histórico | DITEC-2026-000004/000006 gravados no MySQL com 1 evento | ✅ |
| Prioridade gás detectada | `prioridadeGas=true` | true; listas ordenam gás primeiro | ✅ |
| Mesmo horário | 409 | 409 | ✅ |
| Rastrear OS existente (entregue / em andamento / cancelada) | 200 com status e histórico reais | 200; ENTREGUE com 9 eventos; CANCELADO | ✅ |
| Rastrear OS inexistente / telefone errado | 404 | 404 / 404 | ✅ |
| Protocolo malformado | 422 | 422 | ✅ |
| Rastreio sem dados sensíveis / sem notas internas | Ausentes | Ausentes | ✅ |
| Endpoint antigo enumerável | Indisponível | 401 | ✅ |
| Iniciar com agendamento pendente | 422 | 422 | ✅ |
| Pular etapa / concluir via /status | 422 | 422 / 422 | ✅ |
| Avançar sem diagnóstico; aprovação sem orçamento | 422 | 422 / 422 | ✅ |
| Diagnóstico + orçamento + previsão | 200, persistido | 200 | ✅ |
| Técnico cancelar / admin cancelar sem motivo | 403 / 422 | 403 / 422 | ✅ |
| Outro cliente aprova orçamento / vê OS alheia | 403 | 403 / 403 | ✅ |
| Cliente aprova (tela e API) / recusa orçamento | EM_MANUTENCAO / CANCELADO | Idem | ✅ |
| Finalizar com Pix (200 → 190; 260 → 247 em dinheiro) | 5% desconto, garantia 90 d | Idem; garantia até 07/02/2027 | ✅ |
| OS entregue não muda mais | 422 | 422 | ✅ |
| Histórico persistido e coerente (MySQL) | Todas as mudanças registradas | 0 inconsistências nas consultas de integridade | ✅ |
| Recarregar a página após alterações | Dados permanecem | Permanecem (lidos do banco) | ✅ |
| Filtros do admin (protocolo, status, cliente, técnico) | Resultado filtrado | OK na API e na tela | ✅ |
| Atribuir técnico ativo / inativo | 200 / 422 | 200 / 422 | ✅ |
| Previsão no passado | 422 | 422 | ✅ |
| Avaliação 1ª / 2ª vez | 201 / 409 | 201 / 409 | ✅ |
| Atualizar perfil do cliente | Persistido | 200 (nome/telefone agora salvos) | ✅ |
| Rate limit login (11ª tentativa) e com `X-Forwarded-For` forjado | 429 | 429 / 429 | ✅ |
| Chatbot sem `HF_API_KEY` | Resposta útil identificada como automática | 200, < 1 s | ✅ |
| Verificador de área (CEP 04077-000) | Atendido | "Atendemos a sua região." | ✅ |
| Responsividade 375 px (home, rastreio, conta, técnico, admin) | Sem rolagem horizontal | Corrigido (era 394–395 px) | ✅ |
| Logos das marcas | Carregam | 8/8 carregam | ✅ |

## 6. Pendências reais

1. **Chatbot com IA real:** requer `HF_API_KEY` do grupo (não disponível no ambiente); a integração existe e o modo automático cobre a indisponibilidade. A meta de < 2 s com IA não pôde ser medida.
2. **NF-e:** não há integração com emissor autorizado; o sistema só registra o número informado. Integração depende de contrato com provedor (SEFAZ/emissor) — deve constar como funcionalidade pendente.
3. **Lighthouse / WCAG 2.1 AA:** não executados (ferramenta não instalada; não instalei pacotes globais). Recomenda-se rodar o Lighthouse do Chrome DevTools nas 4 páginas.
4. **Produção:** configurar no serviço de hospedagem `DITEC_SEED_ENABLED=false`, `DITEC_ALLOWED_ORIGINS`, `DITEC_TRUST_PROXY=true` (se atrás de proxy), segredos e banco; disponibilidade de 99,5% só é verificável em produção.
5. **Flyway × MySQL 9.7:** funciona, mas o Flyway 10 do Spring Boot 3.3.5 avisa que só testou até MySQL 8.1; em produção prefira MySQL 8.x ou atualize o Spring Boot.
6. Decisões do grupo: `index.html.bak` ainda versionado; versão do `pom.xml` (5.0.0); link de "Avaliar atendimento" aparece mesmo para OS já avaliada (a API responde 409 com mensagem clara).

## 7. Como reproduzir

Ver "Como Executar o Projeto" no `README.md` da raiz (banco → `.env` → `./run-local.sh` → `python3 -m http.server 5500` → `mvn test`).
