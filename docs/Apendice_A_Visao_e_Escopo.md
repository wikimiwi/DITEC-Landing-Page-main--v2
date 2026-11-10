---
title: "Documento de visão e escopo do sistema DITEC Assistência Técnica"
---


# Introdução

## Finalidade do documento

Este documento reúne a visão e o escopo do sistema DITEC Assistência Técnica, primeiro dos seis entregáveis sugeridos pela disciplina (PROJETO E DESENVOLVIMENTO DE SOFTWARE, [s. d.]). Ele responde às perguntas que a disciplina associa ao entendimento do problema: quem tem o problema, quem usará o sistema, qual processo precisa melhorar, quais são as dores, os riscos e as oportunidades, e quais são os objetivos e os critérios de sucesso.

## Fontes e limites da reconstrução

O repositório do projeto não contém documento de visão anterior a este. O documento segue a organização em elementos pré-textuais, textuais e pós-textuais da NBR 14724 (ASSOCIAÇÃO BRASILEIRA DE NORMAS TÉCNICAS, 2024). O conteúdo foi reconstruído a posteriori a partir de quatro fontes: o código-fonte na versão e58a3d2, de 9 de novembro de 2026 (EQUIPE DITEC, 2026a); os arquivos de apresentação do projeto (README), no mesmo repositório, e o relatório de correções do grupo (EQUIPE DITEC, 2026b); a revisão técnica do código atualizado (EQUIPE DITEC, 2026c); e os resultados dos testes e das medições de qualidade executados pelo grupo.

Como técnica de levantamento, empregou-se a análise documental e a leitura do código. Entrevistas, observação do processo real e questionários, citados pela disciplina como técnicas possíveis, não deixaram registro no repositório. Os trechos que dependem de confirmação do grupo trazem a indicação "proposto".

# Entendimento do problema

## Quem tem o problema e quem usa o sistema

O problema afeta duas pontas do atendimento. De um lado, o cliente que precisa consertar um eletrodoméstico (geladeira, máquina de lavar, fogão, micro-ondas ou condicionador de ar) e não sabe quando o técnico virá nem em que etapa está o reparo. De outro, a empresa, que precisa organizar pedidos de visita, agenda e equipe técnica, e manter o histórico de cada atendimento. O repositório apresenta a DITEC como empresa que atende a cidade de São Paulo e municípios da Grande São Paulo, com técnicos certificados e garantia de 90 dias.

Usam o sistema quatro perfis: o visitante (sem conta), o cliente, o técnico e o administrador. A Tabela 1 resume as partes interessadas.

| Parte interessada | Papel | Interesse no sistema |
|------------------|---------------------|-------------------------------------------------------------|
| Visitante | Pessoa sem conta | Conhecer os serviços, verificar se a região é atendida, agendar visita, rastrear a OS e conversar com o assistente |
| Cliente | Usuário com perfil CLIENTE | Acompanhar agendamentos e OS, aprovar ou recusar orçamentos e avaliar o atendimento |
| Técnico | Usuário com perfil TECNICO | Consultar OS atribuídas, registrar diagnóstico e orçamento, atualizar o status e finalizar o atendimento |
| Administrador | Usuário com perfil ADMINISTRADOR | Confirmar agendamentos, atribuir técnicos, gerir serviços e equipe, acompanhar indicadores e exportar relatórios |
| Equipe de desenvolvimento | Grupo da disciplina | Entregar o sistema e a documentação nos prazos do cronograma |
| Docente e avaliação | Avaliador | Verificar clareza, rastreabilidade, coerência dos modelos, qualidade técnica, colaboração e evolução a partir do feedback |
| Google (Gemini API) | Serviço externo | Fornecer a inferência do assistente virtual |

