-- V4: pedidos (solicitações de encomenda), escolhas de massa/recheio, histórico e arquivos enviados.

-- ---------------------------------------------------------------------------
-- Pedido. Quatro dimensões de estado ficam separadas (BUSINESS_RULES.md §2):
-- comercial, produção e pagamento aqui; orçamento na tabela quote (Fase 7).
-- O topo de bolo (opcional, 0..1 por pedido) fica nas colunas topper_*.
-- ---------------------------------------------------------------------------
CREATE TABLE cake_order (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tracking_code       VARCHAR(16)   NOT NULL,
    customer_name       VARCHAR(120)  NOT NULL,
    customer_whatsapp   VARCHAR(20)   NOT NULL,
    pickup_date         DATE          NOT NULL,
    pickup_time         TIME          NOT NULL,
    size_id             BIGINT        NOT NULL REFERENCES cake_size (id),
    size_name_snapshot  VARCHAR(80)   NOT NULL,
    decoration          VARCHAR(1000),
    cake_text           VARCHAR(100),
    notes               VARCHAR(1000),
    topper_requested    BOOLEAN       NOT NULL DEFAULT FALSE,
    topper_theme        VARCHAR(100),
    topper_name         VARCHAR(60),
    topper_age          SMALLINT,
    commercial_status   VARCHAR(30)   NOT NULL,
    production_status   VARCHAR(30)   NOT NULL,
    payment_status      VARCHAR(30)   NOT NULL,
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason VARCHAR(300),
    cancelled_by        VARCHAR(20),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_cake_order_tracking_code UNIQUE (tracking_code),
    CONSTRAINT ck_cake_order_tracking_code CHECK (tracking_code ~ '^[0-9A-HJKMNP-TV-Z]{16}$'),
    CONSTRAINT ck_cake_order_whatsapp CHECK (customer_whatsapp ~ '^\+55[0-9]{10,11}$'),
    CONSTRAINT ck_cake_order_topper CHECK (
        (topper_requested AND topper_theme IS NOT NULL)
        OR (NOT topper_requested AND topper_theme IS NULL AND topper_name IS NULL AND topper_age IS NULL)),
    CONSTRAINT ck_cake_order_topper_age CHECK (topper_age IS NULL OR topper_age BETWEEN 0 AND 120),
    CONSTRAINT ck_cake_order_commercial_status CHECK (commercial_status IN
        ('PENDING_REVIEW', 'QUOTE_SENT', 'AWAITING_DEPOSIT', 'CONFIRMED', 'COMPLETED',
         'REJECTED', 'DECLINED_BY_CLIENT', 'CANCELLED')),
    CONSTRAINT ck_cake_order_production_status CHECK (production_status IN
        ('NOT_STARTED', 'IN_PRODUCTION', 'READY', 'DELIVERED')),
    CONSTRAINT ck_cake_order_payment_status CHECK (payment_status IN
        ('NONE', 'DEPOSIT_UNDER_REVIEW', 'DEPOSIT_PAID', 'PAID')),
    CONSTRAINT ck_cake_order_cancelled_by CHECK (cancelled_by IS NULL OR cancelled_by IN ('OWNER', 'CLIENT', 'SYSTEM')),
    CONSTRAINT ck_cake_order_cancellation CHECK (
        (commercial_status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL)
        OR (commercial_status <> 'CANCELLED' AND cancelled_at IS NULL))
);

CREATE INDEX ix_cake_order_status_pickup ON cake_order (commercial_status, pickup_date);
CREATE INDEX ix_cake_order_pickup ON cake_order (pickup_date, pickup_time);
CREATE INDEX ix_cake_order_created_at ON cake_order (created_at);

-- ---------------------------------------------------------------------------
-- Massas e recheios escolhidos. O nome é guardado (snapshot) para o pedido
-- continuar legível mesmo que o item seja renomeado ou desativado.
-- ---------------------------------------------------------------------------
CREATE TABLE order_flavor (
    order_id      BIGINT      NOT NULL REFERENCES cake_order (id) ON DELETE CASCADE,
    flavor_id     BIGINT      NOT NULL REFERENCES cake_flavor (id),
    name_snapshot VARCHAR(80) NOT NULL,
    position      SMALLINT    NOT NULL,
    CONSTRAINT pk_order_flavor PRIMARY KEY (order_id, flavor_id)
);

CREATE TABLE order_filling (
    order_id      BIGINT      NOT NULL REFERENCES cake_order (id) ON DELETE CASCADE,
    filling_id    BIGINT      NOT NULL REFERENCES cake_filling (id),
    name_snapshot VARCHAR(80) NOT NULL,
    position      SMALLINT    NOT NULL,
    CONSTRAINT pk_order_filling PRIMARY KEY (order_id, filling_id)
);

-- ---------------------------------------------------------------------------
-- Histórico do pedido (somente inserção — mesma proteção do audit_log)
-- ---------------------------------------------------------------------------
CREATE TABLE order_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES cake_order (id),
    event_type  VARCHAR(40)  NOT NULL,
    actor       VARCHAR(20)  NOT NULL,
    description VARCHAR(300) NOT NULL,
    details     JSONB,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_order_history_actor CHECK (actor IN ('OWNER', 'CLIENT', 'SYSTEM'))
);

CREATE INDEX ix_order_history_order ON order_history (order_id, created_at);

CREATE TRIGGER trg_order_history_immutable
    BEFORE UPDATE OR DELETE ON order_history
    FOR EACH ROW EXECUTE FUNCTION prevent_history_modification();

-- ---------------------------------------------------------------------------
-- Arquivos enviados (fotos de referência e, na Fase 8, comprovantes).
-- O arquivo fica em disco com nome aleatório (storage_key); aqui só metadados.
-- ---------------------------------------------------------------------------
CREATE TABLE stored_file (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    storage_key   VARCHAR(80)  NOT NULL,
    order_id      BIGINT       NOT NULL REFERENCES cake_order (id),
    purpose       VARCHAR(30)  NOT NULL,
    original_name VARCHAR(120) NOT NULL,
    content_type  VARCHAR(50)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        VARCHAR(64)  NOT NULL,
    uploaded_by   VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT uk_stored_file_key UNIQUE (storage_key),
    CONSTRAINT ck_stored_file_purpose CHECK (purpose IN ('ORDER_REFERENCE', 'TOPPER_REFERENCE', 'PAYMENT_PROOF')),
    CONSTRAINT ck_stored_file_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp', 'application/pdf')),
    CONSTRAINT ck_stored_file_size CHECK (size_bytes > 0),
    CONSTRAINT ck_stored_file_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_stored_file_uploaded_by CHECK (uploaded_by IN ('OWNER', 'CLIENT'))
);

CREATE INDEX ix_stored_file_order ON stored_file (order_id);

-- ---------------------------------------------------------------------------
-- Reservas de vaga (V3) passam a referenciar o pedido.
-- ---------------------------------------------------------------------------
ALTER TABLE slot_reservation
    ADD CONSTRAINT fk_slot_reservation_order FOREIGN KEY (order_id) REFERENCES cake_order (id);
