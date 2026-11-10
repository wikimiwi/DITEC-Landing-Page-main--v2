---
title: "Requisitos funcionais e não funcionais, regras de negócio e histórias de usuário do sistema DITEC"
---


# Introdução

## Finalidade e método

Este documento especifica o que o sistema DITEC Assistência Técnica faz e as condições em que o faz, segundo as recomendações da disciplina para o levantamento de requisitos: requisitos funcionais, requisitos não funcionais, regras de negócio e restrições, com origem, prioridade, responsável e critério de aceitação (PROJETO E DESENVOLVIMENTO DE SOFTWARE, [s. d.]). A estrutura de cada requisito (identificador, descrição, prioridade, critério verificável e dependências) é compatível com as boas práticas de especificação da norma ISO/IEC/IEEE 29148 (ISO/IEC/IEEE, 2018).

Os requisitos foram **reconstruídos a posteriori** a partir do código na versão e58a3d2 (EQUIPE DITEC, 2026a), dos documentos do grupo (EQUIPE DITEC, 2026b) e dos testes existentes. Não há registro de entrevistas com usuários. Por isso, a **origem** de todos os requisitos é o sistema implementado, e as prioridades e os critérios de aceitação são propostos para validação do grupo.

## Convenções

Os identificadores seguem o padrão RFnn (requisito funcional), RNFnn (não funcional), RNnn (regra de negócio), HUnn (história de usuário) e CUnn (caso de uso). A prioridade usa a técnica MoSCoW, citada pela disciplina: *Must* (indispensável para o fluxo principal), *Should* (importante, mas há contorno) e *Could* (desejável). Nenhum requisito foi classificado como *Won't* nesta versão; as funcionalidades não implementadas estão no Apêndice D.

# Requisitos funcionais

Os 29 requisitos funcionais estão na Tabela 1. A distribuição por prioridade é: 13 Must, 12 Should e 4 Could. Os critérios de aceitação foram redigidos a partir do comportamento do código e dos testes; os códigos de resposta HTTP citados são os devolvidos pelo tratamento centralizado de exceções.

