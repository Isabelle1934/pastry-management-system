-- Fase 10: despesas e categorias. Despesas nunca são apagadas fisicamente (exclusão lógica + auditoria).

CREATE TABLE expense_category (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(60)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_expense_category_name CHECK (length(trim(name)) > 0)
);
CREATE UNIQUE INDEX uk_expense_category_name ON expense_category (lower(name));

-- Categorias iniciais genéricas (podem ser renomeadas ou desativadas no painel).
INSERT INTO expense_category (name, created_at, updated_at) VALUES
    ('Ingredientes', now(), now()),
    ('Embalagens', now(), now()),
    ('Equipamentos e utensílios', now(), now()),
    ('Outros', now(), now());

CREATE TABLE expense (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    description  VARCHAR(200)  NOT NULL,
    amount       NUMERIC(12,2) NOT NULL,
    expense_date DATE          NOT NULL,
    category_id  BIGINT        NOT NULL REFERENCES expense_category (id),
    order_id     BIGINT        REFERENCES cake_order (id),
    notes        VARCHAR(300),
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    version      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_expense_description CHECK (length(trim(description)) > 0),
    CONSTRAINT ck_expense_amount CHECK (amount > 0)
);
CREATE INDEX ix_expense_date ON expense (expense_date) WHERE deleted_at IS NULL;
CREATE INDEX ix_expense_order ON expense (order_id) WHERE order_id IS NOT NULL;

CREATE TRIGGER trg_expense_no_delete
    BEFORE DELETE ON expense
    FOR EACH ROW EXECUTE FUNCTION prevent_history_modification();

-- Recebimentos por data de confirmação (resumo financeiro).
CREATE INDEX ix_payment_reviewed_at ON payment (reviewed_at) WHERE status = 'CONFIRMED';
