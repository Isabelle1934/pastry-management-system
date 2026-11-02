-- V2: configurações da confeitaria e catálogo de personalização (tamanhos, massas, recheios).

-- ---------------------------------------------------------------------------
-- Configurações (linha única, id = 1). Campos de texto começam vazios (NULL):
-- são preenchidos pela proprietária no painel. Nenhum dado fictício é inserido.
-- Configurações de agenda (antecedência etc.) entram na migration da Fase 5.
-- ---------------------------------------------------------------------------
CREATE TABLE bakery_settings (
    id                         SMALLINT     PRIMARY KEY,
    name                       VARCHAR(120),
    about_text                 VARCHAR(2000),
    whatsapp_contact           VARCHAR(20),
    instagram                  VARCHAR(60),
    address                    VARCHAR(300),
    pickup_instructions        VARCHAR(1000),
    pix_key                    VARCHAR(77),
    pix_holder_name            VARCHAR(120),
    quote_validity_hours       INT          NOT NULL DEFAULT 48,
    quote_expiry_warning_hours INT          NOT NULL DEFAULT 12,
    owner_notification_email   VARCHAR(254),
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_bakery_settings_single_row CHECK (id = 1),
    CONSTRAINT ck_bakery_settings_quote_validity CHECK (quote_validity_hours BETWEEN 1 AND 720),
    CONSTRAINT ck_bakery_settings_quote_warning
        CHECK (quote_expiry_warning_hours >= 1 AND quote_expiry_warning_hours < quote_validity_hours)
);

INSERT INTO bakery_settings (id) VALUES (1);

-- ---------------------------------------------------------------------------
-- Tamanhos. O preço-base é apenas referência: o valor final vem do orçamento.
-- ---------------------------------------------------------------------------
CREATE TABLE cake_size (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(80)   NOT NULL,
    description   VARCHAR(300),
    servings      INT,
    base_price    NUMERIC(10,2) NOT NULL,
    max_flavors   INT           NOT NULL,
    max_fillings  INT           NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    display_order INT           NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_cake_size_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_cake_size_servings CHECK (servings IS NULL OR servings > 0),
    CONSTRAINT ck_cake_size_base_price CHECK (base_price >= 0),
    CONSTRAINT ck_cake_size_max_flavors CHECK (max_flavors BETWEEN 1 AND 10),
    CONSTRAINT ck_cake_size_max_fillings CHECK (max_fillings BETWEEN 1 AND 10),
    CONSTRAINT ck_cake_size_display_order CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uk_cake_size_name ON cake_size (lower(name));

-- ---------------------------------------------------------------------------
-- Massas e recheios (mesma estrutura; tabelas separadas para manter FKs claras nos pedidos)
-- ---------------------------------------------------------------------------
CREATE TABLE cake_flavor (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(80)  NOT NULL,
    description   VARCHAR(300),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_cake_flavor_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_cake_flavor_display_order CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uk_cake_flavor_name ON cake_flavor (lower(name));

CREATE TABLE cake_filling (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(80)  NOT NULL,
    description   VARCHAR(300),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_cake_filling_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_cake_filling_display_order CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uk_cake_filling_name ON cake_filling (lower(name));