| ID | Requisito | Ator | Prior. | Critérios de aceitação | Depende de |
|--------|------------------|----------------|--------|-----------------------------------------|----------|
| RF01 | Cadastro de cliente | Visitante | Must | Com nome, e-mail, telefone (10 a 13 dígitos) e senha (8 a 100 caracteres), cria a conta com perfil CLIENTE e devolve o token; e-mail já cadastrado é recusado (409). Agendamento de visitante feito nas últimas 24 h com o mesmo telefone é vinculado à nova conta. | — |
| RF02 | Autenticação por e-mail e senha | Cliente, Técnico, Administrador | Must | Credenciais válidas devolvem token com o perfil; inválidas devolvem 401; o último acesso é registrado. | RF01 |
| RF03 | Identificação do usuário autenticado | Cliente, Técnico, Administrador | Should | O endpoint devolve os dados do usuário correspondente ao token; sem token válido, 401. | RF02 |
| RF04 | Perfil e endereço do cliente | Cliente | Should | O cliente consulta e atualiza os próprios dados e o endereço; outros perfis recebem 403. | RF02 |
| RF05 | Disponibilidade de horários | Visitante, Cliente | Must | Para uma data atendida, lista os 9 horários da grade ainda livres; domingos, datas passadas e horários já decorridos no dia não são oferecidos. | RN03, RN04, RN05 |
| RF06 | Solicitação de agendamento | Visitante, Cliente | Must | Pedido válido cria agendamento PENDENTE, OS AGENDADO e protocolo DITEC-AAAA-NNNNNN. Bairro não atendido, domingo, horário fora da grade ou telefone inválido resultam em 422; horário ocupado, em 409. Aparelho a gás recebe prioridade. | RF05; RN01 a RN08 |
| RF07 | Consulta, alteração e cancelamento de agendamento | Cliente, Administrador | Should | Somente o dono (ou o administrador) acessa o agendamento; alteração e cancelamento só em PENDENTE ou AGENDADO (demais casos, 422); o cancelamento cancela a OS vinculada. | RF06, RN06 |
| RF08 | Listagem dos agendamentos do cliente | Cliente | Should | Lista apenas os agendamentos do cliente autenticado. | RF06 |
| RF09 | Rastreamento público da OS | Visitante, Cliente | Must | Com protocolo no formato DITEC-AAAA-NNNNNN e telefone que confere nos 8 últimos dígitos, devolve status, etapa e eventos visíveis ao cliente, sem dados sensíveis; protocolo inexistente ou telefone divergente resultam na mesma resposta 404; formato inválido resulta em 422. | RF06, RN13 |
| RF10 | Consulta de ordens de serviço | Cliente, Técnico, Administrador | Must | O cliente vê apenas as suas OS; o técnico, as atribuídas a ele; acesso a OS de terceiros resulta em 403. | RF06 |
| RF11 | Atualização de status da OS | Técnico, Administrador | Must | Somente transições previstas (19) são aceitas; pular etapa, regredir ou solicitar CONCLUIDO por esta rota resulta em 422; o técnico atua apenas em OS atribuída a ele (ou assume OS sem técnico); cada mudança gera evento na linha do tempo e sincroniza o agendamento. | RF06, RN09 a RN11, RN15, RN18 a RN21 |
| RF12 | Finalização da OS | Técnico, Administrador | Must | Somente com a OS em EM_TESTES; exige valor (≥ 0), forma de pagamento válida (pix, dinheiro, cartão ou boleto) e descrição do serviço; Pix e dinheiro recebem 5% de desconto; define garantia de 90 dias e o status CONCLUIDO. | RF11, RN10 a RN12 |
| RF13 | Avaliação do atendimento | Cliente | Should | Nota de 1 a 5 e comentário opcional; apenas o dono da OS, com o serviço concluído e uma avaliação por OS (repetição, 409). | RF12, RN14 |
| RF14 | Catálogo de serviços | Visitante, Cliente | Should | A listagem pública devolve os serviços ativos. | RF21 |
| RF15 | Assistente virtual | Visitante, Cliente | Could | A pergunta é respondida pela IA (histórico limitado a 10 mensagens) ou, se a IA estiver indisponível, por resposta automática de FAQ; a interação é registrada em chat_log; excedido o limite de requisições, 429. | RN17 |
| RF16 | Painel administrativo | Administrador | Should | O endpoint devolve indicadores consolidados, acessível somente ao administrador. | RF02 |
| RF17 | Consulta de clientes | Administrador | Could | Lista os clientes cadastrados, visível somente ao administrador. | RF02 |
| RF18 | Gestão de agendamentos pelo administrador | Administrador | Must | Lista os agendamentos (aparelhos a gás primeiro); confirma apenas agendamentos PENDENTES; atribui técnico a agendamento que não esteja cancelado ou concluído. | RF06 |
| RF19 | Consulta de todas as OS com filtros | Administrador | Should | Lista todas as OS e filtra por status, técnico, protocolo e cliente. | RF06 |
| RF20 | Gestão de técnicos | Administrador | Must | Cria técnico com usuário de login (senha com mínimo de 8 caracteres), edita e ativa ou inativa. | RF02 |
| RF21 | Gestão do catálogo de serviços | Administrador | Should | Cria, edita e ativa ou inativa serviços. | RF02 |
| RF22 | Exportação de relatórios em CSV | Administrador | Could | Disponibiliza quatro relatórios: agendamentos, OS, avaliações e interações do chatbot. | RF02 |
| RF23 | Verificação de CEP e bairro no site | Visitante | Could | A página inicial informa se o CEP ou bairro está na área de atendimento (regra espelhada no navegador, em script.js). | RN01, RN02 |
| RF24 | Diagnóstico e orçamento | Técnico, Administrador | Must | Registra diagnóstico (obrigatório), valor do orçamento (0 a 999.999,99) e previsão; só com a OS em andamento e não concluída; o orçamento não pode ser alterado enquanto aguarda aprovação. | RF11 |
| RF25 | Resposta do cliente ao orçamento | Cliente | Must | Apenas o dono, com a OS em AGUARDANDO_APROVACAO: aprovar leva a EM_MANUTENCAO; recusar cancela a OS sem custo (diagnóstico gratuito). | RF24, RN19 |
| RF26 | Observações na OS | Técnico, Administrador | Should | Registra observação de até 500 caracteres, visível ao cliente (padrão) ou interna; observações internas não aparecem no rastreamento público. | RF10 |
| RF27 | Previsão de conclusão | Técnico, Administrador | Should | Informa ou altera a data prevista enquanto o serviço não foi concluído; data no passado é recusada (422). | RF10, RN22 |
| RF28 | Atribuição de técnico à OS | Administrador | Must | Atribui técnico ativo a uma OS ainda não concluída; técnico inativo é recusado. | RF20, RN23 |
| RF29 | Cancelamento de OS | Administrador | Should | Somente o administrador cancela uma OS pela rota de status, com motivo obrigatório; técnico recebe 403. | RF11, RN20 |

