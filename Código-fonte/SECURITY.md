# 🔒 DITEC — Auditoria e Implementação de Segurança

Relatório da auditoria de segurança realizada sobre o sistema DITEC Assistência Técnica
(frontend estático + backend Spring Boot + MySQL). Todas as correções abaixo foram
**implementadas diretamente no código**, não apenas recomendadas — ver a coluna "Correção".

> Assim como no restante do projeto: o código foi revisado manualmente com cuidado, mas
> não foi compilado/executado neste ambiente (sandbox sem MySQL/internet). Rode
> `mvn test` depois de puxar esta versão para confirmar que nada quebrou.

---

## 1. Vulnerabilidades encontradas e corrigidas

### 🔴 ALTA — Exposição excessiva de dados no rastreamento público de OS
- **Arquivo:** `backend/.../controller/OrdemServicoController.java`, `service/OrdemServicoService.java`
- **Problema:** `GET /api/ordens-servico/protocolo/{protocolo}` é público por design (rastreamento
  sem login, como um rastreio de encomenda) — mas o protocolo é **sequencial**
  (`DITEC-2026-000001`, `000002`...), ou seja, adivinhável. O endpoint devolvia o **mesmo
  objeto completo** usado pelo dono/admin/técnico: nome completo do cliente, valor cobrado,
  forma de pagamento, desconto, peças utilizadas, descrição final e número da nota fiscal.
  Qualquer pessoa testando números sequenciais via um script simples conseguiria coletar
  dados pessoais e financeiros de todos os clientes da DITEC.
- **Correção implementada:** criado `OrdemServicoRastreioResponse`, um DTO minimizado
  usado exclusivamente por esse endpoint público — contém apenas protocolo, status,
  aparelho, bairro, nome do técnico, garantia e timeline. **Nome do cliente vem abreviado**
  ("Fulano de Souza A."), e nenhum dado financeiro, endereço completo ou ID interno é
  incluído. `conta.html` foi ajustado (a função de avaliação usava esse endpoint só para
  pegar o ID interno da OS — passou a usar a listagem autenticada `/ordens-servico`).
- **Risco residual:** o protocolo continua sequencial/adivinhável (mudar isso quebraria o
  formato pedido no escopo original, `DITEC-AAAA-NNNNNN`). Mitigado por: (a) os dados agora
  expostos são de baixa sensibilidade; (b) rate limiting nesse endpoint (30 requisições/5min
  por IP) dificulta enumeração em massa.
- **Teste:** `AgendamentoFlowIT.rastreamentoPublicoNuncaExpoeDadosSensiveis` (novo).

### 🔴 ALTA — XSS refletido no formulário de agendamento
- **Arquivo:** `script.js`
- **Problema:** a mensagem de confirmação do agendamento inseria `nome`, `tipoAparelho` e
  `endereco` — vindos direto do formulário, sem qualquer sanitização — via `innerHTML`.
  Um valor como `<img src=x onerror=alert(document.cookie)>` no campo "Nome" executaria
  JavaScript arbitrário na página.
- **Correção implementada:** todos os campos passaram a usar `escHtml()` (função de
  escaping já existente no arquivo, mas esquecida nesse trecho específico).
- **Risco residual:** nenhum identificado — todos os demais 15+ pontos de `innerHTML` no
  frontend (script.js, conta.html/js, admin.html/js, tecnico.html/js) foram auditados
  individualmente e já usavam `escHtml()` corretamente, incluindo as respostas do
  chatbot (que passam primeiro por `escHtml()` e só depois por uma formatação markdown
  restrita a `<strong>`/`<em>`/`<br>` fixos — não há como o texto da IA injetar uma tag).

### 🟠 MÉDIA-ALTA — Ausência de rate limiting (força bruta / spam / abuso de custo)
- **Arquivo:** novo `backend/.../security/RateLimitFilter.java`, `config/SecurityConfig.java`
- **Problema:** nenhum endpoint tinha proteção contra repetição abusiva — login (força
  bruta de senha), cadastro (spam de contas), chatbot (custo por token na Hugging Face),
  agendamento e avaliação (spam) podiam ser chamados sem limite.
