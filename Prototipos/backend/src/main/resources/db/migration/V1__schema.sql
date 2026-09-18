-- ============================================================
-- DITEC Assistencia Tecnica — schema inicial (MySQL 8)
-- Gerado a partir do DRS (secao 7 do prompt mestre de finalizacao).
-- Convencao: nomes de tabela/coluna em snake_case, enums como VARCHAR
-- + CHECK (em vez de ENUM nativo do MySQL) para maxima compatibilidade
-- e legibilidade nas ferramentas de administracao de banco.
-- ============================================================

SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- USUARIO — autenticacao/identidade (CLIENTE, ADMINISTRADOR, TECNICO)
-- ------------------------------------------------------------
CREATE TABLE usuario (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome            VARCHAR(150) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    senha_hash      VARCHAR(255) NOT NULL,
    telefone        VARCHAR(20),
    tipo            VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    data_cadastro   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ultimo_acesso   DATETIME NULL,
    CONSTRAINT uq_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_tipo CHECK (tipo IN ('CLIENTE', 'ADMINISTRADOR', 'TECNICO')),
    CONSTRAINT ck_usuario_status CHECK (status IN ('ATIVO', 'INATIVO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_usuario_tipo ON usuario (tipo);

-- ------------------------------------------------------------
-- CLIENTE — dados de endereco de um usuario tipo CLIENTE
-- ------------------------------------------------------------
CREATE TABLE cliente (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id    BIGINT NOT NULL,
    endereco      VARCHAR(255),
    bairro        VARCHAR(100),
    cidade        VARCHAR(100),
    uf            CHAR(2),
    cep           VARCHAR(9),
    complemento   VARCHAR(100),
    CONSTRAINT uq_cliente_usuario UNIQUE (usuario_id),
    CONSTRAINT fk_cliente_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_cliente_bairro ON cliente (bairro);

-- ------------------------------------------------------------
-- TECNICO — tecnico de campo (ligado a um usuario para permitir login)
-- ------------------------------------------------------------
CREATE TABLE tecnico (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id      BIGINT NOT NULL,
    nome            VARCHAR(150) NOT NULL,
    especialidade   VARCHAR(100),
    certificacao    VARCHAR(100),
    telefone        VARCHAR(20),
    email           VARCHAR(150) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    CONSTRAINT uq_tecnico_usuario UNIQUE (usuario_id),
    CONSTRAINT uq_tecnico_email UNIQUE (email),
    CONSTRAINT fk_tecnico_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_tecnico_status CHECK (status IN ('ATIVO', 'INATIVO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- SERVICO — catalogo de servicos oferecidos
-- ------------------------------------------------------------
CREATE TABLE servico (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome        VARCHAR(150) NOT NULL,
    descricao   VARCHAR(500),
    valor_base  DECIMAL(10, 2),
    status      VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    CONSTRAINT ck_servico_status CHECK (status IN ('ATIVO', 'INATIVO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- AGENDAMENTO — pedido de visita tecnica
-- cliente_id e' opcional (agendamento feito sem conta / visitante);
-- nesse caso nome_contato/telefone_contato guardam os dados informados.
-- ------------------------------------------------------------
CREATE TABLE agendamento (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    protocolo            VARCHAR(20),
    cliente_id           BIGINT NULL,
    nome_contato         VARCHAR(150),
    telefone_contato     VARCHAR(20),
    tecnico_id           BIGINT NULL,
    servico_id           BIGINT NULL,
    data_hora            DATETIME NOT NULL,
    tipo_aparelho        VARCHAR(100) NOT NULL,
    descricao_problema   VARCHAR(500),
    bairro               VARCHAR(100) NOT NULL,
    endereco             VARCHAR(255) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    prioridade_gas       TINYINT(1) NOT NULL DEFAULT 0,
    criado_em            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_agendamento_protocolo UNIQUE (protocolo),
    CONSTRAINT fk_agendamento_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id) ON DELETE SET NULL,
    CONSTRAINT fk_agendamento_tecnico FOREIGN KEY (tecnico_id) REFERENCES tecnico (id) ON DELETE SET NULL,
    CONSTRAINT fk_agendamento_servico FOREIGN KEY (servico_id) REFERENCES servico (id) ON DELETE SET NULL,
    CONSTRAINT ck_agendamento_status CHECK (status IN ('PENDENTE', 'AGENDADO', 'EM_ATENDIMENTO', 'CONCLUIDO', 'CANCELADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_agendamento_data_hora ON agendamento (data_hora);
CREATE INDEX idx_agendamento_status ON agendamento (status);
CREATE INDEX idx_agendamento_cliente ON agendamento (cliente_id);
CREATE INDEX idx_agendamento_telefone_contato ON agendamento (telefone_contato);

-- ------------------------------------------------------------
-- ORDEM_SERVICO — rastreamento real, 1-para-1 com agendamento
-- (mesmo protocolo dos dois, ver ProtocoloService no backend)
-- ------------------------------------------------------------
CREATE TABLE ordem_servico (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    agendamento_id        BIGINT NOT NULL,
    cliente_id            BIGINT NULL,
    tecnico_id            BIGINT NULL,
    protocolo             VARCHAR(20) NOT NULL,
    descricao_problema    VARCHAR(500),
    status                VARCHAR(20) NOT NULL DEFAULT 'AGENDADO',
    valor                 DECIMAL(10, 2),
    forma_pagamento       VARCHAR(30),
    desconto              DECIMAL(5, 2) DEFAULT 0,
    pecas_utilizadas      TEXT,
    descricao_final       TEXT,
    data_inicio           DATETIME NULL,
    data_conclusao        DATETIME NULL,
    garantia_dias         INT DEFAULT 90,
    nota_fiscal_numero    VARCHAR(50),
    criado_em             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_os_agendamento UNIQUE (agendamento_id),
    CONSTRAINT uq_os_protocolo UNIQUE (protocolo),
    CONSTRAINT fk_os_agendamento FOREIGN KEY (agendamento_id) REFERENCES agendamento (id) ON DELETE CASCADE,
    CONSTRAINT fk_os_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id) ON DELETE SET NULL,
    CONSTRAINT fk_os_tecnico FOREIGN KEY (tecnico_id) REFERENCES tecnico (id) ON DELETE SET NULL,
    CONSTRAINT ck_os_status CHECK (status IN ('AGENDADO', 'EM_ATENDIMENTO', 'CONCLUIDO', 'CANCELADO')),
    CONSTRAINT ck_os_garantia CHECK (garantia_dias >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_os_status ON ordem_servico (status);
CREATE INDEX idx_os_cliente ON ordem_servico (cliente_id);
CREATE INDEX idx_os_tecnico ON ordem_servico (tecnico_id);

-- ------------------------------------------------------------
-- TIMELINE_OS — historico de mudancas de status de uma OS
-- ------------------------------------------------------------
CREATE TABLE timeline_os (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    ordem_servico_id   BIGINT NOT NULL,
    status_anterior    VARCHAR(30),
    status_novo        VARCHAR(30) NOT NULL,
    observacao         VARCHAR(500),
    alterado_por       VARCHAR(150) NOT NULL,
    data_hora          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_timeline_os FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_timeline_os_id ON timeline_os (ordem_servico_id);

-- ------------------------------------------------------------
-- AVALIACAO — nota (1-5) e comentario do cliente sobre a OS concluida
-- ------------------------------------------------------------
CREATE TABLE avaliacao (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    ordem_servico_id   BIGINT NOT NULL,
    cliente_id         BIGINT NULL,
    nota               TINYINT NOT NULL,
    comentario         VARCHAR(500),
    criado_em          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_avaliacao_os UNIQUE (ordem_servico_id),
    CONSTRAINT fk_avaliacao_os FOREIGN KEY (ordem_servico_id) REFERENCES ordem_servico (id) ON DELETE CASCADE,
    CONSTRAINT fk_avaliacao_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id) ON DELETE SET NULL,
    CONSTRAINT ck_avaliacao_nota CHECK (nota BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------
-- CHAT_LOG — historico de interacoes do chatbot (UC010/011/013)
-- ------------------------------------------------------------
CREATE TABLE chat_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    cliente_id   BIGINT NULL,
    sessao_id    VARCHAR(64) NOT NULL,
    pergunta     TEXT NOT NULL,
    resposta     TEXT NOT NULL,
    data_hora    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chatlog_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_chatlog_sessao ON chat_log (sessao_id);
CREATE INDEX idx_chatlog_data ON chat_log (data_hora);