Table: Partes interessadas do projeto {#tbl-partes}

Fonte: Elaborado pelos autores (2026), a partir dos perfis do sistema (TipoUsuario) e do material da disciplina.

## Processo atual e dores

Proposto — confirmar com o grupo. Em uma assistência técnica que opera por telefone e mensagens, as informações do atendimento ficam dispersas. Dessa dispersão decorrem quatro dores: conflitos de horário na agenda; falta de rastreabilidade do serviço; dificuldade do cliente em saber a etapa do conserto e o valor a aprovar; e ausência de um registro padronizado do diagnóstico, do orçamento, das peças e da garantia. O repositório declara como objetivo centralizar as informações de clientes, serviços, técnicos e ordens de serviço.

## Riscos e oportunidades

Entre os riscos do negócio estão o atendimento fora da área coberta, a sobreposição de horários, a cobrança sem aprovação prévia e a exposição de dados do cliente no acompanhamento público. As oportunidades identificadas são o agendamento online com protocolo, o rastreamento sem necessidade de contato manual, a aprovação de orçamento pelo próprio cliente e o assistente virtual disponível a qualquer hora. Os riscos técnicos do projeto são tratados no registro de riscos (Apêndice D).

## Objetivos e critérios de sucesso

O **objetivo geral** é disponibilizar um sistema web que permita agendar visitas, rastrear ordens de serviço e gerenciar clientes, técnicos, serviços e relatórios, integrando front-end, API e banco de dados. Os **objetivos específicos**, todos com correspondência no código, são:

a) permitir que visitantes e clientes solicitem agendamentos e consultem a disponibilidade de horários;
b) gerar um protocolo por atendimento e permitir o rastreamento público sem expor dados sensíveis;
c) oferecer áreas autenticadas para cliente, técnico e administrador, com controle de acesso por perfil;
d) registrar o ciclo completo da ordem de serviço, do agendamento à entrega, com diagnóstico, orçamento, previsão, pagamento, garantia e avaliação;
e) aplicar as regras de negócio no servidor, de forma que não possam ser contornadas por chamadas diretas à API;
f) disponibilizar um assistente virtual que continue respondendo quando a inteligência artificial estiver indisponível;
g) proteger a aplicação com autenticação, autorização, validação de entrada e limitação de requisições.

Os critérios de sucesso, propostos para validação do grupo, estão na seção 6.

# Visão do produto

## Declaração de posicionamento

Para clientes e equipes de uma assistência técnica de eletrodomésticos que precisam organizar visitas e acompanhar reparos, o DITEC é uma plataforma web que concentra agendamento, ordem de serviço e relacionamento com o cliente. Diferentemente do atendimento exclusivamente telefônico, o sistema oferece protocolo de rastreamento, histórico de cada etapa, aprovação de orçamento pelo cliente e regras de negócio verificadas no servidor.

## Características principais

O sistema é composto por um front-end estático (HTML, CSS e JavaScript), uma API REST em Java com Spring Boot e um banco de dados MySQL, descritos no Apêndice C. As características principais são:

a) agendamento com grade fixa de nove horários, de segunda a sábado, com verificação de bairro, de conflito e de prioridade para aparelhos a gás;
b) ordem de serviço com dez status, histórico (linha do tempo) com eventos visíveis ou internos, diagnóstico, orçamento, previsão de conclusão, finalização, garantia de 90 dias e desconto de 5% em Pix ou dinheiro;
c) rastreamento público por protocolo e telefone;
d) painéis por perfil (cliente, técnico e administrador), com relatórios em CSV;
e) assistente virtual com inteligência artificial e resposta automática de reserva.

# Escopo

## Dentro do escopo

Está no escopo o conjunto de 29 requisitos funcionais descrito no Apêndice B, que abrange: agendamento com ou sem conta; rastreamento público; cadastro e autenticação por perfil; ciclo completo da ordem de serviço (status, diagnóstico, orçamento, observações, previsão, finalização, avaliação); gestão administrativa de agendamentos, ordens de serviço, técnicos, serviços e clientes; relatórios em CSV; e assistente virtual.

## Fora do escopo

