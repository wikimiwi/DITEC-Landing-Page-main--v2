# 🛠️ DITEC Assistência Técnica — Sistema Completo (v6)

Sistema de atendimento da **DITEC Assistência Técnica**, empresa fictícia de manutenção de
eletrodomésticos em São Paulo e Grande SP. Este README documenta a versão **v6**, que evolui a
landing page (v5, 100% front-end com dados simulados) para um sistema **real e integrado**:

```
Frontend (HTML/CSS/JS)  →  API REST (Spring Boot)  →  MySQL
Frontend (chatbot)      →  Spring Boot (proxy)      →  Hugging Face (Qwen2.5-72B)
```

> Este projeto foi finalizado a partir do *Prompt Mestre de Finalização* do semestre 2026.3
> (disciplina de Análise e Projeto de Sistemas II). O site, o design e o chatbot **já existiam**
> e foram preservados; o que este README documenta é a parte que passou a existir agora:
> banco de dados real, autenticação real, agendamento/OS reais e painel administrativo real.

---

## 📁 Estrutura do projeto

```
DITEC-Landing-Page-main--v2/
├── index.html              # site público (home, agendamento, rastreamento, chatbot)
├── conta.html               # área do cliente (login/cadastro real, perfil, agendamentos, avaliação)
├── admin.html                # painel administrativo (login real, dashboard, CRUD, relatórios)
├── tecnico.html                # área do técnico (login real, minhas OS, iniciar/finalizar atendimento)
├── script.js                  # lógica do site público — agora consome a API real
├── conta.js / admin.js / tecnico.js  # lógica de cada página (extraída do HTML — necessário pra CSP sem unsafe-inline)
├── dashboard-shared.js         # window.DitecAPI — cliente HTTP compartilhado pelas 4 páginas
├── styles.css                   # estilos (inalterado)
├── Manifest.JSON                 # manifesto PWA (inalterado)
├── assetslogos/                   # logos das marcas atendidas
│
├── backend/                        # API Java/Spring Boot
│   ├── pom.xml
│   ├── .env.example
│   └── src/main/java/com/ditec/assistencia/
│       ├── entity/          # Usuario, Cliente, Tecnico, Servico, Agendamento,
│       │                    # OrdemServico, TimelineOS, Avaliacao, ChatLog
│       ├── repository/      # Spring Data JPA
│       ├── service/         # regras de negócio
│       ├── controller/      # endpoints REST
│       ├── security/        # JWT + Spring Security + RateLimitFilter
│       ├── dto/              # records de entrada/saída
│       ├── exception/         # tratamento de erros (401/403/404/409/422/500)
│       └── seed/               # dados de desenvolvimento (DataSeeder)
│   └── src/main/resources/
│       └── db/migration/V1__schema.sql   # schema MySQL (Flyway)
│
├── database/                        # cópia de leitura do schema + script de criação do banco
├── SECURITY.md                       # auditoria de segurança (leia antes de ir pra produção)
└── README.md                         # este arquivo
```

---

## 1. Requisitos

| Ferramenta | Versão | Uso |
|---|---|---|
| **Java** | 21 | Backend (Spring Boot) |
| **Maven** | 3.9+ (ou use o wrapper, se adicionar um) | Build do backend |
| **MySQL** | 8.x | Banco de dados |
| **Navegador moderno** | — | Frontend (Chrome, Edge, Firefox) |
| **Extensão Live Server** (VS Code) ou similar | — | Servir o frontend sem problemas de CORS |
| **Conta na Hugging Face** | — | Chave de API para o chatbot (`HF_API_KEY`) |

O frontend **não precisa de Node/npm** — é HTML/CSS/JS puro.

---

## 2. Banco de dados

```bash
# 1) Crie o banco e o usuário (uma única vez)
mysql -u root -p < database/00-criar-banco-e-usuario.sql
```

