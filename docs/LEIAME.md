# Documentação do projeto DITEC Assistência Técnica

Versão do código documentado: commit `e58a3d2` (9 de novembro de 2026). Nada desta pasta foi enviado ao GitHub nem alterou o histórico do repositório.

## Documentos

| Arquivo | Conteúdo | Entregável da disciplina |
|---|---|---|
| `DITEC_Relatorio_Consolidado.pdf` (e `.md`) | Relatório principal (seções 1 a 11), referências e os seis apêndices em um único documento | Todos |
| `Apendice_A_Visao_e_Escopo` | Documento de visão e escopo | 1 |
| `Apendice_B_Requisitos_e_Historias` | Requisitos funcionais e não funcionais, regras de negócio e histórias de usuário | 2 |
| `Apendice_C_Modelagem_UML_e_ER` | Diagramas UML, modelo entidade-relacionamento e dicionário de dados | 3 |
| `Apendice_D_Backlog_e_Gestao` | Backlog priorizado, quadro Kanban reconstituído, riscos e evidências de gestão | 4 |
| `Apendice_E_Codigo_BD_e_Execucao` | Estrutura do código, banco de dados e instruções de execução | 5 |
| `Apendice_F_Plano_e_Relatorio_de_Testes` | Plano e relatório de testes, rastreabilidade e roteiro de demonstração | 6 |

Cada apêndice também existe como documento independente (PDF e Markdown).

## Pastas de apoio

- `img/` e `shots/`: imagens usadas nos documentos (os arquivos `.md` apontam para elas).
- `diagramas/`: arquivos-fonte dos diagramas. Os `.dot` são desenhados com Graphviz (`dot -Tpng arquivo.dot -o arquivo.png`); os `.mmd` com o Mermaid CLI (`mmdc -i arquivo.mmd -o arquivo.png -c mermaid.json`). `gera_classes.py` converte `classes.mmd` em `classes.dot`.
- `evidencias/`: arquivos de resultado das verificações independentes e os scripts de apoio mantidos (ver o Apêndice F). Os scripts de banco exigem um servidor MariaDB local e uma cópia do repositório.
- `patches/`: patch de documentação proposto (README, SECURITY e reversão da V2), **ainda não aplicado**; depende de autorização do grupo (pendência P04).

## Informações que o repositório não registra

Instituição, curso, cidade, integrantes, professor e a divisão de papéis (desenvolvimento: Henrique e Pedro; testes: Cauã e Jesús) constam nos documentos. Os documentos declaram, nos pontos em que aparecem, que o repositório não registra: reuniões e canais de comunicação, datas e divisão interna da execução dos testes, *feedback* recebido, o procedimento de criação do administrador em produção e a configuração do Render. Se o grupo tiver esses registros, podem ser acrescentados nos Apêndices D, E e F.

## Limites declarados

A documentação foi reconstruída a posteriori. A execução de `mvn test` não foi reproduzida, a configuração de produção não foi verificada, as telas autenticadas não foram capturadas e os roteiros de teste manual e a demonstração não foram executados. O Apêndice F separa o que é relatado pelo grupo, o que foi verificado de forma independente e o que está pendente.
