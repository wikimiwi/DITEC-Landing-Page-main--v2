# Relatório final de correções — DITEC (09/11/2026)

> **Atualização — migração do chatbot para a Google Gemini API.** A integração com a Hugging Face (seções 5 e 6 abaixo) foi
> **removida** e substituída pela Gemini API. Detalhes na seção "Migração para Google Gemini" ao final deste documento.

Continuação da auditoria de [`AUDITORIA-2026-11-09.md`](AUDITORIA-2026-11-09.md). Executado localmente (macOS, MySQL 9.7.1,
JDK 21.0.13, Maven 3.9.9, Node 24, Chromium 158 do Playwright). Nenhum commit, push ou publicação foi feito.
Somente resultados efetivamente medidos estão registrados aqui.

## 1. Resumo

| Item | Situação |
|---|---|
| Back-end, banco, front-end e integração | Funcionando e testados (30 testes automatizados + 17 passos de fluxo da OS + 22 testes de interface) |
| Rastreamento de OS | Funcional de ponta a ponta com dados reais do MySQL |
| Chatbot | **Google Gemini com IA real funcionando** (`gemini-3.5-flash-lite`), média 1,04 s, p95 1,43 s; Hugging Face removida |
| NF-e | Retirada do escopo (sem integração, sem promessas no site/chatbot) |
| Lighthouse | Celular: 98–100 desempenho, 100 acessibilidade, 100 boas práticas; desktop: 82–89 desempenho |
| WCAG 2.1 AA (automatizado) | axe-core: 0 violações em 22 cenários; teclado: 10/10 verificações |
| MySQL 9.7 × Flyway | Investigado; aviso é recomendação, sem erro real; limitação documentada |
| Produção | Perfil `prod`, health check, CORS estrito com falha rápida, `.env.example` e instruções prontos |

### Funcionalidades

| Funcionalidade | Situação inicial | Correção realizada | Resultado final |
|---|---|---|---|
| Rastreamento de OS | HTTP 500; só 4 status; enumerável | 10 status, histórico, protocolo + telefone (1ª etapa) | ✅ 17/17 no fluxo real + testes de interface |
| Agendamento | Funcionava; telefone sem validação; botão "novo agendamento" quebrado (CSP) | Validação de telefone; botão corrigido | ✅ testado pela API e pelo formulário |
| Painel administrativo | Sem filtros/histórico de OS | Filtros, painel de ações, atribuição e cancelamento (1ª etapa) | ✅ testado; 0 violações de acessibilidade |
| Painel do técnico | Só iniciar/finalizar | Diagnóstico, orçamento, previsão, observações (1ª etapa) | ✅ testado |
| Área do cliente | Perfil não salvava; não via a OS | Correções + aprovar/recusar orçamento (1ª etapa) | ✅ testado |
| Chatbot | Prompt no navegador; 502 sem chave; timeout 30 s | Prompt/modelo no servidor, reuso de conexão, timeouts, falhas tratadas (incl. 402), pausa automática, fonte indicada | ⚠️ funcional 24h; **IA real bloqueada por falta de créditos na conta HF** |
| Segurança (JWT, CORS, rate limit, XSS, SQL) | Lacunas (1ª etapa) | Corrigidas; CORS estrito em prod com falha rápida | ✅ testado |
| Acessibilidade | 48 violações axe; sem "pular conteúdo" | Correções de contraste, ARIA, teclado e títulos | ✅ 0 violações automatizadas; avaliação manual pendente |
| Desempenho (Lighthouse) | Celular 92–97 / desktop 71–80 | Fontes assíncronas | ✅ celular 98–100; ⚠️ desktop 82–89 |
| NF-e | Prometida no site, sem implementação | Retirada do escopo | ✅ |
| Banco / Flyway | Boot 3.3.5, Flyway 10, Connector 8.3 | Boot 3.5.16, Flyway 11.7.2, Connector 9.7.0 | ✅ migrações validadas; aviso do Flyway documentado |
| Produção | Sem perfil prod | `application-prod.yml`, health check, `.env.example`, `config.js` | ✅ testado localmente; não publicado |

