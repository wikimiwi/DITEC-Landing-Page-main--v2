# 🔧 DITEC - Sistema de Assistência Técnica

<p align="center">
  Sistema web desenvolvido para gerenciamento de serviços de assistência técnica, clientes e Ordens de Serviço.
</p>

---

## 📋 Sobre o Projeto

O **DITEC** é uma plataforma digital desenvolvida para auxiliar no gerenciamento de uma assistência técnica.

O projeto permite a evolução de uma landing page institucional para um sistema mais completo, incluindo funcionalidades como cadastro de clientes, gerenciamento de Ordens de Serviço, autenticação de usuários, área do cliente e painel administrativo.

---

## 🚀 Funcionalidades

### 👤 Área do Cliente

* Cadastro e autenticação de usuários;
* Visualização de informações do cliente;
* Solicitação e acompanhamento de serviços;
* Consulta de Ordens de Serviço;
* Acompanhamento do status dos atendimentos.

### 🛠️ Gerenciamento de Ordens de Serviço

* Criação de Ordens de Serviço;
* Acompanhamento do status do atendimento;
* Associação de clientes e técnicos;
* Gerenciamento dos serviços realizados.

### 👨‍💼 Painel Administrativo

* Gerenciamento de clientes;
* Gerenciamento de técnicos;
* Controle de Ordens de Serviço;
* Acompanhamento dos atendimentos;
* Visualização e gerenciamento das informações do sistema;
* Geração de relatórios.

### 🔐 Segurança

* Sistema de autenticação;
* Autorização de usuários;
* Autenticação baseada em JWT;
* Implementação de práticas de segurança no backend.

---

## 🧰 Tecnologias Utilizadas

### Frontend

* HTML5
* CSS3
* JavaScript

### Backend

* Java
* Spring Boot
* Spring Security
* JWT

### Banco de Dados

* MySQL

### Ferramentas

* Git
* GitHub
* Visual Studio Code
* MySQL

---

## 📂 Estrutura do Projeto

```text
DITEC-Landing-Page-main--v2/
│
├── .vscode/
│
├── Banco de Dados/
│   └── database/
│
├── Código-fonte/          # front-end (HTML/CSS/JS) — publicado no GitHub Pages
│   └── assetslogos/       # logos das marcas usados pelo site
│
├── Prototipos/
│   └── backend/
│
├── assetslogos/           # originais dos logos
├── docs/                  # relatório de auditoria
└── README.md
```

---

## ⚙️ Como Executar o Projeto (local)

**Pré-requisitos:** Java 21, Maven 3.9+, MySQL 8+ (testado com MySQL 9.7 — ver nota sobre Flyway no relatório final), Python 3 (servir o front-end) e Node 20+ (só para os testes em `qa/`).
Stack: Spring Boot 3.5.16, Flyway 11.7.2, MySQL Connector/J 9.7.0, JJWT 0.12.7.
No macOS: `brew install openjdk@21 maven mysql`.

### 1️⃣ Banco de dados (uma única vez)

```bash
mysql -u root -p < "Banco de Dados/database/00-criar-banco-e-usuario.sql"
```

Troque a senha `uma-senha-forte-local` do script por uma sua. As tabelas são criadas pelo **Flyway** ao iniciar o backend
(`V1__schema.sql` + `V2__rastreamento_os.sql`).

### 2️⃣ Configuração do backend

```bash
cd Prototipos/backend
cp .env.example .env      # preencha DB_PASSWORD (a do passo 1) e JWT_SECRET (openssl rand -base64 48)
```

`.env` nunca é versionado. Variáveis: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`,
`DITEC_ALLOWED_ORIGINS`, `DITEC_SEED_ENABLED`, `DITEC_RATELIMIT_ENABLED`, `DITEC_TRUST_PROXY` (só `true` atrás de proxy
reverso confiável) e `GEMINI_API_KEY` (opcional — sem ela o chatbot responde em modo automático com as regras da empresa; ver seção do chatbot abaixo).

### 3️⃣ Iniciar o backend (porta 8080)

```bash
cd Prototipos/backend
./run-local.sh
```

O script carrega o `.env`, compila e sobe a API em `http://localhost:8080`. Com `DITEC_SEED_ENABLED=true` e banco vazio,
são criados **dados fictícios de demonstração** (ver tabela abaixo).

### 4️⃣ Iniciar o front-end (porta 5500)

```bash
cd Código-fonte
python3 -m http.server 5500
```