Isso cria o database `ditec_assistencia` e o usuário `ditec_user`. As tabelas **não** precisam
ser criadas manualmente — o Flyway (embutido no backend) aplica `V1__schema.sql` sozinho no
primeiro start. O arquivo `database/schema.sql` é apenas uma cópia de leitura, para quem quiser
inspecionar a estrutura sem abrir o projeto Java.

**Tabelas criadas:** `usuario`, `cliente`, `tecnico`, `servico`, `agendamento`, `ordem_servico`,
`timeline_os`, `avaliacao`, `chat_log` — todas com FKs, índices e `CHECK` constraints
(ver seção 8 para o detalhamento).

---

## 3. Backend (Spring Boot)

```bash
cd backend
cp .env.example .env       # edite com suas credenciais reais
# exporte as variáveis do .env no seu terminal/IDE, ou configure-as
# diretamente nas variáveis de ambiente do sistema/IDE (IntelliJ, Eclipse, VS Code)

mvn spring-boot:run
```

O servidor sobe em `http://localhost:8080`. Na **primeira execução**, dois processos automáticos
acontecem:

1. **Flyway** cria todas as tabelas (`V1__schema.sql`).
2. **DataSeeder** popula dados de desenvolvimento (administrador, técnico, cliente e uma OS de
   exemplo já concluída, com timeline e nota fiscal) — ver credenciais na seção 6. Isso só
   acontece se ainda não existir nenhum `ADMINISTRADOR` no banco, então é seguro reiniciar a
   aplicação quantas vezes quiser.

### 3.1. Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DB_URL` | sim | JDBC URL do MySQL |
| `DB_USERNAME` | não (padrão `ditec_user`) | Usuário do banco |
| `DB_PASSWORD` | **sim, sem valor padrão** | Senha do banco — app recusa subir sem isso |
| `JWT_SECRET` | **sim, sem valor padrão** | Segredo HS256, **mínimo 32 caracteres** (`openssl rand -base64 48`) — app recusa subir sem isso |
| `JWT_EXPIRATION_MINUTES` | não (padrão 120) | Validade do token |
| `HF_API_KEY` | sim, para o chatbot funcionar | Token da Hugging Face (escopo *Inference Providers*) |
| `HF_MODEL` | não (padrão `Qwen/Qwen2.5-72B-Instruct`) | Modelo usado no chatbot |
| `DITEC_ALLOWED_ORIGINS` | não (padrão `localhost:5500`) | Origens liberadas no CORS |
| `DITEC_SEED_ENABLED` | não (padrão `true`) | Liga/desliga o `DataSeeder` (nunca roda se o profile ativo contiver "prod") |
| `DITEC_RATELIMIT_ENABLED` | não (padrão `true`) | Liga/desliga a proteção contra força bruta/spam |

Todos os detalhes e valores de exemplo estão em `backend/.env.example`. **A aplicação recusa
subir sem `JWT_SECRET`** (falha rápida, em vez de usar uma chave fraca por padrão).

### 3.2. Testes automatizados

```bash
cd backend
mvn test
```

Os testes rodam com **H2 em memória** (perfil `test`), então não precisam do MySQL. Cobrem os
fluxos pedidos na seção "34. Testes" do prompt mestre:

- `AuthFlowIT` — cadastro, e-mail duplicado (409), login com senha errada (401)
- `AgendamentoFlowIT` — geração de protocolo, conflito de horário (409), bairro fora de área (422), rastreamento público
- `OrdemServicoFlowIT` — técnico assume a OS, progressão de status sem pular etapa, desconto de 5% no pagamento à vista

> **Sobre validação neste ambiente:** o código foi escrito e revisado manualmente com cuidado,
> mas **não foi compilado nem executado** por quem o gerou (sandbox sem acesso à internet/MySQL).
> Rode `mvn test` e `mvn spring-boot:run` como primeiro passo para confirmar que tudo compila e
> sobe — e reporte qualquer erro de compilação encontrado, para que possamos corrigi-lo juntos.

---

## 4. Frontend