## 2. Situação inicial (desta etapa)

- Chatbot sem `HF_API_KEY` (respondia só em modo automático); cliente HTTP sem reuso de conexão e timeout de 30 s.
- Site e chatbot afirmavam "nota fiscal emitida em todos os serviços".
- Lighthouse e axe nunca executados; Spring Boot 3.3.5 (fora do suporte), Flyway 10.10.0, MySQL Connector 8.3.0.
- `index.html.bak` versionado e publicado pelo deploy; sem perfil de produção.

## 3. Problemas encontrados e correções

| # | Problema | Correção |
|---|---|---|
| 1 | Botão **"Fazer novo agendamento"** não funcionava: `onclick` inline bloqueado pela própria CSP | Ação movida para `script.js`; testado no navegador |
| 2 | No celular, o **aviso de cookies cobria o botão do chatbot** (impossível abrir o chat) | Botões flutuantes sobem enquanto o aviso está visível |
| 3 | Telefone aceitava qualquer texto — visitante com telefone inválido nunca conseguiria rastrear a OS | Validação: 10 a 13 dígitos (agendamento, cadastro, perfil) + teste automatizado |
| 4 | Chatbot: timeout de 30 s, sem distinção de erros, sem reuso de conexão | `HttpClient` do JDK com HTTP/2 e keep-alive; timeouts configuráveis (5 s conexão / 15 s leitura); log por tipo de erro (401/403, 429, 404, 5xx, timeout) **sem nunca registrar a chave**; resposta indica a fonte (`ia` ou `automatica`) |
| 5 | Front do chat podia ficar esperando indefinidamente | Timeout de 25 s no navegador (`AbortController`), mensagens amigáveis para indisponibilidade e para limite de requisições |
| 6 | Calendário com `role="grid"` inválido; dias sem semântica de botão; Espaço não funcionava | Dias como botões acessíveis (`aria-disabled`, `aria-pressed`, Enter/Espaço) |
| 7 | Contraste insuficiente (texto cinza, botão WhatsApp 1,98:1, rodapé, Facebook, depoimentos) | Cores ajustadas para ≥ 4,5:1 mantendo a identidade (azul/verde DITEC) |
| 8 | Rótulos acessíveis divergentes do texto visível; badge do chat lido como "1" | Rótulos alinhados; badge decorativo; chat fecha com Esc e devolve o foco |
| 9 | Link "pular para o conteúdo" tinha CSS mas não existia no HTML; títulos h2→h4; `role="listitem"` em `<article>`/`<details>` | Link restaurado nas 4 páginas; hierarquia corrigida; papéis ARIA válidos |
| 10 | Fonte do Google bloqueando a renderização | Carregamento assíncrono (`carregar-fontes.js`, compatível com a CSP) + `preload` + `noscript` |
| 11 | Recompilar com o servidor ligado derrubava a API (`ClassNotFoundException`) | `run-local.sh` executa uma cópia do JAR |
| 12 | CORS caía em `localhost` se não configurado | Lista vazia = nenhuma origem; perfil `prod` **não sobe** sem `FRONTEND_URL`/`DITEC_ALLOWED_ORIGINS` |

## 4. NF-e fora do escopo

- Não existia biblioteca, endpoint ou tela de emissão fiscal — apenas o campo opcional `nota_fiscal_numero`.
- **Mantido** o campo (sem alteração no banco), agora rotulado "Nº do comprovante/nota (opcional)": é um registro manual, não emissão.
- Removidos: frase "Nota fiscal emitida em todos os serviços", FAQ "Emitem nota fiscal?", menção na garantia, afirmação no prompt do chatbot e no FAQ automático (agora orienta falar com a equipe). README declara a NF-e fora do escopo.

## 5. Configuração do chatbot

