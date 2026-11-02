-- V6: pagamentos (sinal, saldo, reembolso), avisos para a proprietária e prazo para pagar o sinal.

-- ---------------------------------------------------------------------------
-- Prazo, em horas após o aceite do orçamento, para o sinal ser pago (pendência P4).
-- NULL = sem prazo (nenhum cancelamento automático por falta de sinal).
-- ---------------------------------------------------------------------------
ALTER TABLE bakery_settings
    ADD COLUMN deposit_deadline_hours INT DEFAULT 48,
    ADD CONSTRAINT ck_bakery_settings_deposit_deadline
        CHECK (deposit_deadline_hours IS NULL OR deposit_deadline_hours BETWEEN 1 AND 720);

-- ---------------------------------------------------------------------------
-- Pagamentos. Nunca são apagados: correções são feitas por rejeição (com motivo)
-- ou por um novo registro (ex.: reembolso). Pertencem ao pedido, não ao orçamento.
-- ---------------------------------------------------------------------------
CREATE TABLE payment (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      BIGINT        NOT NULL REFERENCES cake_order (id),
    kind          VARCHAR(20)   NOT NULL,
    method        VARCHAR(10)   NOT NULL,
    amount        NUMERIC(12,2) NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    proof_file_id BIGINT        REFERENCES stored_file (id),
    submitted_by  VARCHAR(20)   NOT NULL,
    notes         VARCHAR(300),
    review_note   VARCHAR(300),
    reviewed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_payment_kind CHECK (kind IN ('DEPOSIT', 'BALANCE', 'REFUND')),
    CONSTRAINT ck_payment_method CHECK (method IN ('PIX', 'CASH')),
    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING_REVIEW', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_payment_submitted_by CHECK (submitted_by IN ('OWNER', 'CLIENT')),
    CONSTRAINT ck_payment_reviewed CHECK (status = 'PENDING_REVIEW' OR reviewed_at IS NOT NULL)
);

CREATE INDEX ix_payment_order ON payment (order_id);
CREATE INDEX ix_payment_status ON payment (status);
-- Um comprovante em análise por vez em cada pedido.
CREATE UNIQUE INDEX uk_payment_pending_per_order ON payment (order_id) WHERE status = 'PENDING_REVIEW';

CREATE TRIGGER trg_payment_no_delete
    BEFORE DELETE ON payment
    FOR EACH ROW EXECUTE FUNCTION prevent_history_modification();

-- Pagamento analisado não volta para análise nem muda de valor.
CREATE FUNCTION prevent_reviewed_payment_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.status <> 'PENDING_REVIEW' AND (NEW.status <> OLD.status OR NEW.amount <> OLD.amount
            OR NEW.kind <> OLD.kind OR NEW.order_id <> OLD.order_id) THEN
        RAISE EXCEPTION 'Pagamentos já analisados não podem ser alterados'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_payment_reviewed_frozen
    BEFORE UPDATE ON payment
    FOR EACH ROW EXECUTE FUNCTION prevent_reviewed_payment_change();

-- ---------------------------------------------------------------------------
-- Avisos para a proprietária (painel + e-mail). dedup_key único torna a criação
-- idempotente: o processo agendado pode rodar várias vezes sem duplicar avisos.
-- ---------------------------------------------------------------------------
CREATE TABLE notification (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type           VARCHAR(40)  NOT NULL,
    order_id       BIGINT       REFERENCES cake_order (id),
    message        VARCHAR(500) NOT NULL,
    dedup_key      VARCHAR(120) NOT NULL,
    email_status   VARCHAR(20)  NOT NULL,
    email_attempts INT          NOT NULL DEFAULT 0,
    email_sent_at  TIMESTAMPTZ,
    read_at        TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_notification_dedup UNIQUE (dedup_key),
    CONSTRAINT ck_notification_email_status CHECK (email_status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX ix_notification_unread ON notification (created_at) WHERE read_at IS NULL;
CREATE INDEX ix_notification_email_pending ON notification (created_at) WHERE email_status IN ('PENDING', 'FAILED');