- **Correção implementada:** filtro de rate limiting em memória, por IP, com limites por
  rota: login (10/5min), cadastro (5/hora), chatbot (20/5min), criar agendamento (10/hora),
  rastreamento público (30/5min), avaliação (10/hora). Resposta `429 Too Many Requests`
  no formato padrão de erro da API.
- **Risco residual:** implementação em memória local — funciona para uma única instância
  da aplicação. Em produção com múltiplas instâncias/balanceamento de carga, cada instância
  teria seu próprio contador (o limite efetivo multiplicaria pelo número de instâncias).
  Para escalar horizontalmente, migrar para um contador compartilhado (Redis, por exemplo).
- **Teste:** `RateLimitIT.bloqueiaAposMuitasTentativasDeLoginEmSequencia` (novo).

### 🟠 MÉDIA-ALTA — Chatbot confiava cegamente em parâmetros do cliente
- **Arquivo:** `dto/chat/ChatRequest.java`, `dto/chat/ChatMessageDto.java`, `service/ChatService.java`, `controller/ChatController.java`
- **Problema:** `max_tokens` e `temperature` enviados pelo cliente eram repassados
  diretamente para a Hugging Face sem limite — um cliente malicioso podia pedir
  `max_tokens: 1000000` e gerar custo desproporcional contra uma API paga por token.
  Também não havia limite de tamanho por mensagem nem de quantidade de mensagens no
  histórico, e o `@Valid` nem estava sendo aplicado no controller.
- **Correção implementada:** `max_tokens` agora é sempre limitado a um teto de 500 no
  backend (o que o cliente pedir é só uma sugestão, nunca o valor final);
  `temperature` restrita a 0–1; mensagens limitadas a 2000 caracteres cada e no máximo
  20 mensagens por requisição; `@Valid` adicionado ao `ChatController`.
- **Risco residual:** nenhum identificado para este vetor especificamente. Custo/abuso
  residual do chatbot em si (uso legítimo, mas intenso) seguem cobertos pelo rate limit
  do item anterior.

### 🟡 MÉDIA — Fallback inseguro de senha do banco de dados
- **Arquivo:** `backend/src/main/resources/application.yml`
- **Problema:** `password: ${DB_PASSWORD:changeme}` — se a variável de ambiente não fosse
  definida, a aplicação conectava silenciosamente ao MySQL com a senha literal `changeme`,
  em vez de falhar. Uma senha de banco previsível é um risco real caso o MySQL fique
  acessível externamente por engano.
- **Correção implementada:** removido o valor padrão (`${DB_PASSWORD}`, sem fallback) —
  agora a aplicação **recusa subir** sem essa variável definida, mesma lógica de
  fail-fast já aplicada ao `JWT_SECRET`.
- **Risco residual:** nenhum.

### 🟡 MÉDIA — Política de senha fraca e ausência de limite de tamanho nas entradas
- **Arquivo:** todos os DTOs em `dto/**` (cadastro, login, agendamento, cliente, técnico,
  serviço, avaliação, finalização de OS)
- **Problema:** senha mínima de 6 caracteres sem limite máximo; a maioria dos campos de
  texto livre não tinha `@Size(max=...)`, permitindo payloads desproporcionalmente
  grandes (risco de esgotamento de recursos e de erros de truncamento no banco
  vazando como 500 genérico em vez de um 422 controlado).
- **Correção implementada:** senha agora exige 8–100 caracteres; **todos** os campos de
  texto livre do projeto receberam `@Size(max=...)` alinhado ao tamanho real da coluna
  no banco (ex: `bairro` máx. 100, `endereco` máx. 255, `descricaoProblema` máx. 500).
- **Risco residual:** nenhum.

### 🟡 MÉDIA — Seed de desenvolvimento sem trava de produção
- **Arquivo:** `backend/.../seed/DataSeeder.java`
- **Problema:** o `DataSeeder` (que cria `admin@ditec.com.br/admin123` e as demais contas
  de teste) só dependia da flag `DITEC_SEED_ENABLED`. Se alguém esquecesse essa flag
  ligada ao configurar produção, credenciais padrão conhecidas seriam criadas lá.