Não foram encontrados no código, e portanto não integram esta versão: recuperação de senha; notificações automáticas ao cliente; pagamento online (a forma de pagamento é apenas registrada); emissão de nota fiscal eletrônica, declarada fora do escopo pelo grupo; aplicativo móvel nativo; e limitação de requisições distribuída entre instâncias. Esses itens aparecem como candidatos a versões futuras no Apêndice D.

## Premissas

a) A área de atendimento é a definida em regra de negócio: 54 bairros e municípios e três faixas de CEP.
b) O atendimento ocorre de segunda a sábado, nos nove horários da grade.
c) O cliente informa um telefone de contato válido, usado também para o rastreamento.
d) O serviço de inteligência artificial pode ficar indisponível, e o sistema deve continuar funcional nesse caso.

## Restrições

a) **Tecnologia:** Java 21, Spring Boot 3.5, MySQL com migrações Flyway, front-end em JavaScript sem framework.
b) **Prazo:** o cronograma da disciplina, com entregas semanais até 2 de novembro de 2026.
c) **Orçamento:** o projeto utiliza serviços de plano gratuito (hospedagem e limite de uso da inteligência artificial), o que impõe limites de requisições.
d) **Legislação:** tratamento de dados pessoais sujeito à Lei Geral de Proteção de Dados Pessoais (LGPD); a política de privacidade do site requer revisão (Apêndice D, risco R06).
e) **Integrações:** Google Gemini API, GitHub Pages e Render.

# Partes interessadas, responsabilidades e comunicação

A norma ISO/IEC/IEEE 29148 recomenda que as partes interessadas e as suas necessidades sejam identificadas antes da especificação de requisitos (ISO/IEC/IEEE, 2018). As responsabilidades por perfil estão na Tabela 1. Segundo o grupo, Henrique de Sousa Cunha e Pedro Cainan Rodrigues de Paula respondem pelo desenvolvimento, e Cauã De Lucca Uema e Jesús Alejandro Romero Rivero, pelos testes; a divisão não está registrada no repositório (Apêndice D). Os canais de comunicação não deixaram registro no repositório. O grupo tem quatro integrantes (folha de rosto). O histórico do repositório registra três identidades de autoria de commits, que o grupo deve conciliar com essa lista (Apêndice D).

# Entregas e critérios de sucesso {#sec-sucesso}

As entregas previstas no cronograma da disciplina e o local, neste conjunto de documentos, em que cada uma é tratada estão na Tabela 2.

| Entrega do cronograma | Prevista para | Tratada em |
|-----------------------|-----------------|------------------------------------------------------------|
| E1 Proposta | 10/08/2026 | Apêndice A (este documento) |
| E2 Requisitos e casos de uso | 24/08/2026 | Apêndices B e C |
| A1 a A3 Visão, requisitos e diagramas UML | 07/09/2026 | Apêndices A, B e C |
| E3 Modelagem | 14/09/2026 | Apêndice C |
| E4, A4 e A5 Protótipo das telas e modelo do banco de dados | 21/09/2026 | Apêndice C |
| E5 e A6 MVP e sua documentação | 28/09/2026 | Apêndice E |
| A7 Código funcionando | 26/10/2026 | Apêndice E |
| E6 Testes | 02/11/2026 | Apêndice F |

Table: Entregas do cronograma e apêndices correspondentes {#tbl-entregas}

Fonte: Elaborado pelos autores (2026), com base no cronograma da disciplina.

Os critérios de sucesso do produto, propostos para validação do grupo, são os seguintes:

a) o fluxo completo (agendamento, confirmação, diagnóstico, orçamento, manutenção, testes, finalização e entrega) pode ser percorrido sem violar as regras de negócio;
b) o rastreamento público não expõe dados sensíveis nem permite descobrir quais protocolos existem;
c) a suíte automatizada de 52 testes executa sem falhas;
d) as telas não apresentam violações automatizadas de acessibilidade nos cenários testados;
e) o assistente virtual responde em menos de dois segundos com a inteligência artificial ativa e continua respondendo na indisponibilidade dela;
f) os requisitos podem ser rastreados até modelo, código e teste (Apêndice F).
