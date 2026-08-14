

# 🛠️ DITEC Assistência — Landing Page Inteligente com Chatbot IA

Bem-vindo ao repositório do projeto **DITEC Assistência**! Esta é uma solução de ponta a ponta desenvolvida para modernizar o atendimento de uma empresa especializada em manutenção de eletrodomésticos em São Paulo e Grande SP.

O coração do projeto é transformar a experiência clássica de "solicitar um orçamento" em uma jornada fluida, automatizada e inteligente, unindo uma interface estática premium a um **Chatbot com Inteligência Artificial** integrado nativamente a um backend de microserviços.

---

## 🎯 O Cenário e o Objetivo

O agendamento de serviços domésticos costuma ser burocrático ou depender 100% de interações humanas demoradas no WhatsApp. O objetivo da **DITEC v5** é resolver isso em três frentes:

1. **Triagem por IA:** Um chatbot que entende linguagem natural, responde dúvidas comuns sobre as políticas da empresa e filtra o interesse do cliente antes mesmo dele falar com um atendente.
2. **Autonomia de Agendamento:** Um calendário dinâmico que permite ao cliente escolher o horário sem precisar ligar para ninguém.
3. **Transparência:** Um sistema interno simulado de rastreamento de Ordens de Serviço (OS) diretamente pela interface.

---

## 🚀 Funcionalidades Principais

* **🤖 Chatbot IA Integrado:** Alimentado pelo modelo `Qwen/Qwen2.5-72B-Instruct` (via API do Hugging Face) intermediado por um proxy corporativo em **Spring Boot** (Java). Ele sabe tudo sobre a DITEC e recusa educadamente perguntas fora do escopo.
* **📅 Agendamento Online Automatizado:** Calendário interativo em Javascript puro que gerencia dias úteis, horários disponíveis e bloqueia slots já ocupados de forma visual e intuitiva.
* **🔍 Rastreamento de OS em Tempo Real:** Módulo que simula a consulta a um banco de dados de ordens de serviço. Experimente digitar as chaves de teste `OS-2025-1001`, `OS-2025-1002` ou `OS-2025-1003` para ver a linha do tempo mudar de estado!
* **📍 Verificador de Cobertura Inteligente:** O usuário digita o CEP ou o bairro e o sistema valida instantaneamente se a região está dentro do raio de atendimento da DITEC utilizando normalização de strings (removendo acentos e espaços extras).
* **✨ Interface Premium & Animações Fluidas:** Efeitos de inclinação (tilt) tridimensional nos cards ao passar o mouse, botões magnéticos que seguem o cursor, efeito cascata (stagger) em listas e efeitos de clique baseados em *Ripple ripples*.
* **⚖️ Conformidade Legal (LGPD):** Banner de consentimento de cookies customizado de acordo com a Lei Geral de Proteção de Dados (Lei 13.709/2018).

---

## 🛠️ Arquitetura e Tecnologias

A stack foi pensada para balancear performance no carregamento inicial (Client-Side) com segurança e robustez no processamento da IA (Server-Side).

### Frontend (Interface e Interações)

* **HTML5 & CSS3 Avançado:** Layout totalmente responsivo baseado em CSS Variables, Flexbox e Grid, com foco em acessibilidade (tags semânticas e atributos `aria-*`).
* **JavaScript Vanilla (ES6+):** Código limpo, modularizado usando IIFEs para evitar poluição do escopo global e manipulação direta do DOM para máxima performance e zero dependência de frameworks pesados.

### Backend (Camada de Proxy e IA)

* **Java & Spring Boot (Rodando localmente na porta `8080`):** Atua como uma camada segura (Proxy) que intercepta as chamadas do Chatbot, oculta chaves de API sensíveis, gerencia o histórico recente de conversas (contexto de até 4 mensagens) e injeta o *System Prompt* corporativo de forma segura antes de enviar a requisição para a API de inferência da **Hugging Face**.

---

## 💻 Estrutura do Código Analisado

### `index.html`

O ponto de entrada da aplicação. Contém a marcação semântica estruturada em seções fáceis de escanear (`#sobre`, `#servicos`, `#agendamento`, `#rastreamento`, `#faq`). Inclui também metatags Open Graph para SEO otimizado, JSON-LD estruturado para o Google reconhecer o negócio local (`LocalBusiness`), e a janela flutuante do Chatbot com gatilhos de acessibilidade.

### `script.js`

Centraliza toda a inteligência da interface. Está dividido logicamente em módulos auto-executáveis:

* `initCookies()`: Controla as preferências de privacidade salvas no `localStorage`.
* `askMistral()`: Gerencia a comunicação assíncrona (`fetch`) com o backend Java (`http://localhost:8080/api/chat`).
* `initCalendar()`: Controla a geração de dias, meses e o estado dos horários (`HORARIOS` e `OCUPADOS`).
* `initTracking()`: Renderiza dinamicamente as etapas visuais e as badges de status de uma Ordem de Serviço com base em mocks estruturados.

---

## 🔧 Como Executar o Projeto

1. **Clone o repositório:**
```bash
git clone https://github.com/seu-usuario/ditec-assistencia.git

```


2. **Inicie o Backend (Spring Boot):**
* Certifique-se de ter o ecossistema Java instalado.
* Configure sua chave de API da Hugging Face na sua aplicação Spring.
* Execute o servidor para escutar na porta `8080`. Ele deve expor o endpoint `POST /api/chat`.


3. **Abra o Frontend:**
* Como a integração utiliza chamadas `fetch` assíncronas para o `localhost`, abra o arquivo `index.html` utilizando um servidor local (como a extensão *Live Server* do VS Code) para evitar bloqueios de políticas de CORS pelo navegador.



---

## 💡 Próximos Passos (Roadmap de Evolução)

* [ ] Conectar o módulo de agendamento online diretamente a uma API do Google Calendar para automatizar a agenda dos técnicos em tempo real.
* [ ] Substituir os dados simulados de OS (`OS_DATA`) por requisições HTTP reais apontando para o banco de dados PostgreSQL/MySQL de produção da empresa através do backend em Spring Boot.

---

✉️ **Contribuições e Feedbacks:** Sinta-se à vontade para abrir uma *Issue* ou enviar um *Pull Request* se encontrar alguma melhoria na lógica do chat ou nas animações!
