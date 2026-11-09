-- ============================================================
-- DITEC — V2: rastreamento completo da Ordem de Servico
--
-- Migracao INCREMENTAL (nao recria tabelas nem apaga dados):
--  1. Ciclo de vida da OS ampliado (10 status) — o antigo EM_ATENDIMENTO
--     e' convertido para EM_MANUTENCAO (mesmo significado: tecnico
--     trabalhando no reparo), inclusive no historico ja gravado.
--  2. Novos campos de acompanhamento: diagnostico, orcamento, previsao
--     de conclusao e data de entrega.
--  3. Historico (timeline_os) passa a registrar o tipo do evento, o
--     usuario responsavel (FK) e se o evento e' visivel ao cliente.
--
-- Reversao manual (se necessario): ver "Banco de Dados/database/README.md".
-- ============================================================

-- ---------- 1. ORDEM_SERVICO: novos campos ----------
ALTER TABLE ordem_servico
    ADD COLUMN diagnostico          TEXT          NULL AFTER descricao_problema,
    ADD COLUMN valor_orcamento      DECIMAL(10,2) NULL AFTER diagnostico,
    ADD COLUMN orcamento_aprovado   TINYINT(1)    NULL AFTER valor_orcamento,
    ADD COLUMN previsao_conclusao   DATE          NULL AFTER orcamento_aprovado,
    ADD COLUMN data_entrega         DATETIME      NULL AFTER data_conclusao;

-- ---------- 2. ORDEM_SERVICO: novo dominio de status ----------
ALTER TABLE ordem_servico DROP CHECK ck_os_status;

UPDATE ordem_servico SET status = 'EM_MANUTENCAO' WHERE status = 'EM_ATENDIMENTO';

ALTER TABLE ordem_servico
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'AGENDADO',
    ADD CONSTRAINT ck_os_status CHECK (status IN (
        'AGENDADO', 'EM_DIAGNOSTICO', 'AGUARDANDO_APROVACAO', 'AGUARDANDO_PECAS',
        'EM_MANUTENCAO', 'EM_TESTES', 'CONCLUIDO', 'AGUARDANDO_RETIRADA',
        'ENTREGUE', 'CANCELADO')),
    ADD CONSTRAINT ck_os_valor_orcamento CHECK (valor_orcamento IS NULL OR valor_orcamento >= 0),
    ADD CONSTRAINT ck_os_valor CHECK (valor IS NULL OR valor >= 0);

CREATE INDEX idx_os_criado_em ON ordem_servico (criado_em);

-- ---------- 3. TIMELINE_OS: tipo, responsavel e visibilidade ----------
ALTER TABLE timeline_os
    ADD COLUMN tipo             VARCHAR(20) NOT NULL DEFAULT 'STATUS' AFTER ordem_servico_id,
    ADD COLUMN usuario_id       BIGINT      NULL AFTER alterado_por,
    ADD COLUMN visivel_cliente  TINYINT(1)  NOT NULL DEFAULT 1 AFTER usuario_id,
    ADD CONSTRAINT fk_timeline_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_timeline_tipo CHECK (tipo IN (
        'ABERTURA', 'STATUS', 'DIAGNOSTICO', 'ORCAMENTO', 'OBSERVACAO', 'ATRIBUICAO', 'PREVISAO'));

UPDATE timeline_os SET status_anterior = 'EM_MANUTENCAO' WHERE status_anterior = 'EM_ATENDIMENTO';
UPDATE timeline_os SET status_novo = 'EM_MANUTENCAO' WHERE status_novo = 'EM_ATENDIMENTO';
UPDATE timeline_os SET tipo = 'ABERTURA' WHERE status_anterior IS NULL;

-- Ordenacao do historico por OS usa os dois campos juntos.
CREATE INDEX idx_timeline_os_data ON timeline_os (ordem_servico_id, data_hora);