- Fluxo: navegador → `POST /api/chat` (só a conversa) → `ChatService` (prompt de sistema e modelo **no servidor**) → `https://router.huggingface.co/v1/chat/completions` com `Authorization: Bearer ${HF_API_KEY}`.
- Modelo padrão: `Qwen/Qwen2.5-72B-Instruct` (`HF_MODEL`); `max_tokens` 220; histórico limitado a 8 mensagens; mensagens `system` do cliente descartadas.
- A chave fica só em `Prototipos/backend/.env` (no `.gitignore`) ou nas variáveis da hospedagem. Varredura confirmou que nenhuma chave está no código.
- **Como ativar:** preencha `HF_API_KEY="..."` em `Prototipos/backend/.env` (token com "Make calls to Inference Providers") e reinicie com `./run-local.sh`.

### Testes do chatbot (executados)

| Teste | Resultado |
|---|---|
| Abrir/fechar, enviar, receber, campo liberado após resposta (desktop e 375 px) | ✅ |
| Mensagem vazia no site (não envia) / na API (422) / lista vazia (422) / 2.001 caracteres (422) | ✅ |
| Pergunta fora do escopo | ✅ resposta de orientação |
| Prompt de sistema e modelo enviados pelo cliente | ✅ ignorados (200, sem efeito) |
| **Chave inválida contra a API real da Hugging Face** | ✅ HF respondeu 401 → log "chave inválida", usuário recebe resposta automática em 0,38 s; chave ausente do log |
| **Timeout** (IA simulada travada, limite 2 s) | ✅ resposta automática em 2,15 s |
| **Hugging Face indisponível** (porta fechada) | ✅ resposta automática em 0,13 s |
| Limite de requisições (25 seguidas) | ✅ 429 a partir da 21ª na janela de 5 min; mensagem amigável no chat |
| **Chave real configurada** (`.env`, token fine-grained com `inference.serverless.write`) | ✅ aceita pela HF (não houve 401) |
| **Resposta da IA real** | ⛔ HF respondeu **402: "Purchase pre-paid credits… or subscribe to PRO"** (conta gratuita sem créditos; o mesmo para Llama-3.1-8B) → resposta automática ao usuário |
| Pausa automática após 402 (circuit breaker de 5 min) | ✅ 1 chamada à HF; as 14 seguintes respondidas em 4–6 ms sem consultar a HF; chave ausente do log |

## 6. Desempenho da IA

**Não medido com IA real**: a chave foi configurada e aceita, mas a conta da Hugging Face não tem créditos (HTTP 402), então nenhuma resposta veio do modelo. O script está pronto: `cd qa && node benchmark-chat.mjs 15` registra quantidade, média, mínimo,
máximo, p95, falhas, taxa de sucesso, separa a 1ª requisição das seguintes e conta respostas `ia` × `automatica`
(resultados em `qa/relatorios/benchmark-chat-*.json`). Medição feita no modo automático, **que não vale como medição da IA**:
benchmark de 15 requisições com a chave real: 15/15 sucesso (100%), 0 falhas, `respostasDaIA = 0`; 1ª requisição 411 ms (inclui a tentativa à HF que retornou 402), seguintes média 5 ms (mín. 4, máx. 6, p95 6 ms). Se o modelo de 72B não ficar abaixo de 2 s, as alternativas preparadas são
trocar `HF_MODEL` por um modelo menor (ex.: um de 7–8B) e/ou reduzir `HF_READ_TIMEOUT_MS`; o reuso de conexão já está ativo.

## 7. Lighthouse (12.8.2, Chromium 158)

Medido com servidor que imita a hospedagem (gzip/brotli + cache, `qa/servidor-producao.mjs`). Antes = primeira medição desta etapa.

| Página | Disp. | Desempenho antes → depois | Acessibilidade | Boas práticas | SEO |
|---|---|---|---|---|---|
| index (Sobre, Serviços, Agendamento, Rastreio, Contato) | celular | 92 → **100** | 86 → **100** | 93 → **100** | 100 |
| index | desktop | 71 → **89** | 86 → **100** | 93 → **100** | 100 |
| conta (login cliente) | celular / desktop | 95 → **98** / 76 → **82** | **100** | **100** | 50 → 60 |
| tecnico (login) | celular / desktop | 96 → **98** / 80 → **82** | 95 → **100** | **100** | 50 → 60 |
| admin (login) | celular / desktop | 97 → **99** / 75 → **86** | 95 → **100** | **100** | 50 → 60 |