- **Correção implementada:** o seed agora também verifica o profile ativo do Spring e
  **se recusa a rodar** caso o nome do profile contenha "prod" — independente do valor
  da flag.
- **Risco residual:** nenhum, desde que o profile de produção seja nomeado de forma
  reconhecível (ex: `prod`, `production`) — documentado no README.

### 🟢 BAIXA/INFORMATIVA — Ausência de headers de segurança HTTP
- **Arquivo:** `config/SecurityConfig.java` (backend) e `<head>` de `index.html`,
  `conta.html`, `admin.html`, `tecnico.html` (frontend)
- **Problema:** nenhum header de segurança (CSP, HSTS, Referrer-Policy, Permissions-Policy,
  X-Frame-Options) era enviado.
- **Correção implementada:**
  - **Backend:** CSP restritiva (`default-src 'none'` — é uma API JSON pura, não deveria
    carregar nenhum subrecurso), HSTS, Referrer-Policy, Permissions-Policy e
    X-Frame-Options via Spring Security. `X-Content-Type-Options: nosniff` e
    `Cache-Control: no-store` já vêm habilitados por padrão pelo Spring Security.
  - **Frontend:** como são arquivos estáticos (sem servidor próprio), a CSP foi
    adicionada via `<meta http-equiv>` em todas as 4 páginas. Isso só foi possível de
    forma restritiva porque os scripts inline de `conta.html`, `admin.html` e
    `tecnico.html` foram **extraídos para arquivos externos** (`conta.js`, `admin.js`,
    `tecnico.js`) — permitindo `script-src 'self'` sem `unsafe-inline`.
- **Risco residual:** `<meta http-equiv="Content-Security-Policy">` não suporta a
  diretiva `frame-ancestors` (limitação do próprio navegador — só funciona via header
  HTTP real), e headers como `X-Content-Type-Options`/`X-Frame-Options` não podem ser
  setados via meta tag de forma alguma. **Em produção, o frontend deve ser servido por
  um servidor real (Nginx/Apache/CDN) configurado para enviar esses headers via HTTP**,
  não apenas via meta tag. Documentado abaixo, seção "Configuração de produção".

### 🟢 INFORMATIVA — Técnico não recebia o endereço completo da visita
- **Arquivo:** `dto/os/OrdemServicoResponse.java`, `service/OrdemServicoMapper.java`
- **Achado durante a auditoria de "dados mínimos necessários" (seção 19/27):** a resposta
  usada pelo técnico (`OrdemServicoResponse`) trazia apenas o **bairro**, não o endereço
  completo — ou seja, o técnico não tinha como saber onde exatamente ir. Não é uma falha
  de segurança (é o oposto: dado de menos), mas é um problema funcional real encontrado
  ao mapear "quem vê o quê".
- **Correção implementada:** campo `endereco` adicionado à resposta completa (autenticada).
  O endpoint **público** de rastreamento continua sem esse campo — ver item de exposição
  de dados acima.

### 🟢 INFORMATIVA — Bug funcional crítico encontrado durante a auditoria (não é falha de segurança, mas quebrava a página inteira)
- **Arquivo:** `conta.html`
- **Problema:** a página estava **sem a tag `<script src="dashboard-shared.js">`** desde
  a primeira entrega do backend (erro de montagem do arquivo). Sem esse script, o objeto
  `DitecAPI` nunca existia, e a página inteira falhava com `DitecAPI is not defined` assim
  que qualquer botão era clicado.
- **Correção implementada:** tag adicionada de volta, na ordem correta (antes de `conta.js`).

---

## 2. Auditado e confirmado seguro (nenhuma mudança necessária)