Acesse `http://localhost:5500` (o CORS da API libera as portas 5500/5501; o Live Server do VS Code também funciona).

### 5️⃣ Testes automatizados

```bash
cd Prototipos/backend
mvn test
```

30 testes (JUnit 5 + Spring Boot Test + MockMvc + H2 em modo MySQL): agendamento, autenticação, ciclo de vida completo da OS,
histórico, permissões, rastreamento, rate limit, matriz de transições, FAQ do chatbot e integração com a Gemini API
(simulada por um servidor local — os testes nunca chamam o Google nem consomem a cota).

Testes de qualidade do front-end (ferramentas locais em `qa/`, sem instalação global):

```bash
cd qa && npm install && npm run instalar-navegador   # primeira vez
npm run a11y          # axe-core (WCAG 2.1 A/AA) em todas as páginas, inclusive painéis logados
npm run teclado       # navegação por teclado, foco visível e títulos
npm run interface     # formulários, botões, links, chatbot e rastreamento (Playwright)
npm run fluxo-os      # fluxo completo da OS contra a API real
npm run lighthouse    # Lighthouse em celular e desktop
node benchmark-chat.mjs 15   # latência do chatbot (média, p95, falhas)
```

### Contas e OS de demonstração (somente desenvolvimento — dados fictícios)

| Perfil | E-mail | Senha |
|---|---|---|
| Administrador | admin@ditec.com.br | admin123 |
| Técnico | tecnico@ditec.com.br | tecnico123 |
| Cliente | cliente@ditec.com.br | cliente123 |

| Protocolo | Situação | Telefone para rastrear |
|---|---|---|
| DITEC-AAAA-000001 | Entregue (histórico completo) | (11) 97777-2222 |
| DITEC-AAAA-000002 | Aguardando aprovação do orçamento | (11) 97777-2222 |
| DITEC-AAAA-000003 | Cancelada | (11) 96666-3333 |

O seed **nunca roda** com perfil contendo `prod` nem se já existir um administrador. Em produção use `DITEC_SEED_ENABLED=false`.

---

## 🧾 Ordem de Serviço: ciclo de vida e rastreamento

```text
AGENDADO (aguardando avaliação) → EM_DIAGNOSTICO → [AGUARDANDO_APROVACAO] → [AGUARDANDO_PECAS]
  → EM_MANUTENCAO → EM_TESTES → CONCLUIDO → [AGUARDANDO_RETIRADA] → ENTREGUE
CANCELADO: a partir de qualquer etapa anterior aos testes (somente administrador, com motivo,
           ou o cliente ao cancelar o agendamento / recusar o orçamento)
```

Regras: uma etapa por vez (sem pular nem regredir, exceto Testes → Manutenção e Manutenção ↔ Aguardando peças);
o atendimento só começa com o agendamento confirmado; sair de "Em diagnóstico" exige diagnóstico registrado;
"Aguardando aprovação" exige valor de orçamento; **concluir** só pela finalização (valor, forma de pagamento e descrição
do serviço; 5% de desconto em Pix/dinheiro; garantia de 90 dias); ENTREGUE e CANCELADO são finais.
Cada mudança, diagnóstico, orçamento, previsão, observação e atribuição de técnico grava um registro em `timeline_os`
(status anterior/novo, data e hora, usuário responsável, observação, visível ou não ao cliente) — nunca apagado.

**Rastreamento pelo cliente:** seção *Rastrear OS* do site, com **protocolo + telefone do agendamento** (impede que terceiros
consultem OS alheias testando protocolos sequenciais). Mostra etapa atual, linha do tempo real, diagnóstico, técnico,
orçamento, previsão, última atualização e garantia — sem endereço, telefone, valores pagos ou anotações internas.
Clientes logados acompanham e aprovam/recusam orçamentos em *Minha Conta*.

> **Fora do escopo:** emissão de Nota Fiscal Eletrônica (NF-e). O sistema não integra com emissores fiscais;
> a finalização da OS permite apenas registrar, opcionalmente, o número de um comprovante/nota emitido fora do sistema.

## 🤖 Chatbot com Google Gemini API

O chatbot usa a **Google Gemini API** (substituiu a Hugging Face em 09/11/2026). A interface do site não mudou.

**Arquitetura**