- **SEO 60** nas áreas internas é intencional: `noindex` impede que painéis privados apareçam no Google (o Lighthouse penaliza isso).
- **Desktop < 90** em 4 páginas: o restante é o CSS único (~60 KB) bloqueando a primeira pintura na simulação de rede do desktop.
  Melhorar exigiria CSS crítico embutido/minificação (etapa de build), não feito para não alterar a estrutura do projeto.
- Com o servidor Python simples (sem compressão) os números de desktop são menores — em hospedagem real há compressão.

## 8. Acessibilidade (WCAG 2.1 AA)

- **axe-core 4.14.0 (via Playwright), regras WCAG 2.0/2.1 A e AA:** de 48 violações (críticas e sérias) para **0** em 22 cenários:
  home, rastreio com resultado e com erro, chatbot aberto, modal de login, login/área do cliente, login/painel do técnico com OS aberta,
  login/painel admin com OS aberta — em 1280 px e 375 px (`qa/relatorios/axe-resultado.json`).
- **Teclado (`qa/teclado.mjs`): 10/10** — um `h1`, sem saltos de título, "pular para o conteúdo" no 1º Tab, foco visível nos 25 primeiros
  elementos, calendário/horário com Enter, rastreio com Enter e anúncio por `aria-live`, chat abre com Enter e fecha com Esc devolvendo o foco.
- **Exige avaliação manual** (ferramentas não comprovam): uso real com NVDA/VoiceOver; ordem de leitura e clareza das mensagens; zoom 200%
  e texto ampliado; legendas/alternativas de conteúdo multimídia; tempo das animações; contraste de textos sobre gradientes/imagens;
  compreensão das mensagens de erro por usuários reais.

## 9. MySQL 9.7 e Flyway

| Item | Antes | Depois |
|---|---|---|
| Spring Boot | 3.3.5 (fora do suporte) | **3.5.16** (última da linha 3.x; mesma base Java 21/Jakarta) |
| Flyway | 10.10.0 | **11.7.2** (gerenciado pelo Boot 3.5) |
| MySQL Connector/J | 8.3.0 | **9.7.0** (mesma versão do servidor) |
| Hibernate / Spring Security | 6.5.3 / 6.3.4 | 6.6.53 / 6.5.11 |
| JJWT | 0.12.6 | 0.12.7 (correção da mesma linha) |

- Inspeção do bytecode do `MySQLDatabase.ensureSupported`: Flyway **10.10.0 e 11.7.2 declaram suporte até MySQL 8.1**; o **13.12.1** (mais recente) vai até **9.4**.
  **Nenhuma versão do Flyway declara suporte ao 9.7** — o aviso persiste com qualquer combinação; não foi suprimido nem ignorado.
- Não há erro real: com Flyway 11.7.2 o banco existente foi **validado** (2 migrações) e um banco temporário novo recebeu V1+V2 do zero (10 tabelas); o temporário foi apagado.
  Backup do banco antes do upgrade: `scratchpad/backup/ditec_assistencia-antes-upgrade.sql` (fora do repositório).
- Recomendação para produção: **MySQL 8.0/8.4 LTS** (suportados oficialmente) ou aceitar o aviso documentado com 9.x. O MySQL local não foi alterado.

## 10. Configuração para produção

| Variável | Obrigatória | Observação |
|---|---|---|
| `DITEC_PROFILE` | sim | `prod` (desliga seed, confia no proxy, logs WARN) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | sim | MySQL da hospedagem |
| `JWT_SECRET` | sim | ≥ 32 caracteres aleatórios (`openssl rand -base64 48`); a API recusa subir sem ela |
| `FRONTEND_URL` ou `DITEC_ALLOWED_ORIGINS` | sim | URL pública do site; o perfil prod **não sobe** sem ela (testado) |
| `GEMINI_API_KEY` | para IA | sem ela o chat responde no modo automático |
| `GEMINI_MODEL`, `GEMINI_FALLBACK_MODEL`, `GEMINI_READ_TIMEOUT_MS`, `JWT_EXPIRATION_MINUTES` | não | padrões no `.env.example` |
| `PORT` | não | lido pelo `Dockerfile` |

