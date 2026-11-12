-- V8: catálogo dinâmico de produtos (categorias, produtos, fotos), itens do catálogo nos pedidos,
-- modalidade de atendimento (retirada ou delivery) e configurações de atendimento.
-- Nada é apagado: pedidos existentes continuam como "bolo personalizado" com retirada na confeitaria.

-- ---------------------------------------------------------------------------
-- Categorias criadas pela proprietária (nenhuma é inserida aqui: nada de dados fictícios).
-- ---------------------------------------------------------------------------
CREATE TABLE product_category (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(60)  NOT NULL,
    description   VARCHAR(300),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_category_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_product_category_display_order CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uk_product_category_name ON product_category (lower(name));

-- ---------------------------------------------------------------------------
-- Produtos. Situação: ACTIVE (aparece e pode ser pedido), UNAVAILABLE (aparece como
-- "indisponível no momento"), INACTIVE (fora do catálogo público). Nunca são apagados.
-- price NULL = preço sob orçamento. available_weekdays: máscara de bits ISO
-- (bit 0 = segunda ... bit 6 = domingo); NULL = todos os dias.
-- ---------------------------------------------------------------------------
CREATE TABLE product (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id        BIGINT        NOT NULL REFERENCES product_category (id),
    name               VARCHAR(100)  NOT NULL,
    description        VARCHAR(1000),
    price              NUMERIC(10,2),
    unit_label         VARCHAR(40),
    min_quantity       INT           NOT NULL DEFAULT 1,
    status             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    orderable          BOOLEAN       NOT NULL DEFAULT TRUE,
    seasonal           BOOLEAN       NOT NULL DEFAULT FALSE,
    available_from     DATE,
    available_until    DATE,
    available_weekdays SMALLINT,
    pickup_allowed     BOOLEAN       NOT NULL DEFAULT TRUE,
    delivery_allowed   BOOLEAN       NOT NULL DEFAULT FALSE,
    display_order      INT           NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_product_price CHECK (price IS NULL OR price >= 0),
    CONSTRAINT ck_product_min_quantity CHECK (min_quantity BETWEEN 1 AND 9999),
    CONSTRAINT ck_product_status CHECK (status IN ('ACTIVE', 'UNAVAILABLE', 'INACTIVE')),
    CONSTRAINT ck_product_period CHECK (available_from IS NULL OR available_until IS NULL
        OR available_until >= available_from),
    CONSTRAINT ck_product_weekdays CHECK (available_weekdays IS NULL OR available_weekdays BETWEEN 1 AND 127),
    CONSTRAINT ck_product_fulfillment CHECK (pickup_allowed OR delivery_allowed),
    CONSTRAINT ck_product_display_order CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uk_product_name ON product (lower(name));
CREATE INDEX ix_product_category ON product (category_id, display_order);

-- ---------------------------------------------------------------------------
-- Fotos dos produtos usam o mesmo armazenamento dos demais arquivos: cada arquivo pertence
-- a um pedido OU a um produto.
-- ---------------------------------------------------------------------------
ALTER TABLE stored_file
    ALTER COLUMN order_id DROP NOT NULL,
    ADD COLUMN product_id BIGINT REFERENCES product (id),
    DROP CONSTRAINT ck_stored_file_purpose,
    ADD CONSTRAINT ck_stored_file_purpose
        CHECK (purpose IN ('ORDER_REFERENCE', 'TOPPER_REFERENCE', 'PAYMENT_PROOF', 'PRODUCT_PHOTO')),
    ADD CONSTRAINT ck_stored_file_owner CHECK (
        (purpose = 'PRODUCT_PHOTO' AND product_id IS NOT NULL AND order_id IS NULL)
        OR (purpose <> 'PRODUCT_PHOTO' AND order_id IS NOT NULL AND product_id IS NULL));

CREATE INDEX ix_stored_file_product ON stored_file (product_id) WHERE product_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Pedido: o bolo personalizado passa a ser opcional (pedido só com itens do catálogo)
-- e ganha a modalidade de atendimento. pickup_date/pickup_time continuam sendo a data e o
-- horário agendados — de retirada ou de entrega.
-- ---------------------------------------------------------------------------
ALTER TABLE cake_order
    ALTER COLUMN size_id DROP NOT NULL,
    ALTER COLUMN size_name_snapshot DROP NOT NULL,
    ADD COLUMN custom_cake        BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN fulfillment_type   VARCHAR(20)  NOT NULL DEFAULT 'PICKUP',
    ADD COLUMN delivery_address   VARCHAR(300),
    ADD COLUMN delivery_district  VARCHAR(100),
    ADD COLUMN delivery_reference VARCHAR(200),
    ADD CONSTRAINT ck_cake_order_custom_cake CHECK (
        (custom_cake AND size_id IS NOT NULL AND size_name_snapshot IS NOT NULL)
        OR (NOT custom_cake AND size_id IS NULL AND size_name_snapshot IS NULL AND NOT topper_requested)),
    ADD CONSTRAINT ck_cake_order_fulfillment CHECK (fulfillment_type IN ('PICKUP', 'DELIVERY')),
    ADD CONSTRAINT ck_cake_order_delivery_address CHECK (
        (fulfillment_type = 'DELIVERY' AND delivery_address IS NOT NULL)
        OR (fulfillment_type = 'PICKUP' AND delivery_address IS NULL AND delivery_district IS NULL
            AND delivery_reference IS NULL));

-- ---------------------------------------------------------------------------
-- Itens do catálogo escolhidos no pedido. Nome, categoria, preço e unidade são copiados
-- (snapshot): editar ou desativar o produto depois não altera pedidos já registrados.
-- Um produto aparece no máximo uma vez por pedido (a quantidade é somada no item).
-- ---------------------------------------------------------------------------
CREATE TABLE order_item (
    order_id            BIGINT        NOT NULL REFERENCES cake_order (id) ON DELETE CASCADE,
    product_id          BIGINT        NOT NULL REFERENCES product (id),
    name_snapshot       VARCHAR(100)  NOT NULL,
    category_snapshot   VARCHAR(60)   NOT NULL,
    unit_price_snapshot NUMERIC(10,2),
    unit_label_snapshot VARCHAR(40),
    quantity            INT           NOT NULL,
    notes               VARCHAR(300),
    position            SMALLINT      NOT NULL,
    CONSTRAINT pk_order_item PRIMARY KEY (order_id, product_id),
    CONSTRAINT ck_order_item_quantity CHECK (quantity BETWEEN 1 AND 9999),
    CONSTRAINT ck_order_item_price CHECK (unit_price_snapshot IS NULL OR unit_price_snapshot >= 0)
);

CREATE INDEX ix_order_item_product ON order_item (product_id);

-- ---------------------------------------------------------------------------
-- Configurações de atendimento. Por padrão o comportamento anterior é mantido:
-- só retirada, com bolo personalizado aberto a encomendas.
-- ---------------------------------------------------------------------------
ALTER TABLE bakery_settings
    ADD COLUMN pickup_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN delivery_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN delivery_info         VARCHAR(500),
    ADD COLUMN custom_cake_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN custom_cake_delivery  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_bakery_settings_fulfillment CHECK (pickup_enabled OR delivery_enabled);