```text
Navegador (script.js)  --POST /api/chat {messages, sessionId}-->  Spring Boot
  ChatController -> ChatService (regras da DITEC no servidor, histórico das últimas 10 mensagens)
    -> GeminiClient --POST {GEMINI_API_URL}/models/{GEMINI_MODEL}:generateContent
                     header x-goog-api-key: <GEMINI_API_KEY>  (a chave nunca vai ao navegador, à URL ou aos logs)
    <- resposta {choices:[{message:{content}}], fonte:"ia"|"automatica"}  (mesmo contrato de antes)
```

- **Contexto:** o front envia o histórico; o servidor converte `user`/`assistant` em `user`/`model` (formato do Gemini) e envia as regras
  da DITEC como `systemInstruction` (o navegador não consegue alterá-las).
- **Falhas:** 429 e 5xx têm no máximo 2 novas tentativas com espera progressiva; chave inválida (400 `API_KEY_INVALID`/401), sem crédito (402)
  e sem permissão (403) não são repetidas e pausam a IA por 5 min; limite do plano pausa por 1 min; timeout/instabilidade, 30 s; modelo
  inexistente (404) tenta uma vez o `GEMINI_FALLBACK_MODEL`. Em todos os casos o usuário recebe na hora uma resposta automática identificada
  ("Resposta automática — o assistente de IA está indisponível"). O `RateLimitFilter` continua limitando 20 mensagens/5 min por IP.

**Como obter e configurar a chave**

1. Acesse https://aistudio.google.com/apikey (Google AI Studio), entre com sua conta Google e clique em *Create API key*.
2. Em `Prototipos/backend/.env` preencha `GEMINI_API_KEY="..."` (o arquivo está no `.gitignore`; nunca coloque a chave no código ou no front).
   Na hospedagem, cadastre `GEMINI_API_KEY` nas variáveis de ambiente do serviço.
3. Reinicie a API (`./run-local.sh`).

| Variável | Padrão | Uso |
|---|---|---|
| `GEMINI_API_KEY` | (vazia) | Chave do Google AI Studio. Vazia = modo automático |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Modelo principal |
| `GEMINI_FALLBACK_MODEL` | `gemini-2.5-flash-lite` | Usado se o principal não estiver disponível para a conta |
| `GEMINI_API_URL` | `https://generativelanguage.googleapis.com/v1beta` | Endpoint da API |
| `GEMINI_MAX_OUTPUT_TOKENS`, `GEMINI_TEMPERATURE`, `GEMINI_THINKING_LEVEL`, `GEMINI_READ_TIMEOUT_MS`, `GEMINI_MAX_RETRIES` | 400, 0.6, LOW, 15000, 2 | Ajustes finos |

> **Escolha do modelo:** o pedido inicial era `gemini-2.5-flash`, mas em 09/11/2026 o Google respondeu para a chave do projeto
> *"This model models/gemini-2.5-flash is no longer available to new users"* (404; o modelo está descontinuado e será desligado em
> 01/12/2026). Por isso o padrão é o **`gemini-3.5-flash-lite`** (estável), com `gemini-2.5-flash-lite` como reserva. Medição real:
> 12/12 respostas da IA, **média 1,04 s, p95 1,43 s** (plano gratuito, requisições espaçadas). Se o principal ficar indisponível, o
> sistema passa ao alternativo e só volta a testar o principal após 6 h. Modelos Gemini 3+ usam `thinkingLevel`; os 2.5 usam
> `thinkingBudget=0` — o ajuste é automático.

**Como verificar se está funcionando**

```bash
curl -s -X POST http://localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"user","content":"Qual a garantia?"}]}'
```

`"fonte":"ia"` = resposta do Gemini; `"fonte":"automatica"` = IA indisponível (o motivo aparece no log da API, sem a chave).
Para medir o tempo de resposta: `cd qa && node benchmark-chat.mjs 15`.

**Limitações do plano gratuito:** os limites (requisições por minuto, tokens por minuto e requisições por dia) valem **por projeto**, variam
por modelo e são mostrados no Google AI Studio; a cota diária reinicia à meia-noite (horário do Pacífico). Ao atingir o limite a API
responde 429 e o chat passa ao modo automático pelo tempo que o Google indicar (`retryDelay`). Nos testes, uma rajada de 10
perguntas em menos de 1 minuto já atingiu o limite por minuto do plano gratuito. No plano gratuito o Google pode usar o conteúdo enviado para melhorar seus
produtos — não envie dados pessoais sensíveis pelo chat (o assistente é instruído a nunca pedir senhas ou dados bancários).

## 🔌 Principais endpoints da API