Front-end: em `Código-fonte/config.js` defina `window.DITEC_API_BASE_URL` com a URL pública da API e inclua essa URL no `connect-src` da meta CSP
das 4 páginas. Testado localmente no perfil `prod`: health `GET /actuator/health` → `{"status":"UP"}`, CORS da origem autorizada 200 e de outra 403,
cabeçalhos de segurança presentes, seed bloqueado. Nada foi publicado.

## 11. `index.html.bak`

Versão **mais antiga** do `index.html`: não tem nenhum `id` ausente do atual; faltam-lhe login/cadastro, campos de bairro e defeito e a integração com a API.
Só existe nele o rastreamento **simulado** (números fictícios `OS-2025-1001…1003`) e um rótulo antigo — nada a incorporar. Sem histórico Git disponível
(a pasta não é um repositório). **Recomendação:** excluir (aguarda confirmação). Enquanto isso, o deploy deixou de publicá-lo.

## 12. `pom.xml`

Versão do artefato `5.0.0` mantida (decisão do grupo; o README fala em "v6/v7"). Atualizações justificadas na seção 9; adicionado
`spring-boot-starter-actuator` (health check da hospedagem). `mvn test`: compila sem erros e **30/30** após as atualizações.

## 13–14. Testes executados e resultados

| Teste | Resultado esperado | Resultado obtido | Status |
|---|---|---|---|
| Compilação + `mvn test` (JUnit/MockMvc/H2) | sem falhas | 30 executados, 30 aprovados, 0 ignorados | ✅ |
| Inicialização Spring Boot 3.5.16 no MySQL 9.7 | sobe e valida migrações | iniciou em ~2,5 s; 2 migrações validadas | ✅ |
| Migrações do zero (banco temporário) | V1+V2 aplicadas | aplicadas, 10 tabelas | ✅ |
| Fluxo completo da OS (`qa/teste-fluxo-os.sh`, 10 passos) | todos os passos | 17/17 verificações | ✅ |
| Interface (`qa/teste-interface.mjs`, 1280 e 375 px) | formulários, botões, links, chat, rastreio | 22/22 | ✅ |
| Integridade do banco após os testes | 0 inconsistências | 0 em 5 consultas (13 OS, 73 eventos) | ✅ |
| Perfil prod (health, CORS, falha rápida, seed bloqueado) | conforme configuração | conforme | ✅ |
| Cenários de falha do chatbot | resposta amigável, chave fora do log | conforme (seção 5) | ✅ |
| axe-core WCAG 2.1 A/AA | 0 violações | 0 em 22 cenários | ✅ |
| Teclado / títulos / foco | 10/10 | 10/10 | ✅ |
| Lighthouse | ≥ 90 | celular ✅ todas; desktop: index 89, internas 82–86 | ⚠️ parcial |
| Chatbot com IA real e latência < 2 s | medido | **não executado (sem chave)** | ⛔ |

Autenticação, permissões, agendamento e rastreamento estão cobertos pelos testes automatizados e pelo fluxo da OS.

## 15. Arquivos alterados nesta etapa

- **Back-end:** `ChatService`, `ChatFallbackService`, `RestClientConfig`, `ChatCompletionResponse`, `SecurityConfig`, `OrdemServico` (documentação do campo),
  `DataSeeder`, `FinalizarOSRequest`, `AgendamentoCreateRequest`, `RegisterRequest`, `ClienteUpdateRequest`, `application.yml`,
  **novo** `application-prod.yml`, `pom.xml`, `.env.example`, `run-local.sh`; testes `AgendamentoFlowIT`, `RegrasAuxiliaresTest`.
- **Front-end:** `index.html`, `conta.html`, `tecnico.html`, `admin.html`, `script.js`, `dashboard-shared.js`, `os-equipe.js`, `styles.css`,
  **novos** `config.js`, `carregar-fontes.js`.