Abra a pasta raiz do projeto com o **Live Server** (ou qualquer servidor estático) — não abra o
`index.html` direto como arquivo (`file://`), pois o `fetch` para a API é bloqueado nesse modo.

Por padrão, o frontend aponta para `http://localhost:8080`. Para mudar (ex: backend em outra
porta/host), defina antes de carregar `dashboard-shared.js`:

```html
<script>window.DITEC_API_BASE_URL = 'http://localhost:9090';</script>
<script src="dashboard-shared.js"></script>
```

---

## 5. Fluxo de ponta a ponta (o que dá pra demonstrar)

**Cliente:** abre o site → agenda uma visita (com ou sem conta) → recebe o protocolo →
completa o cadastro (se agendou como visitante — o agendamento é vinculado automaticamente
pelo telefone) → faz login em `conta.html` → vê o agendamento, o endereço/perfil, rastreia a OS
→ quando concluída, avalia o atendimento (1–5 estrelas).

**Admin:** login em `admin.html` (`admin@ditec.com.br` / `admin123`) → dashboard com números
reais → confirma agendamentos pendentes → atribui um técnico → acompanha a OS → cadastra novos
técnicos/serviços → exporta relatórios CSV (agendamentos, OS, avaliações, chatbot).

**Técnico:** loga em `tecnico.html` (`tecnico@ditec.com.br` / `tecnico123`) → vê as OS atribuídas a
ele → assume a
OS (associação automática ao primeiro técnico que mexe numa OS sem técnico) → avança o status
`AGENDADO → EM_ATENDIMENTO → CONCLUIDO` → finaliza informando valor/peças/forma de pagamento →
garantia de 90 dias é ativada automaticamente.

**Chatbot:** o widget já existente continua funcionando exatamente igual — só que agora fala
com o Spring Boot de verdade, que esconde a `HF_API_KEY`, repassa para a Hugging Face e grava
cada pergunta/resposta em `chat_log`.

---

## 6. Credenciais de teste (ambiente de desenvolvimento)

Criadas automaticamente pelo `DataSeeder` — **nunca use em produção**:

| Perfil | E-mail | Senha |
|---|---|---|
| Administrador | `admin@ditec.com.br` | `admin123` |
| Técnico | `tecnico@ditec.com.br` | `tecnico123` |
| Cliente | `cliente@ditec.com.br` | `cliente123` |

O seed também cria uma **Ordem de Serviço concluída de exemplo** (com timeline completa e nota
fiscal) — o protocolo exato aparece no log do backend na primeira execução
(`Protocolo de teste para rastreamento: DITEC-AAAA-NNNNNN`), e pode ser usado imediatamente no
rastreamento público do site.

---

## 7. Endpoints da API

Prefixo: `http://localhost:8080/api`

