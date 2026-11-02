-- V5: orçamentos personalizados com versões. Um orçamento enviado nunca é alterado:
-- mudanças geram nova versão e a anterior fica registrada (SUPERSEDED).

CREATE TABLE quote (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id       BIGINT        NOT NULL REFERENCES cake_order (id),
    version_number INT           NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    total          NUMERIC(12,2) NOT NULL DEFAULT 0,
    deposit_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes          VARCHAR(1000),
    validity_hours INT           NOT NULL,
    valid_until    TIMESTAMPTZ,
    sent_at        TIMESTAMPTZ,
    responded_at   TIMESTAMPTZ,
    decline_reason VARCHAR(300),
    expired_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_quote_order_version UNIQUE (order_id, version_number),
    CONSTRAINT ck_quote_status CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'SUPERSEDED')),
    CONSTRAINT ck_quote_amounts CHECK (total >= 0 AND deposit_amount >= 0 AND deposit_amount <= total),
    CONSTRAINT ck_quote_validity CHECK (validity_hours BETWEEN 1 AND 720),
    CONSTRAINT ck_quote_sent CHECK (status = 'DRAFT' OR (sent_at IS NOT NULL AND valid_until IS NOT NULL AND total > 0))
);

-- No máximo um orçamento vigente (enviado ou aceito) e um rascunho por pedido.
CREATE UNIQUE INDEX uk_quote_current_per_order ON quote (order_id) WHERE status IN ('SENT', 'ACCEPTED');
CREATE UNIQUE INDEX uk_quote_draft_per_order ON quote (order_id) WHERE status = 'DRAFT';
-- Busca de orçamentos a vencer (processo agendado da Fase 8).
CREATE INDEX ix_quote_sent_valid_until ON quote (valid_until) WHERE status = 'SENT';

CREATE TABLE quote_item (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quote_id    BIGINT        NOT NULL REFERENCES quote (id) ON DELETE CASCADE,
    position    SMALLINT      NOT NULL,
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(300),
    quantity    NUMERIC(10,2) NOT NULL,
    unit_price  NUMERIC(12,2) NOT NULL,
    subtotal    NUMERIC(12,2) NOT NULL,
    CONSTRAINT ck_quote_item_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_quote_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_quote_item_price CHECK (unit_price >= 0),
    CONSTRAINT ck_quote_item_subtotal CHECK (subtotal = round(quantity * unit_price, 2))
);

CREATE INDEX ix_quote_item_quote ON quote_item (quote_id, position);

-- ---------------------------------------------------------------------------
-- Itens e valores só podem mudar enquanto o orçamento é rascunho (garantia no banco).
-- ---------------------------------------------------------------------------
CREATE FUNCTION prevent_sent_quote_item_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    parent_status VARCHAR(20);
BEGIN
    SELECT status INTO parent_status FROM quote WHERE id = COALESCE(NEW.quote_id, OLD.quote_id);
    IF parent_status IS NOT NULL AND parent_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Itens de orçamento enviado não podem ser alterados'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_quote_item_draft_only
    BEFORE INSERT OR UPDATE OR DELETE ON quote_item
    FOR EACH ROW EXECUTE FUNCTION prevent_sent_quote_item_change();

CREATE FUNCTION prevent_sent_quote_amount_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.status <> 'DRAFT' AND (NEW.total <> OLD.total OR NEW.deposit_amount <> OLD.deposit_amount
            OR NEW.version_number <> OLD.version_number OR NEW.order_id <> OLD.order_id) THEN
        RAISE EXCEPTION 'Valores de orçamento enviado não podem ser alterados'
            USING ERRCODE = 'restrict_violation';
    END IF;
    IF OLD.status <> 'DRAFT' AND NEW.status = 'DRAFT' THEN
        RAISE EXCEPTION 'Orçamento enviado não pode voltar a ser rascunho'
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_quote_amounts_frozen
    BEFORE UPDATE ON quote
    FOR EACH ROW EXECUTE FUNCTION prevent_sent_quote_amount_change();

CREATE TRIGGER trg_quote_no_delete_after_send
    BEFORE DELETE ON quote
    FOR EACH ROW WHEN (OLD.status <> 'DRAFT')
    EXECUTE FUNCTION prevent_history_modification();