Table: Requisitos funcionais, prioridade (MoSCoW) e critérios de aceitação {#tbl-rf}

Fonte: Elaborado pelos autores (2026).


## Exemplo de requisito completamente especificado

A Tabela 2 mostra, para o RF25, a cadeia completa que a disciplina espera de um requisito bem definido: requisito, regra, requisito não funcional associado e critério de aceitação.

| Elemento | Especificação |
|---------------------|-------------------------------------------------------------------------------|
| Requisito funcional | RF25: o cliente deve poder aprovar ou recusar o orçamento enviado pelo técnico |
| Requisito não funcional | RNF03: somente o dono da OS pode responder; RNF04: no máximo 10 respostas por hora por origem |
| Regra de negócio | RN19: o orçamento só é enviado com valor registrado; a recusa cancela a OS sem custo (diagnóstico gratuito) |
| Critério de aceitação | Com a OS em AGUARDANDO_APROVACAO, a aprovação leva a EM_MANUTENCAO e grava orcamento_aprovado como verdadeiro; a recusa leva a CANCELADO e grava falso; qualquer outro status resulta em 422 |
| Teste | T22 (recusa e cancelamento sem custo) e T19 (ciclo completo com aprovação) |

Table: Exemplo de especificação completa do RF25 {#tbl-exemplo}

Fonte: Elaborado pelos autores (2026).

# Requisitos não funcionais

A Tabela 3 lista os 18 requisitos não funcionais, agrupados por categoria (segurança, confiabilidade, manutenibilidade, testabilidade, portabilidade, usabilidade, acessibilidade, desempenho e privacidade). A coluna de situação usa o vocabulário CONCLUÍDO e PARCIAL; os itens parciais têm limitação declarada na evidência ou no Apêndice F. Requisitos de capacidade e disponibilidade (por exemplo, número de usuários simultâneos ou percentual de disponibilidade) não existem no repositório e **não foram inventados**; se o grupo os desejar, devem ser definidos e medidos.

| ID | Categoria | Requisito | Evidência | Situação |
|---------|-------------------|-----------------------------|----------------------------|---------------|
| RNF01 | Segurança | Autenticação sem estado por JWT, com expiração configurável (padrão de 120 minutos). | SecurityConfig (política stateless); application.yml | CONCLUÍDO |
| RNF02 | Segurança | Senhas armazenadas com hash BCrypt; nunca em texto claro. | BCryptPasswordEncoder; coluna senha_hash | CONCLUÍDO |
| RNF03 | Segurança | Autorização por perfil verificada no servidor em cada operação. | SecurityConfig; @PreAuthorize nos controllers; OrdemServicoFlowIT.permissoesDeAcessoAOS | CONCLUÍDO |
| RNF04 | Segurança | Limitação de requisições por origem e rota; o cabeçalho X-Forwarded-For só é considerado quando a aplicação está configurada para confiar no proxy (DITEC_TRUST_PROXY). | RateLimitFilter; RateLimitIT (2 testes) | CONCLUÍDO |
| RNF05 | Segurança | Cabeçalhos de segurança na API: CSP restritiva, X-Frame-Options DENY, Referrer-Policy, Permissions-Policy e HSTS. | SecurityConfig (HSTS só tem efeito sob HTTPS) | CONCLUÍDO |
| RNF06 | Segurança | CORS restrito às origens configuradas, falhando na inicialização do perfil de produção quando não houver origem. | SecurityConfig; DITEC_ALLOWED_ORIGINS e FRONTEND_URL | PARCIAL |
| RNF07 | Segurança | A chave da IA fica somente no servidor e trafega em cabeçalho, nunca na URL, no front-end ou nos logs. | GeminiClient; GeminiClientTest; varredura de segredos | CONCLUÍDO |
| RNF08 | Confiabilidade | Validação de entrada e respostas de erro padronizadas (401, 403, 404, 409, 422, 502 e 500). | Bean Validation nos DTOs; GlobalExceptionHandler | CONCLUÍDO |
| RNF09 | Confiabilidade | Integridade dos dados por chaves, unicidade e domínios no banco (13 FK, 8 UNIQUE e 11 CHECK). | V1__schema.sql e V2__rastreamento_os.sql; testes de migração | CONCLUÍDO |
| RNF10 | Confiabilidade | O chatbot continua respondendo, por FAQ automática, quando a IA está indisponível, com pausa temporária das chamadas após falhas. | ChatService; ChatFallbackService; ChatGeminiIT | CONCLUÍDO |
| RNF11 | Manutenibilidade | Evolução controlada do esquema por migrações versionadas (Flyway). | db/migration (V1 e V2) | CONCLUÍDO |
| RNF12 | Manutenibilidade | Separação em camadas (controller, service, repository), DTOs e tratamento centralizado de exceções. | Estrutura de pacotes do backend | CONCLUÍDO |
| RNF13 | Testabilidade | Suíte automatizada com perfil de teste em H2 (modo MySQL) e IA simulada. | 52 métodos de teste; MockGeminiServer | CONCLUÍDO |
| RNF14 | Portabilidade | Implantação por contêiner e configuração por variáveis de ambiente. | Dockerfile; .env.example; deploy-pages.yml | PARCIAL |
| RNF15 | Usabilidade | Interface responsiva (celular e desktop). | meta viewport e media queries em styles.css; teste de interface do grupo (1280 e 375 px) | CONCLUÍDO |
| RNF16 | Acessibilidade | Atendimento automatizado às regras WCAG 2.1 A e AA e navegação por teclado. | axe-core: 0 violações em 22 cenários; teclado 10/10 (relatados pelo grupo) | PARCIAL |
| RNF17 | Desempenho | Resposta do chatbot com IA em menos de 2 s (meta do grupo); páginas com Lighthouse de desempenho ≥ 90. | benchmark-chat (média de 1.038 ms); Lighthouse: celular 98–100, desktop 82–89 | PARCIAL |
| RNF18 | Privacidade | Informar o uso de cookies e a política de privacidade, em conformidade com a LGPD. | Aviso de cookies e política em index.html; divergência N-07 (revisão técnica) | PARCIAL |

Table: Requisitos não funcionais e evidências {#tbl-rnf}

Fonte: Elaborado pelos autores (2026).


# Regras de negócio

As regras de negócio (RN) são as condições e restrições do domínio que o servidor aplica independentemente da interface. A Tabela 4 indica, para cada regra, onde ela é aplicada no código.

| ID | Regra de negócio | Onde é aplicada |
|--------|-------------------------------------------------------------|--------------------------------|
| RN01 | Atende-se somente bairros e municípios de uma lista fixa de 54 itens, comparados sem acento e sem diferenciar maiúsculas de minúsculas. | RegrasNegocioService.isBairroAtendido |
| RN02 | CEP atendido: 01000-000 a 05999-999, 06000-000 a 08499-999 e 09000-000 a 09999-999. | RegrasNegocioService.isCepAtendido; script.js |
| RN03 | Não há atendimento aos domingos. | RegrasNegocioService.isDiaAtendido |
| RN04 | Horários fixos: 07:00, 08:30, 10:00, 11:30, 13:00, 14:30, 16:00, 17:30 e 19:00. | RegrasNegocioService.HORARIOS_PERMITIDOS |
| RN05 | Só pode haver um agendamento ativo por horário (conflito resulta em 409). | AgendamentoService.criar e atualizar |
| RN06 | Agendamento só pode ser alterado ou cancelado nos status PENDENTE ou AGENDADO. | AgendamentoService; StatusAgendamento |
| RN07 | Aparelhos a gás têm prioridade (o nome do aparelho contém "gas"). | RegrasNegocioService.isAparelhoGas |
| RN08 | Cada atendimento recebe protocolo DITEC-AAAA-NNNNNN, idêntico no agendamento e na OS. | ProtocoloService |
| RN09 | A OS segue uma matriz de 19 transições entre 10 status; ENTREGUE e CANCELADO são terminais. | StatusOS.podeTransicionarPara |
| RN10 | CONCLUIDO só é alcançado pela finalização (nunca pela troca simples de status), a partir de EM_TESTES. | OrdemServicoService.atualizarStatus e finalizar |
| RN11 | Pagamento em Pix ou dinheiro recebe 5% de desconto; formas válidas: pix, dinheiro, cartão (crédito ou débito) e boleto. | OrdemServicoService.finalizar |
| RN12 | A garantia padrão é de 90 dias, gravada na finalização. | OrdemServicoService; coluna garantia_dias |
| RN13 | O rastreamento público exige protocolo e telefone (8 últimos dígitos), responde igualmente a protocolo inexistente e telefone divergente e não expõe dados sensíveis nem observações internas. | OrdemServicoService.rastrear; OrdemServicoMapper |
| RN14 | Avaliação: apenas do dono, de OS com serviço concluído, uma por OS, nota de 1 a 5. | OrdemServicoService.avaliar; UNIQUE e CHECK |
| RN15 | O técnico só atua em OS atribuída a ele; se a OS não tem técnico, ele a assume ao atuar. | OrdemServicoService (garantirTecnicoDaOSOuAdmin; assumirSeAindaSemTecnico) |
| RN16 | E-mail é único; senha com 8 a 100 caracteres (cadastro de cliente e de técnico). | RegisterRequest; TecnicoRequest; UNIQUE |
| RN17 | Limites de requisições por origem: login 10/5 min; cadastro 5/h; chat 20/5 min; agendamento 10/h; rastreamento 20/5 min; resposta ao orçamento 10/h; avaliação 10/h. | RateLimitFilter |
| RN18 | Sair de EM_DIAGNOSTICO (exceto cancelando) exige diagnóstico registrado. | OrdemServicoService.atualizarStatus |
| RN19 | Enviar o orçamento ao cliente (AGUARDANDO_APROVACAO) exige valor de orçamento registrado; a resposta do cliente é registrada no campo orcamento_aprovado. | OrdemServicoService.atualizarStatus e responderOrcamento |
| RN20 | Cancelar uma OS pela rota de status é permitido somente ao administrador, com motivo; o cliente também cancela ao recusar o orçamento, e o cancelamento do agendamento cancela a OS. | OrdemServicoService |
| RN21 | O atendimento só inicia quando o agendamento já foi confirmado (não está PENDENTE). | OrdemServicoService.atualizarStatus |
| RN22 | A previsão de conclusão não pode estar no passado e só pode ser alterada antes da conclusão do serviço. | OrdemServicoService.atualizarPrevisao |
| RN23 | Técnico inativo não recebe novas OS; a troca de técnico não é permitida em OS concluída, entregue ou cancelada. | OrdemServicoService.aplicarTecnico |
| RN24 | No cadastro, agendamento e OS de visitante criados nas últimas 24 h com o mesmo telefone são vinculados à nova conta. | AuthService.register |

Table: Regras de negócio {#tbl-rn}

Fonte: Elaborado pelos autores (2026).


# Restrições

As restrições do projeto, descritas com mais detalhe no Apêndice A, são: tecnologia (Java 21, Spring Boot 3.5, MySQL, JavaScript sem framework); prazo (cronograma semanal da disciplina); orçamento (serviços de plano gratuito, com limites de uso); legislação (Lei Geral de Proteção de Dados Pessoais); e integrações (Google Gemini API, GitHub Pages e Render).

# Histórias de usuário e critérios de aceitação

As histórias seguem o formato recomendado pela disciplina — "Como [perfil], quero [capacidade], para [benefício]" — e cada uma traz critérios de aceitação verificáveis (PROJETO E DESENVOLVIMENTO DE SOFTWARE, [s. d.]). A Tabela 5 relaciona as 20 histórias aos requisitos funcionais.

| ID | História de usuário | Critérios de aceitação | RF | Prior. |
|--------|-------------------------------|---------------------------------------------|--------|--------|
| HU01 | Como visitante, quero verificar se o meu CEP ou bairro é atendido, para saber se posso solicitar a visita. | • Dado um CEP ou bairro da área, o site informa que há atendimento.<br>• Dado um CEP ou bairro fora da área, o site informa que não há atendimento. | RF23 | Could |
| HU02 | Como visitante, quero escolher um horário livre e solicitar uma visita técnica sem criar conta, para ter o conserto agendado com pouco esforço. | • Dado um dia útil futuro, a grade mostra apenas os horários livres.<br>• Dados válidos geram um protocolo DITEC-AAAA-NNNNNN.<br>• Horário ocupado, domingo ou bairro não atendido são recusados com mensagem clara. | RF05, RF06 | Must |
| HU03 | Como visitante, quero consultar o andamento da minha ordem de serviço com o protocolo e o telefone, para saber em que etapa está o reparo. | • Protocolo e telefone corretos exibem a etapa e os eventos visíveis.<br>• Protocolo inexistente ou telefone divergente exibem a mesma mensagem, sem revelar qual dado falhou.<br>• Valores internos e observações internas não aparecem. | RF09 | Must |
| HU04 | Como visitante, quero tirar dúvidas com um assistente virtual, para obter respostas rápidas sobre serviços, garantia e agendamento. | • A pergunta recebe resposta, inclusive quando a IA está indisponível.<br>• O assistente não confirma agendamentos nem solicita dados de cartão. | RF15 | Could |
| HU05 | Como visitante, quero criar uma conta de cliente, para acompanhar meus agendamentos e ordens de serviço. | • Dados válidos criam a conta e iniciam a sessão.<br>• E-mail já cadastrado é recusado. | RF01 | Must |
| HU06 | Como cliente, quero entrar na minha conta e manter o endereço atualizado, para agilizar novos agendamentos. | • Credenciais válidas abrem a área do cliente; inválidas são recusadas.<br>• As alterações de perfil e endereço são persistidas. | RF02, RF03, RF04 | Should |
| HU07 | Como cliente, quero listar, alterar e cancelar meus agendamentos ainda não iniciados, para reagir a imprevistos. | • Somente os agendamentos próprios são listados.<br>• Agendamento em atendimento ou concluído não pode ser alterado nem cancelado. | RF07, RF08 | Should |
| HU08 | Como cliente, quero aprovar ou recusar o orçamento, para decidir antes de qualquer custo de reparo. | • Com a OS aguardando aprovação, a aprovação leva o reparo adiante.<br>• A recusa cancela a OS sem custo. | RF25 | Must |
| HU09 | Como cliente, quero consultar minhas ordens de serviço e o histórico de cada uma, para acompanhar todas as etapas do atendimento. | • Apenas as OS do cliente são exibidas.<br>• O histórico mostra somente eventos visíveis ao cliente. | RF10 | Must |
| HU10 | Como cliente, quero avaliar o atendimento concluído, para registrar a minha satisfação. | • A avaliação (nota de 1 a 5) só é aceita após a conclusão do serviço.<br>• Uma segunda avaliação da mesma OS é recusada. | RF13 | Should |
| HU11 | Como técnico, quero ver as ordens de serviço atribuídas a mim, para organizar o meu trabalho. | • A lista contém apenas as OS atribuídas ao técnico.<br>• O acesso a OS de outro técnico é negado. | RF10 | Must |
| HU12 | Como técnico, quero atualizar o status da OS seguindo o fluxo previsto, para manter o cliente informado. | • Apenas transições permitidas são aceitas.<br>• Cada mudança gera um evento no histórico. | RF11 | Must |
| HU13 | Como técnico, quero registrar o diagnóstico e o orçamento, para submeter o valor à aprovação do cliente. | • O diagnóstico é obrigatório para sair de EM_DIAGNOSTICO.<br>• O envio ao cliente exige o valor do orçamento. | RF24 | Must |
| HU14 | Como técnico, quero registrar observações e a previsão de conclusão, para manter o cliente informado e preservar anotações internas. | • Observações internas não aparecem ao cliente.<br>• Previsão no passado é recusada. | RF26, RF27 | Should |
| HU15 | Como técnico, quero finalizar a OS com valor, pagamento, peças e descrição, para registrar a conclusão e ativar a garantia. | • A finalização só ocorre com a OS em testes.<br>• Pix ou dinheiro recebem 5% de desconto.<br>• A garantia de 90 dias é registrada. | RF12 | Must |
| HU16 | Como administrador, quero confirmar agendamentos e atribuir técnico, para organizar a agenda e a equipe. | • Somente agendamentos pendentes são confirmados.<br>• Técnico inativo não pode receber OS. | RF18, RF28 | Must |
| HU17 | Como administrador, quero acompanhar o painel e filtrar todas as ordens de serviço, para supervisionar o atendimento. | • Os filtros por status, técnico, protocolo e cliente retornam os resultados esperados. | RF16, RF19 | Should |
| HU18 | Como administrador, quero cancelar uma OS informando o motivo, para registrar interrupções legítimas do atendimento. | • O cancelamento exige motivo.<br>• O técnico não consegue cancelar. | RF29 | Should |
| HU19 | Como administrador, quero gerir técnicos e serviços, para manter a equipe e o catálogo atualizados. | • É possível criar, editar e ativar ou inativar técnicos e serviços. | RF20, RF21 | Must |
| HU20 | Como administrador, quero consultar clientes e exportar relatórios em CSV, para analisar a operação fora do sistema. | • Os quatro relatórios são exportados em CSV.<br>• Apenas o administrador acessa. | RF17, RF22 | Could |

Table: Histórias de usuário e critérios de aceitação {#tbl-hu}

Fonte: Elaborado pelos autores (2026).


# Priorização

A priorização considerou o valor para o fluxo principal (do agendamento à entrega), as dependências entre requisitos e o risco de segurança. São *Must* os requisitos sem os quais o fluxo ponta a ponta não se completa: agendamento, rastreamento, consulta e atualização da OS, diagnóstico e orçamento, finalização, gestão de agendamentos e de técnicos, e atribuição de técnico. São *Should* os que melhoram a operação ou o controle sem bloquear o fluxo, e *Could* os desejáveis, como o assistente virtual, os relatórios CSV e a consulta de clientes.

A disciplina cita ainda a votação por pontos e a matriz valor × esforço como técnicas possíveis. Não há estimativas de esforço no repositório e elas não foram inventadas; por isso adotou-se somente o MoSCoW, acompanhado das dependências declaradas na Tabela 1. Caso o grupo estime o esforço de cada história, a matriz valor × esforço pode ser construída sobre os mesmos identificadores.

# Qualidade dos requisitos: ambiguidades e divergências encontradas

A verificação dos requisitos contra o código e a documentação revelou os pontos da Tabela 6, que exigem decisão ou harmonização do grupo.

| ID | Ponto | Onde | Tratamento proposto |
|-------|------------------------------------|---------------------|------------------------------------|
| AM1 | A documentação do enum StatusOS afirma que o cancelamento é possível em qualquer etapa anterior à conclusão, mas EM_TESTES não admite cancelamento (comportamento afirmado por teste) | StatusOS; OrdemServicoFlowIT | Ajustar o comentário ou a regra (RN09 e RN20) |
| AM2 | O modelo de IA padrão difere entre o relatório do grupo (gemini-2.5-flash) e a configuração (gemini-3.5-flash-lite, com alternativo gemini-2.5-flash-lite) | application.yml; relatório do grupo | Harmonizar o texto |
| AM3 | A política de privacidade afirma que os dados não são compartilhados com terceiros, mas o conteúdo do chat é enviado à Google e gravado em chat_log | index.html | Revisar o texto da política (RNF18) |
| AM4 | O RNF17 define a meta de resposta do chat (menos de 2 s) para a IA ativa, mas não para a resposta automática | Relatório do grupo | Declarar a meta também para o modo automático |
| AM5 | O vínculo de agendamento de visitante à conta por telefone, em 24 h, não verifica a posse do número (RN24) | AuthService | Decidir entre exigir o protocolo ou confirmar o telefone |

Table: Ambiguidades e divergências identificadas na verificação dos requisitos {#tbl-ambig}

Fonte: Elaborado pelos autores (2026), a partir da revisão técnica (EQUIPE DITEC, 2026c).