| Método e rota | Acesso | Descrição |
|---|---|---|
| `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me` | público / logado | Cadastro, login (JWT) e usuário atual |
| `GET /api/agendamentos/disponibilidade?data=AAAA-MM-DD` | público | Horários livres do dia |
| `POST /api/agendamentos` | público | Cria agendamento + OS (protocolo) |
| `GET/PUT/DELETE /api/agendamentos/{id}` | dono ou admin | Consulta, altera, cancela |
| `POST /api/ordens-servico/rastreio` `{protocolo, telefone}` | público | Rastreamento seguro |
| `GET /api/ordens-servico` | cliente / técnico | Minhas OS |
| `GET /api/ordens-servico/{id}` | dono, técnico da OS ou admin | Detalhe + histórico |
| `PUT /api/ordens-servico/{id}/status` `{status, observacao}` | técnico / admin | Muda etapa (regras acima) |
| `PUT /api/ordens-servico/{id}/diagnostico` `{diagnostico, valorOrcamento, previsaoConclusao}` | técnico / admin | Diagnóstico e orçamento |
| `PUT /api/ordens-servico/{id}/previsao` `{previsaoConclusao, observacao}` | técnico / admin | Previsão de conclusão |
| `POST /api/ordens-servico/{id}/observacoes` `{texto, visivelCliente}` | técnico / admin | Observação no histórico |
| `POST /api/ordens-servico/{id}/finalizar` | técnico / admin | Conclui (valor, pagamento, descrição, peças, nº de comprovante opcional) |
| `POST /api/ordens-servico/{id}/orcamento/resposta` `{aprovado}` | cliente dono | Aprova ou recusa orçamento |
| `POST /api/ordens-servico/{id}/avaliacao` | cliente dono | Nota 1–5 após conclusão |
| `GET /api/admin/ordens-servico?status=&tecnicoId=&protocolo=&cliente=` | admin | Lista com filtros |
| `PUT /api/admin/ordens-servico/{id}/tecnico` `{tecnicoId}` | admin | Atribui técnico (ativo) |
| `GET /api/admin/dashboard`, `/clientes`, `/agendamentos`, `/tecnicos`, `/servicos`, `/relatorios/*` | admin | Gestão e relatórios CSV |
| `POST /api/chat` | público | Chatbot (Google Gemini pelo backend; modo automático sem `GEMINI_API_KEY`) |

Erros seguem `{status, error, message, path}`: 401 (sem login/sessão expirada), 403 (sem permissão), 404, 409 (conflito),
422 (regra de negócio/validação), 429 (rate limit), 502 (integração externa).

Relatórios: [`docs/AUDITORIA-2026-11-09.md`](docs/AUDITORIA-2026-11-09.md) e [`docs/RELATORIO_FINAL_CORRECOES_DITEC.md`](docs/RELATORIO_FINAL_CORRECOES_DITEC.md) (chatbot, Lighthouse, WCAG, Flyway, produção e hospedagem).

---

## 🔄 Histórico do Projeto

O projeto passou por diferentes etapas de desenvolvimento:

* Criação inicial da estrutura do sistema;
* Desenvolvimento das páginas para clientes e administradores;
* Implementação do backend;
* Integração com banco de dados MySQL;
* Implementação de autenticação JWT;
* Desenvolvimento do gerenciamento de agendamentos e Ordens de Serviço;
* Criação de painel administrativo;
* Implementação de relatórios;
* Auditoria e melhorias de segurança;
* Organização da estrutura do projeto.

---

## 🎯 Objetivo

O objetivo do projeto é desenvolver uma solução digital para facilitar o gerenciamento de uma assistência técnica, centralizando informações de clientes, serviços, técnicos e Ordens de Serviço.

Além disso, o projeto busca aplicar conhecimentos práticos de:

* Desenvolvimento Web;
* Desenvolvimento Backend;
* Banco de Dados;
* Segurança da Informação;
* Autenticação de usuários;
* Gerenciamento de sistemas.

---

## 👥 Desenvolvedores

Projeto desenvolvido como parte de estudos e desenvolvimento acadêmico.

**Equipe DITEC**

* Henrique de Sousa Cunha
* Caua Uema de Lucca

---

## 📌 Status do Projeto

🚧 **Em desenvolvimento**

O sistema continua recebendo melhorias, correções e novas funcionalidades.

---

## 🔗 Repositório

[Acessar o repositório no GitHub](https://github.com/wikimiwi/DITEC-Landing-Page-main--v2)

---

<p align="center">
  Desenvolvido com 💻 e dedicação pela equipe DITEC.
</p>
