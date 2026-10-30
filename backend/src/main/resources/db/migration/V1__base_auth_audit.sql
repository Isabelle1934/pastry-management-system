-- V1: base do esquema — usuária administrativa, recuperação de senha, sessões,
-- auditoria imutável e trava de processos agendados.

-- ---------------------------------------------------------------------------
-- Função reutilizável: impede UPDATE/DELETE em tabelas de histórico.
-- Usada por audit_log e, nas próximas fases, por order_history.
-- ---------------------------------------------------------------------------
CREATE FUNCTION prevent_history_modification() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Registros de histórico são imutáveis (tabela %)', TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$;

-- ---------------------------------------------------------------------------
-- Usuária administrativa
-- ---------------------------------------------------------------------------
CREATE TABLE admin_user (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email                VARCHAR(254) NOT NULL,
    name                 VARCHAR(120) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
    password_changed_at  TIMESTAMPTZ  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_admin_user_email UNIQUE (email),
    CONSTRAINT ck_admin_user_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_admin_user_name_not_blank CHECK (length(trim(name)) > 0)
);

-- ---------------------------------------------------------------------------
-- Tokens de redefinição de senha (somente o hash SHA-256 é armazenado)
-- ---------------------------------------------------------------------------
CREATE TABLE password_reset_token (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_user_id BIGINT      NOT NULL REFERENCES admin_user (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_password_reset_token_hash_format CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_password_reset_token_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_password_reset_token_user ON password_reset_token (admin_user_id);

-- ---------------------------------------------------------------------------
-- Auditoria de ações administrativas e do sistema (somente inserção)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_type  VARCHAR(20) NOT NULL,
    actor_id    BIGINT,
    action      VARCHAR(80) NOT NULL,
    entity_type VARCHAR(60) NOT NULL,
    entity_id   VARCHAR(64),
    details     JSONB,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_audit_log_actor_type CHECK (actor_type IN ('OWNER', 'CLIENT', 'SYSTEM'))
);

CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_log_created_at ON audit_log (created_at);

CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_history_modification();

-- ---------------------------------------------------------------------------
-- Spring Session JDBC (esquema oficial para PostgreSQL)
-- ---------------------------------------------------------------------------
CREATE TABLE spring_session (
    primary_id            CHAR(36) NOT NULL,
    session_id            CHAR(36) NOT NULL,
    creation_time         BIGINT   NOT NULL,
    last_access_time      BIGINT   NOT NULL,
    max_inactive_interval INT      NOT NULL,
    expiry_time           BIGINT   NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- ShedLock: garante execução única dos processos agendados
-- ---------------------------------------------------------------------------
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
