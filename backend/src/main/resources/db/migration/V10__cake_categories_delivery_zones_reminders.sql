-- V10: categorias de bolo com a tabela de tamanhos da proprietária, modalidades de entrega com taxas
-- separadas, agenda sem limite padrão por horário, aviso de pontualidade e lembrete de pagamento.
-- Somente acréscimos e afrouxamento de restrições: nenhum dado existente é apagado ou alterado.

-- ---------------------------------------------------------------------------
-- Categorias de bolo personalizado (ex.: baixos, altos, 2 andares).
--   description: características (camadas de massa/recheio), mostradas à cliente.
--   notice: aviso destacado (ex.: "Topo não incluso").
--   consult_only: categoria sem tabela de preço — a cliente descreve e o valor vem no orçamento
--                 (ex.: bolos de 3 andares ou mais); usa max_flavors/max_fillings da própria categoria.
-- ---------------------------------------------------------------------------
CREATE TABLE cake_category (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(80)  NOT NULL,
    description   VARCHAR(300),
    notice        VARCHAR(300),
    consult_only  BOOLEAN      NOT NULL DEFAULT FALSE,
    max_flavors   INT          NOT NULL DEFAULT 1,
    max_fillings  INT          NOT NULL DEFAULT 2,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_cake_category_name CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_cake_category_max_flavors CHECK (max_flavors BETWEEN 1 AND 10),
    CONSTRAINT ck_cake_category_max_fillings CHECK (max_fillings BETWEEN 1 AND 10),
    CONSTRAINT ck_cake_category_display_order CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uk_cake_category_name ON cake_category (lower(name));

-- Tamanhos ganham categoria, peso e diâmetro (opcionais: tamanhos antigos continuam válidos sem eles).
ALTER TABLE cake_size
    ADD COLUMN category_id BIGINT REFERENCES cake_category (id),
    ADD COLUMN weight_kg   NUMERIC(5,2),
    ADD COLUMN diameter_cm INT,
    ADD CONSTRAINT ck_cake_size_weight CHECK (weight_kg IS NULL OR weight_kg > 0),
    ADD CONSTRAINT ck_cake_size_diameter CHECK (diameter_cm IS NULL OR diameter_cm BETWEEN 1 AND 200);

CREATE INDEX ix_cake_size_category ON cake_size (category_id);

-- Tabela enviada pela proprietária (imagens "Tamanhos de bolo" e "Bolos de 2 andares").
-- Só entra o que ainda não existe (nome sem diferenciar maiúsculas). Preços editáveis no painel.
INSERT INTO cake_category (name, description, notice, consult_only, max_flavors, max_fillings, display_order,
                           created_at, updated_at)
SELECT v.name, v.description, v.notice, v.consult_only, 1, 2, v.display_order, now(), now()
FROM (VALUES
    ('Bolos baixos', '2 camadas de recheio e 3 camadas de massa. Você pode escolher 2 sabores de recheio.',
     NULL, FALSE, 1),
    ('Bolos altos', '3 camadas de recheio e 4 camadas de massa. Você pode escolher 2 sabores de recheio.',
     NULL, FALSE, 2),
    ('Bolos de 2 andares', 'Bolo de dois andares. Você pode escolher 2 sabores de recheio.',
     'Topo não incluso.', FALSE, 3),
    ('Bolos de 3 andares ou mais', 'Descreva o bolo que você imagina: o valor é calculado no orçamento.',
     'Consulte valores.', TRUE, 4)
) AS v (name, description, notice, consult_only, display_order)
WHERE NOT EXISTS (SELECT 1 FROM cake_category c WHERE lower(c.name) = lower(v.name));

INSERT INTO cake_size (name, servings, base_price, max_flavors, max_fillings, display_order, category_id,
                       weight_kg, diameter_cm, created_at, updated_at)
SELECT v.name, v.servings, v.price, 1, 2, v.display_order,
       (SELECT id FROM cake_category c WHERE lower(c.name) = lower(v.category)), v.weight, v.diameter, now(), now()
FROM (VALUES
    ('Bolos baixos', 'Baixo 15 cm · 1,5 kg', 15, 105.00, 1.5, 15, 1),
    ('Bolos baixos', 'Baixo 20 cm · 2 kg', 20, 140.00, 2, 20, 2),
    ('Bolos baixos', 'Baixo 25 cm · 3 kg', 30, 210.00, 3, 25, 3),
    ('Bolos baixos', 'Baixo 30 cm · 4 kg', 40, 280.00, 4, 30, 4),
    ('Bolos baixos', 'Baixo 35 cm · 6 kg', 60, 420.00, 6, 35, 5),
    ('Bolos altos', 'Alto 12 cm · 1 kg', 10, 70.00, 1, 12, 11),
    ('Bolos altos', 'Alto 15 cm · 2 kg', 18, 140.00, 2, 15, 12),
    ('Bolos altos', 'Alto 20 cm · 3 kg', 30, 210.00, 3, 20, 13),
    ('Bolos altos', 'Alto 22 cm · 4 kg', 40, 280.00, 4, 22, 14),
    ('Bolos altos', 'Alto 25 cm · 6 kg', 60, 420.00, 6, 25, 15),
    ('Bolos altos', 'Alto 30 cm · 8 kg', 80, 560.00, 8, 30, 16),
    ('Bolos de 2 andares', '2 andares · 3,5 kg', 35, 298.00, 3.5, NULL, 21),
    ('Bolos de 2 andares', '2 andares · 5 kg', 50, 400.00, 5, NULL, 22),
    ('Bolos de 2 andares', '2 andares · 6 kg', 60, 470.00, 6, NULL, 23),
    ('Bolos de 2 andares', '2 andares · 8 kg', 80, 610.00, 8, NULL, 24),
    ('Bolos de 2 andares', '2 andares · 10 kg', 100, 750.00, 10, NULL, 25)
) AS v (category, name, servings, price, weight, diameter, display_order)
WHERE NOT EXISTS (SELECT 1 FROM cake_size s WHERE lower(s.name) = lower(v.name));

-- ---------------------------------------------------------------------------
-- Pedido: categoria do bolo (obrigatória só para bolos "consulte valores", sem tamanho) e cópia do
-- nome da categoria e do preço-base do tamanho no momento do pedido (preserva valores históricos).
-- Pedidos antigos ficam sem a cópia de preço (o valor deles está no orçamento).
-- ---------------------------------------------------------------------------
ALTER TABLE cake_order
    ADD COLUMN cake_category_id       BIGINT REFERENCES cake_category (id),
    ADD COLUMN cake_category_snapshot VARCHAR(80),
    ADD COLUMN size_price_snapshot    NUMERIC(10,2),
    ADD COLUMN customer_email         VARCHAR(254),
    ADD COLUMN delivery_zone          VARCHAR(20);

UPDATE cake_order SET delivery_zone = 'CITY' WHERE fulfillment_type = 'DELIVERY' AND delivery_zone IS NULL;

ALTER TABLE cake_order
    DROP CONSTRAINT ck_cake_order_custom_cake,
    ADD CONSTRAINT ck_cake_order_custom_cake CHECK (
        (custom_cake AND ((size_id IS NOT NULL AND size_name_snapshot IS NOT NULL) OR cake_category_id IS NOT NULL))
        OR (NOT custom_cake AND size_id IS NULL AND size_name_snapshot IS NULL AND cake_category_id IS NULL
            AND NOT topper_requested)),
    ADD CONSTRAINT ck_cake_order_delivery_zone CHECK (
        (fulfillment_type = 'DELIVERY' AND delivery_zone IN ('CITY', 'PARTY_VENUE', 'OUTSIDE_CITY'))
        OR (fulfillment_type = 'PICKUP' AND delivery_zone IS NULL)),
    ADD CONSTRAINT ck_cake_order_customer_email CHECK (customer_email IS NULL OR customer_email LIKE '%_@_%');

-- Busca de solicitações repetidas (mesma cliente, mesma data/horário, poucos minutos depois).
CREATE INDEX ix_cake_order_duplicate_check ON cake_order (customer_whatsapp, pickup_date, pickup_time, created_at);

-- ---------------------------------------------------------------------------
-- Agenda: o limite por horário passa a ser opcional (NULL = sem limite). Horários já cadastrados
-- mantêm o limite que tinham; a proprietária pode apagá-lo no painel.
-- ---------------------------------------------------------------------------
ALTER TABLE weekly_slot ALTER COLUMN capacity DROP NOT NULL;

-- ---------------------------------------------------------------------------
-- Configurações
--   delivery_fee (V9) passa a ser a taxa de ENTREGA DE BOLOS na cidade (R$ 20,00).
--   delivery_fee_sweets: delivery de outros doces na cidade (R$ 5,00).
--   party_venue_fee: entrega no local da festa (R$ 30,00 na tabela) — só entra sozinha no orçamento
--                    depois que a proprietária confirmar (party_venue_fee_confirmed).
--   punctuality_notice: aviso de pontualidade (editável).
--   deposit_reminder_hours: lembrete de pagamento X horas antes do fim do prazo do sinal.
-- ---------------------------------------------------------------------------
ALTER TABLE bakery_settings
    ADD COLUMN delivery_fee_sweets       NUMERIC(8,2)  NOT NULL DEFAULT 5.00,
    ADD COLUMN party_venue_fee           NUMERIC(8,2)  NOT NULL DEFAULT 30.00,
    ADD COLUMN party_venue_fee_confirmed BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN punctuality_notice        VARCHAR(1000) NOT NULL DEFAULT
        'Pedimos que a retirada ou a entrega aconteça no horário combinado. Os bolos e doces são preparados para esse horário: chegar no combinado garante que tudo chegue até você do jeitinho planejado. Se precisar mudar o horário, avise com antecedência pelo WhatsApp.',
    ADD COLUMN deposit_reminder_hours    INT           NOT NULL DEFAULT 12,
    ADD CONSTRAINT ck_bakery_settings_delivery_fee_sweets CHECK (delivery_fee_sweets BETWEEN 0 AND 9999.99),
    ADD CONSTRAINT ck_bakery_settings_party_venue_fee CHECK (party_venue_fee BETWEEN 0 AND 9999.99),
    ADD CONSTRAINT ck_bakery_settings_punctuality CHECK (length(trim(punctuality_notice)) > 0),
    ADD CONSTRAINT ck_bakery_settings_reminder CHECK (deposit_reminder_hours BETWEEN 1 AND 720);