- **Projeto:** **novos** `.gitignore`, pasta `qa/` (scripts de Lighthouse, axe, teclado, interface, fluxo da OS e benchmark do chat),
  este relatório; `.github/workflows/deploy-pages.yml`; `README.md`.
- Nada foi excluído.

## 16. Pendências restantes

1. **Créditos na Hugging Face** (ação do titular da conta): comprar créditos pré-pagos ou assinar o PRO em https://huggingface.co/settings/billing. Depois reiniciar a API (ou aguardar 5 min da pausa automática) e executar `cd qa && node benchmark-chat.mjs 15` para medir a IA real (meta < 2 s). A chave já está configurada.
2. Desempenho desktop do Lighthouse abaixo de 90 em 4 páginas (causa e alternativa na seção 7).
3. Avaliação manual de acessibilidade com leitor de tela (seção 8).
4. Decisão do grupo: excluir `index.html.bak`; versão do `pom.xml`; MySQL 8.x LTS em produção ou aceitar o aviso do Flyway.
5. Publicação: preencher as variáveis da seção 10 na hospedagem (não feito, por falta de autorização).

## 17. Execução local

```bash
cd Prototipos/backend && ./run-local.sh            # API em http://localhost:8080
cd Código-fonte && python3 -m http.server 5500     # site em http://localhost:5500
cd Prototipos/backend && mvn test                  # 30 testes
cd qa && npm install && npm run instalar-navegador # 1ª vez (ferramentas locais)
cd qa && npm run a11y && npm run teclado && npm run interface && npm run fluxo-os
cd qa && npm run lighthouse                        # ou: node servidor-producao.mjs + npm run lighthouse:producao
```

## 18. Hospedagem

1. Banco MySQL 8.x (recomendado) e usuário dedicado; a API cria as tabelas pelo Flyway na primeira execução.
2. API: `Prototipos/backend/Dockerfile` (Render/Railway/Fly) com as variáveis da seção 10 (`DITEC_PROFILE=prod`); health check em `/actuator/health`.
3. Front-end: GitHub Pages (workflow existente). Antes, ajuste `Código-fonte/config.js` e o `connect-src` da CSP com a URL da API.
4. Teste após publicar: login, agendamento, rastreio (protocolo + telefone), chatbot e painéis.


## Migração para Google Gemini (09/11/2026)

**Motivo:** a conta da Hugging Face (`wikimiwi`) estava sem créditos (HTTP 402). O chatbot passou a usar a Google Gemini API.

**Implementado**
- `GeminiClient` (novo): `POST {GEMINI_API_URL}/models/{GEMINI_MODEL}:generateContent`, chave no header `x-goog-api-key`
  (nunca na URL, no front ou nos logs), `systemInstruction` com as regras da DITEC, histórico convertido para os papéis
  `user`/`model`, raciocínio desligado no 2.5 (`thinkingBudget: 0`) e `thinkingLevel` no 3.x, partes de "pensamento" descartadas.
- Erros tratados: 400 `API_KEY_INVALID`/401 (chave), 402 (crédito), 403 (permissão), 404 (modelo → tenta o `GEMINI_FALLBACK_MODEL`
  uma vez), 429 e 5xx (no máximo 2 novas tentativas, espera progressiva com jitter), 504/timeout (sem repetição),
  conteúdo bloqueado (mensagem educada), JSON inválido ou sem texto. Pausa automática da IA após falhas persistentes.
- `ChatService` reescrito: mesmo contrato com o front (`choices` + `fonte`), histórico de 10 mensagens, `chat_log` mantido,
  prompt de sistema com as regras comerciais existentes. `RateLimitFilter` e validações inalterados.
- Removidos: `HuggingFaceProperties`, configuração `ditec.huggingface` e variáveis `HF_*` do `.env.example`;
  constantes mortas (`HF_MODEL`, prompt antigo) do `script.js`. Visual do chat inalterado.