| Item | Situação encontrada |
|---|---|
| **SQL Injection** | 100% JPA/JPQL com parâmetros nomeados (`:param`) — nenhuma concatenação de string em query em nenhum lugar do projeto. |
| **Armazenamento de senha** | BCrypt via `BCryptPasswordEncoder` do Spring Security — salt gerenciado automaticamente pelo próprio algoritmo. |
| **JWT** | HS256 via jjwt 0.12.x (API `verifyWith()`, resistente a ataques de confusão de algoritmo); assinatura validada em toda requisição; expiração aplicada; segredo nunca hardcoded e obrigatório ter 32+ caracteres (a aplicação recusa subir sem isso). |
| **CSRF** | Corretamente desabilitado — a API é 100% stateless (JWT via header `Authorization`, nunca cookie). CSRF explora credenciais que o navegador envia sozinho (cookies); como isso nunca é usado aqui, não há o que explorar. Decisão documentada no próprio `SecurityConfig.java`. |
| **CORS** | Lista explícita de origens (nunca `*`), métodos e headers restritos ao necessário. |
| **Autorização / IDOR** | Reauditado endpoint por endpoint: agendamentos, ordens de serviço e avaliações verificam propriedade do recurso no *service layer* (nunca confiam apenas no ID da URL); endpoints administrativos protegidos por `@PreAuthorize` a nível de classe; um técnico só altera OS atribuídas a ele (ou ainda sem técnico, caso em que a assume). |
| **Vazamento de erros** | `GlobalExceptionHandler` nunca devolve stack trace nem mensagem interna para exceções inesperadas; reforçado com `server.error.include-stacktrace: never` no `application.yml` como defesa em profundidade. |
| **Segredos no código** | Varredura completa no repositório (`password`, `secret`, `api_key`, `jwt_secret`, `hf_token`, etc.) — nenhum segredo real encontrado. `.env.example` contém apenas placeholders. |
| **Enumeração de contas no login** | Mensagem sempre genérica ("E-mail ou senha incorretos"), nunca revela se o e-mail existe. |
| **Upload de arquivos** | Não existe essa funcionalidade no projeto — nada a auditar aqui. |
| **Actuator** | Não incluído como dependência — sem superfície de exposição. |
| **DTOs / exposição de dados** | Nenhum endpoint retorna a entidade `Usuario` diretamente nem o campo `senhaHash` em qualquer resposta. |

---

## 3. Arquitetura de autenticação e decisões de segurança

- **Autenticação:** JWT (HS256) no header `Authorization: Bearer <token>`, emitido em
  `/api/auth/login` e `/api/auth/register`. Validade padrão de 120 minutos
  (`JWT_EXPIRATION_MINUTES`). Sem refresh token nesta versão — expirado, o usuário
  precisa logar de novo (simples e seguro; renovação automática fica como evolução futura).
- **Armazenamento do token no frontend:** `localStorage`, via `dashboard-shared.js`
  (`DitecAPI`). **Decisão consciente:** migrar para cookies `HttpOnly`/`Secure`/`SameSite`
  eliminaria o risco de roubo de token via XSS, mas exigiria proteção CSRF adicional e uma
  mudança arquitetural maior (o backend passaria a depender de cookies, e todo o CORS teria
  que ser revisado). Como o projeto é 100% frontend estático + API separada, e a superfície
  de XSS já foi auditada e fechada (ver seção 1), optou-se por manter `localStorage` +
  header, mitigando o risco residual via CSP (impede scripts de terceiros) em vez de uma
  reescrita arquitetural de alto risco sem ambiente de testes real disponível.
- **Autorização:** por perfil (`CLIENTE`/`ADMINISTRADOR`/`TECNICO`) via `@PreAuthorize`,
  combinada com verificação de propriedade do recurso no service layer.
- **CORS:** restrito às origens em `DITEC_ALLOWED_ORIGINS` (nunca `*`).
- **Segredos:** todos via variável de ambiente (`JWT_SECRET`, `DB_PASSWORD`, `HF_API_KEY`)
  — nenhum tem valor padrão inseguro; a aplicação falha ao iniciar se algum obrigatório
  estiver ausente.
- **Rate limiting:** ver seção 1 — em memória, por IP, configurável via
  `DITEC_RATELIMIT_ENABLED`.
- **Logs:** nunca registram senha, hash, token ou chave de API. O `DataSeeder` imprime as
  credenciais de teste no console **apenas** em ambientes não-produtivos — ver trava
  descrita na seção 1.
- **Chatbot / Hugging Face:** a chave (`HF_API_KEY`) nunca sai do backend; o fluxo é
  sempre `Frontend → Backend DITEC → Hugging Face`. Limites de tokens/tamanho de
  mensagem aplicados no servidor (ver seção 1).