| Método | Rota | Acesso | Descrição |
|---|---|---|---|
| POST | `/auth/register` | público | Cadastro de cliente |
| POST | `/auth/login` | público | Login (cliente, admin ou técnico) |
| GET | `/auth/me` | autenticado | Dados do usuário logado |
| GET | `/clientes/me` | cliente | Perfil do cliente |
| PUT | `/clientes/me` | cliente | Atualizar perfil |
| GET | `/clientes/me/agendamentos` | cliente | Meus agendamentos |
| GET | `/agendamentos/disponibilidade?data=AAAA-MM-DD` | público | Horários livres/ocupados |
| POST | `/agendamentos` | público (ou cliente) | Criar agendamento |
| GET / PUT / DELETE | `/agendamentos/{id}` | dono ou admin | Ver / alterar / cancelar |
| GET | `/ordens-servico/protocolo/{protocolo}` | público | Rastreamento (com timeline) |
| GET | `/ordens-servico/{id}` | dono, técnico ou admin | Detalhe da OS |
| GET | `/ordens-servico` | cliente ou técnico | Minhas OS |
| PUT | `/ordens-servico/{id}/status` | técnico ou admin | Avançar status (sem regressão) |
| POST | `/ordens-servico/{id}/finalizar` | técnico ou admin | Concluir, valor, garantia 90d |
| POST | `/ordens-servico/{id}/avaliacao` | cliente | Avaliar atendimento concluído |
| GET | `/servicos` | público | Catálogo de serviços ativos |
| POST | `/chat` | público | Proxy do chatbot (Hugging Face) |
| GET | `/admin/dashboard` | admin | Números gerais |
| GET | `/admin/clientes` | admin | Lista de clientes |
| GET | `/admin/agendamentos` | admin | Lista paginada |
| PUT | `/admin/agendamentos/{id}/confirmar` | admin | PENDENTE → AGENDADO |
| PUT | `/admin/agendamentos/{id}/tecnico` | admin | Atribuir técnico |
| GET | `/admin/ordens-servico` | admin | Todas as OS |
| GET/POST/PUT | `/admin/tecnicos[/{id}]` | admin | CRUD de técnicos |
| PUT | `/admin/tecnicos/{id}/status` | admin | Ativar/desativar |
| GET/POST/PUT | `/admin/servicos[/{id}]` | admin | CRUD de serviços |
| PUT | `/admin/servicos/{id}/status` | admin | Ativar/desativar |
| GET | `/admin/relatorios/{agendamentos\|os\|avaliacoes\|chatbot}` | admin | Exportação CSV |

Erros seguem sempre o mesmo formato JSON (`timestamp`, `status`, `error`, `message`, `path`).

---

## 8. Banco de dados — visão geral do schema

```
usuario 1───1 cliente 1───N agendamento N───1 tecnico
                 │               │1
                 │               │1
                 │           ordem_servico 1───N timeline_os
                 │               │1
                 └───────────────┴──1 avaliacao
usuario 1───1 tecnico
usuario 1───1 cliente ──N── chat_log
servico ──N── agendamento
```

- `agendamento.cliente_id` é **opcional** — permite agendar sem conta (visitante).
- `agendamento` e `ordem_servico` compartilham o **mesmo protocolo** (`DITEC-AAAA-NNNNNN`),
  gerado a partir do próprio ID auto-increment do agendamento.
- Enums (`tipo`, `status`, etc.) são `VARCHAR` + `CHECK`, não `ENUM` nativo do MySQL — mais
  portável e sem atrito com o `ddl-auto` do Hibernate.

Detalhes completos em `backend/src/main/resources/db/migration/V1__schema.sql`.

---

## 9. Decisões técnicas (o que não estava 100% especificado)

| Decisão | Por quê |
|---|---|
| Agendamento sem conta continua permitido | Já era o comportamento da v5; exigir login quebraria a UX existente |
| OS é criada junto com o agendamento (status inicial `AGENDADO`) | O rastreamento por protocolo já funcionava antes de qualquer confirmação manual |
| Um técnico "assume" uma OS sem técnico ao mexer nela pela 1ª vez | Evita precisar de uma tela extra de distribuição manual; admin também pode atribuir antes |
| JWT no `localStorage` (não cookie httpOnly) | Simplicidade para uma SPA estática sem servidor de sessão; documentado como ponto de evolução para produção real |
| Dados de teste via `CommandLineRunner` (Java), não SQL com hash fixo | Garante que a senha semeada sempre bate com o `PasswordEncoder` real usado no login |
| CSV gerado manualmente (sem lib externa) | Projeto pequeno, não justifica dependência extra |
| Campo "Bairro / Endereço" do formulário de agendamento virou dois campos (`Bairro` + `Endereço`) | Necessário para validar de verdade a área de cobertura no backend (antes era só cosmético no front) |
| Chatbot mantém o **mesmo contrato JSON** que já existia (`model/messages/max_tokens/...`) | O widget não precisou ser reescrito — só o back dele, que agora é real |
| Verificador de CEP/bairro da home continua 100% client-side | Já funcionava bem e não depende de dado que precise vir do banco |

---

## 10. Regras de negócio implementadas