- Modelo: `gemini-2.5-flash` (pedido) como padrão, com `gemini-3.5-flash-lite` como alternativo automático — a documentação
  oficial informa que o 2.5 Flash está descontinuado (desligamento em 01/12/2026) e restrito a quem já o usava.

**Testes (resultados reais)**

| Teste | Resultado |
|---|---|
| `mvn test` completo | **49/49** (14 `GeminiClientTest` + 5 `ChatGeminiIT` com Gemini simulado + 30 anteriores), 0 ignorados |
| Formato da requisição, header de autenticação, chave fora da URL | ✅ (simulado) |
| Contexto da conversa (papéis, ordem, junção, descarte de `system`) | ✅ (simulado e via endpoint) |
| 429 com nova tentativa; tentativas limitadas a 3 chamadas | ✅ |
| 503 com nova tentativa; 400/401/402/403 sem repetição; 404 → modelo alternativo | ✅ |
| Timeout (800 ms) sem travar; resposta inválida; conteúdo bloqueado; partes de raciocínio ignoradas | ✅ |
| Endpoint `/api/chat`: contrato do front, `chat_log`, pausa após chave inválida, chave ausente dos logs, validações 422 | ✅ |
| **Endpoint real do Google com chave propositalmente inválida** (não consome cota) | ✅ Google respondeu 400 `API_KEY_INVALID` ao `models/gemini-2.5-flash:generateContent` com `x-goog-api-key`; DITEC classificou como chave inválida, respondeu ao usuário e não registrou a chave |
| Interface (Playwright, 1280 e 375 px) | ✅ 22/22, chat funcionando no modo automático |
| **Chave real fornecida (09/11/2026)** | ⛔ **recusada pelo Google (HTTP 401)**: a chave começa com `AQ.` e não é aceita como chave da Gemini API — `ACCESS_TOKEN_TYPE_UNSUPPORTED` (header `x-goog-api-key` e `?key=`) e `API_KEY_SERVICE_BLOCKED` (como Bearer), tanto em `models.list` quanto em `generateContent`. O DITEC tratou: resposta automática em 0,44 s, IA pausada 5 min, chave ausente do log |
| **Nova chave (09/11/2026)** | ✅ aceita (`models.list` 200); `gemini-2.5-flash` → **404 "no longer available to new users"** → troca automática para `gemini-3.5-flash-lite` |
| **Respostas reais da IA** (diagnóstico com perguntas, contexto, fora do escopo/computadores, tentativa de mudar regras + cartão, cheiro de gás) | ✅ 5/5 coerentes com as regras; não inventou preço, não confirmou agendamento, recusou dados de cartão |
| **Erro factual encontrado e corrigido** | ⚠️→✅ a IA disse "das 07h às 07h"; prompt reescrito em português acentuado com fatos explícitos; 4/4 respostas seguintes corretas ("das 7h às 20h") |
| **Rajada de 15 perguntas sem intervalo** | ✅ 9 pela IA; na 10ª o Google respondeu **429 RESOURCE_EXHAUSTED** (limite por minuto do plano gratuito) → resposta automática e pausa; nova chamada após a janela funcionou (recuperação). Melhoria: o sistema passou a respeitar o `retryDelay` do Google em vez de insistir |
| **Latência final (12 perguntas, 7 s de intervalo, prompt final)** | ✅ 12/12 da IA, 0 falhas, 100%. **1ª: 1.428 ms; seguintes: média 1.003 ms (mín. 842, máx. 1.175, p95 1.175); geral: média 1.038 ms, p95 1.428 ms** — meta < 2 s atendida |
| Interface com IA real (Playwright, 1280 e 375 px) | ✅ 22/22 |
| Chave em logs, relatórios ou código | ✅ 0 ocorrências |
| `mvn test` após os ajustes | ✅ **52/52** |

**Situação:** chatbot funcionando com IA real (Gemini `gemini-3.5-flash-lite`), meta de latência atendida. Medição reproduzível com
`cd qa && node benchmark-chat.mjs 12 7000`. A chave antiga `HF_API_KEY` que está no `.env` local não é mais usada e pode ser apagada pelo titular.