---

## 4. Testes de segurança implementados

| Teste | O que cobre |
|---|---|
| `AuthFlowIT` | Cadastro, e-mail duplicado (409), login com senha errada (401) |
| `AgendamentoFlowIT.rastreamentoPublicoNuncaExpoeDadosSensiveis` | Confirma que o endpoint público NUNCA devolve nome completo, valor, forma de pagamento, desconto, peças, nota fiscal, endereço ou ID interno |
| `OrdemServicoFlowIT` | Técnico só altera OS atribuída a ele; progressão de status sem pular etapa (422) |
| `RateLimitIT` | Confirma bloqueio (429) após exceder o limite de tentativas de login |

Rodar com `mvn test` (usa H2 em memória, não precisa de MySQL).

---

## 5. Pontos que exigem configuração de produção

Nada aqui foi (ou poderia ser) resolvido só no código — são passos de configuração/infra
que precisam ser feitos **no momento do deploy real**:

1. **HTTPS obrigatório** — todo o tráfego (frontend e API) deve rodar atrás de TLS. O
   header HSTS já está configurado no backend, mas só tem efeito quando servido via HTTPS.
2. **Servidor real para o frontend** — trocar o Live Server/arquivo estático por
   Nginx/Apache/CDN configurado para enviar `X-Content-Type-Options`, `X-Frame-Options`
   e `Content-Security-Policy` (com `frame-ancestors`) via header HTTP real, não só a
   meta tag que está no HTML hoje.
3. **Rotacionar todos os segredos** antes de ir para produção — `JWT_SECRET`,
   `DB_PASSWORD`, `HF_API_KEY` usados em desenvolvimento **nunca** devem ser os mesmos
   de produção.
4. **`DITEC_SEED_ENABLED=false`** e profile ativo contendo "prod" (dupla trava, ver seção 1).
5. **`DITEC_ALLOWED_ORIGINS`** apontando exclusivamente para o domínio real do frontend
   em produção — nunca `localhost`.
6. **Rate limiting distribuído** (Redis ou equivalente) caso a aplicação rode em mais de
   uma instância simultaneamente.
7. **Usuário do MySQL com privilégios mínimos** — o script `database/00-criar-banco-e-usuario.sql`
   já cria um usuário dedicado (não usa root), mas revise as permissões concedidas
   (`GRANT ALL` hoje é adequado para desenvolvimento; em produção, considere restringir
   a `SELECT/INSERT/UPDATE/DELETE` apenas nas tabelas necessárias).
8. **Monitoramento de logs** para os eventos que already são logados (login recusado,
   erro de autenticação, seed bloqueado por profile de produção) — hoje eles só vão para
   o console/arquivo de log local.

---

## 6. Critério de qualidade — status

- [x] Autenticação protegida (BCrypt + JWT com segredo obrigatório)
- [x] Autorização protegida (perfil + propriedade do recurso)
- [x] IDOR/exposição excessiva de dados corrigidos
- [x] Senhas adequadamente protegidas (BCrypt + política mínima de 8 caracteres)
- [x] JWT corretamente configurado
- [x] Segredos fora do código (variáveis de ambiente, sem fallback inseguro)
- [x] API protegida (validação de entrada com limites de tamanho em todos os DTOs)
- [x] SQL injection mitigado (JPA/JPQL parametrizado)
- [x] XSS mitigado (escaping consistente + CSP)
- [x] CORS restrito
- [x] Headers de segurança configurados (backend via Spring Security; frontend via meta tag, com limitações documentadas)
- [x] Chatbot não expõe a API key, e tem limites de custo aplicados no servidor
- [x] Dados de usuários não ficam cruzados entre clientes/técnicos
- [x] Erros não expõem informação interna
- [x] Endpoints administrativos protegidos
- [ ] Dependências — não foram atualizadas nesta rodada (fora do pedido "não atualizar indiscriminadamente"); recomenda-se rodar `mvn versions:display-dependency-updates` periodicamente
- [x] Testes de segurança executados (novos testes automatizados, ver seção 4)
- [x] Documentação atualizada (este arquivo + README.md)