- Atendimento presencial: **segunda a sábado, 07h–20h** (horários fixos, os mesmos que já existiam no calendário); **domingo bloqueado**.
- Bairro fora da área de cobertura é **recusado no backend** (não só escondido no front).
- Aparelhos a gás recebem `prioridadeGas = true` automaticamente.
- Cancelamento/alteração só permitidos em `PENDENTE` ou `AGENDADO`.
- Progressão da OS **sem regressão**: `AGENDADO → EM_ATENDIMENTO → CONCLUIDO` (ou `CANCELADO` a qualquer momento antes de concluir).
- Pagamento à vista (Pix ou dinheiro) aplica **5% de desconto** automaticamente na finalização.
- Garantia de **90 dias** ativada na finalização da OS.
- Protocolo único (`DITEC-AAAA-NNNNNN`), gerado no backend, nunca no frontend.
- Senhas sempre com **BCrypt**; `HF_API_KEY` nunca é exposta ao navegador; autorização por perfil sempre checada no backend (nunca confia em role enviada pelo cliente).

---

## 11. Pendências (o que ficou para uma próxima etapa)

- **PWA**: manifesto e ícones já existiam; um Service Worker para funcionamento offline não foi adicionado (fora do escopo pedido).
- **Compilação/execução real**: ainda não confirmada pelo usuário neste projeto — rode `mvn compile` (ou `mvn test`) e reporte qualquer erro real de build.

---

## 12. Segunda rodada — bug corrigido e pendências resolvidas

Depois da primeira entrega, revisei o backend de novo (sem depender de compilador — checagem
estrutural de chaves/parênteses/imports/pacotes em todos os 88 arquivos) e encontrei um **bug real
de lógica**, não relacionado a sintaxe:

> `OrdemServicoService.listarMinhas()` usava `cud.getId()` (o ID do **Usuario** logado) para
> buscar Ordens de Serviço por `tecnico_id` — mas `tecnico_id` referencia o ID da entidade
> **Tecnico**, que tem sua própria chave primária (diferente da de Usuario). Na prática, um
> técnico nunca conseguiria ver as próprias OS atribuídas. Corrigido para resolver primeiro o
> `Tecnico` pelo `usuario_id` antes de buscar — e foi adicionado o teste
> `tecnicoVeAOsQueAssumiuNaListagemDeMinhasOS` para não deixar essa regressão voltar.

Também foram implementadas as três pendências da entrega anterior:

- **`tecnico.html`** (novo): login próprio do técnico, lista de "Minhas Ordens de Serviço" com
  estatísticas, botão para iniciar atendimento e formulário para finalizar (valor, forma de
  pagamento, peças, nota fiscal) — usando os mesmos endpoints que já existiam.
- **Edição de técnico/serviço no admin**: botão "Editar" nas tabelas de Técnicos e Serviços,
  reaproveitando `PUT /admin/tecnicos/{id}` e `PUT /admin/servicos/{id}` (já existiam, só faltava
  a UI). No caso do técnico, o e-mail fica bloqueado durante a edição — é a chave de login e o
  backend não permite trocá-lo por essa rota.
- **Edição de agendamento pelo cliente**: botão "✏️ Editar" ao lado de "Cancelar" em
  `conta.html`, com um mini-formulário inline (aparelho, data/horário, bairro, endereço,
  descrição) que chama `PUT /api/agendamentos/{id}` — reaproveita toda a validação que já existia
  no backend (bairro atendido, horário permitido, conflito de agenda).

---

## 13. Terceira rodada — auditoria e implementação de segurança

Depois das duas rodadas de funcionalidade, foi feita uma auditoria de segurança completa
(login, JWT, autorização/IDOR, SQL injection, XSS, CORS/CSRF, headers HTTP, segredos,
chatbot, rate limiting, validação de entrada). O relatório completo — vulnerabilidade por
vulnerabilidade, com severidade, arquivo afetado, correção implementada e risco residual —
está em **[`SECURITY.md`](./SECURITY.md)**.

Resumo do que mudou nesta rodada:

- 🔴 **Corrigido:** o rastreamento público de OS (`/api/ordens-servico/protocolo/{protocolo}`)
  expunha nome completo, valor, forma de pagamento, desconto e nota fiscal de qualquer
  cliente para qualquer pessoa capaz de adivinhar um protocolo sequencial. Agora devolve
  um DTO minimizado, com nome abreviado e sem dados financeiros.
- 🔴 **Corrigido:** XSS real em `script.js` (mensagem de confirmação do agendamento inseria
  nome/aparelho/endereço sem escapar).
- 🟠 **Adicionado:** rate limiting (login, cadastro, chatbot, agendamento, avaliação,
  rastreamento público) — protege contra força bruta, spam e abuso de custo do chatbot.
- 🟠 **Corrigido:** o chatbot confiava cegamente em `max_tokens`/`temperature` enviados
  pelo cliente — agora o backend sempre aplica um teto de segurança.
- 🟡 **Corrigido:** `DB_PASSWORD` tinha um valor padrão inseguro (`changeme`) caso a
  variável de ambiente não fosse definida — agora a aplicação recusa subir sem ela.
- 🟡 **Reforçado:** política de senha (mínimo 8 caracteres) e limite de tamanho máximo em
  **todos** os campos de texto livre de todos os DTOs do projeto.
- 🟡 **Corrigido:** `DataSeeder` agora se recusa a criar as contas de teste se o profile
  ativo contiver "prod", mesmo que a flag de seed esteja ligada por engano.
- 🟢 **Adicionado:** headers de segurança HTTP no backend (CSP, HSTS, Referrer-Policy,
  Permissions-Policy) e CSP + Referrer-Policy no frontend — o que exigiu extrair os
  scripts inline de `conta.html`/`admin.html`/`tecnico.html` para arquivos externos.
- 🟢 **Corrigido (achado durante a auditoria, não é falha de segurança):** `conta.html`
  estava sem a tag `<script src="dashboard-shared.js">` desde a primeira entrega — a
  página inteira falhava com `DitecAPI is not defined`.
- 🟢 **Corrigido (achado durante a auditoria):** o técnico não recebia o endereço completo
  da visita, só o bairro — adicionado à resposta autenticada da OS.

Tudo que **já estava** correto (BCrypt, JWT, JPA parametrizado, CORS restrito, autorização
por propriedade do recurso, etc.) está documentado como "auditado e confirmado seguro" no
`SECURITY.md`, seção 2.

---

## 14. Checklist final

- [x] Banco MySQL real com 9 tabelas, FKs, índices e constraints (Flyway)
- [x] Autenticação real (Spring Security + JWT + BCrypt), 3 perfis (cliente/admin/técnico)
- [x] Agendamento real (persistido, com validação de área/horário/conflito, edição e cancelamento pelo cliente)
- [x] Rastreamento de OS real (timeline no banco, protocolo único, resposta pública minimizada)
- [x] Painel administrativo real (dashboard, clientes, agendamentos, OS, técnicos, serviços — com criar/editar/ativar-desativar)
- [x] Tela própria do técnico (login, minhas OS, iniciar/finalizar atendimento)
- [x] Relatórios exportáveis em CSV (agendamentos, OS, avaliações, chatbot)
- [x] Chatbot com proxy real (chave protegida, log de interações, limites de custo aplicados no servidor)
- [x] Regras de negócio do DRS (horário, área, gás, garantia, desconto, progressão de status)
- [x] Auditoria de segurança completa (ver `SECURITY.md`) — IDOR, XSS, rate limiting, headers, validação de entrada
- [x] Testes automatizados (H2, sem depender do MySQL para rodar) — incluindo testes de segurança (rate limit, exposição de dados no rastreamento público)
- [x] Dados de teste seguros (gerados em runtime, nunca em SQL com senha fixa, nunca criados em profile de produção)
- [ ] Build/execução confirmados pelo usuário — rode `mvn compile` (ou `mvn test`) e me avise o resultado
