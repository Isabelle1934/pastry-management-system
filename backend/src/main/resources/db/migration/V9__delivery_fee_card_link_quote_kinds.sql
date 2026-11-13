-- V9: taxa de entrega, link de pagamento no cartão, prazo do sinal antes da data da encomenda,
-- tipo dos itens do orçamento e massas confirmadas pela proprietária. Somente acréscimos: nenhum dado é apagado.

-- ---------------------------------------------------------------------------
-- Configurações
--   delivery_fee: taxa de entrega dentro da cidade (confirmada: R$ 20,00). Fora da cidade: sob consulta (P15).
--   card_payment_link: link de pagamento no cartão de crédito informado pela proprietária (sem integração).
--   deposit_cutoff_hours: o sinal precisa ser pago até X horas antes da data/horário agendado (0 = sem limite).
-- ---------------------------------------------------------------------------
ALTER TABLE bakery_settings
    ADD COLUMN delivery_fee         NUMERIC(8,2) NOT NULL DEFAULT 20.00,
    ADD COLUMN card_payment_link    VARCHAR(500),
    ADD COLUMN deposit_cutoff_hours INT          NOT NULL DEFAULT 24,
    ADD CONSTRAINT ck_bakery_settings_delivery_fee CHECK (delivery_fee BETWEEN 0 AND 9999.99),
    ADD CONSTRAINT ck_bakery_settings_card_link CHECK (card_payment_link IS NULL OR card_payment_link LIKE 'https://%'),
    ADD CONSTRAINT ck_bakery_settings_deposit_cutoff CHECK (deposit_cutoff_hours BETWEEN 0 AND 720);

-- ---------------------------------------------------------------------------
-- Pagamentos: o sinal pode ser pago por Pix ou pelo link de pagamento no cartão.
-- ---------------------------------------------------------------------------
ALTER TABLE payment DROP CONSTRAINT ck_payment_method;
ALTER TABLE payment ADD CONSTRAINT ck_payment_method CHECK (method IN ('PIX', 'CASH', 'CARD_LINK'));

-- ---------------------------------------------------------------------------
-- Itens do orçamento ganham um tipo, para o cálculo separado de produtos, adicionais, topo e entrega.
-- Itens já existentes ficam como PRODUCT (o total e os valores não mudam).
-- O gatilho de V5 impede alterar itens de orçamentos enviados; por isso a coluna nasce com valor padrão.
-- ---------------------------------------------------------------------------
ALTER TABLE quote_item
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'PRODUCT',
    ADD CONSTRAINT ck_quote_item_kind CHECK (kind IN ('PRODUCT', 'EXTRA', 'TOPPER', 'DELIVERY'));

-- ---------------------------------------------------------------------------
-- Massas confirmadas pela proprietária. Só entram se ainda não existirem (nome sem diferenciar maiúsculas).
-- Tamanhos, preços e recheios dependem da planilha da proprietária e não são cadastrados aqui.
-- ---------------------------------------------------------------------------
INSERT INTO cake_flavor (name, display_order, created_at, updated_at)
SELECT v.name, v.display_order, now(), now()
FROM (VALUES ('Baunilha', 1), ('Chocolate', 2), ('Red Velvet', 3)) AS v (name, display_order)
WHERE NOT EXISTS (SELECT 1 FROM cake_flavor f WHERE lower(f.name) = lower(v.name));
