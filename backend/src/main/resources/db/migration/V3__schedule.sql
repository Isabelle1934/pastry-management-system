-- V3: agenda — horários semanais, bloqueios, ocupação por horário e reservas de capacidade.
-- Datas e horários de retirada são locais (America/Sao_Paulo): DATE + TIME, sem fuso.

-- ---------------------------------------------------------------------------
-- Regras gerais de agendamento (valores padrão editáveis no painel)
-- ---------------------------------------------------------------------------
ALTER TABLE bakery_settings
    ADD COLUMN min_lead_time_hours    INT NOT NULL DEFAULT 24,
    ADD COLUMN max_booking_days_ahead INT NOT NULL DEFAULT 90,
    ADD CONSTRAINT ck_bakery_settings_min_lead CHECK (min_lead_time_hours BETWEEN 0 AND 720),
    ADD CONSTRAINT ck_bakery_settings_max_days CHECK (max_booking_days_ahead BETWEEN 1 AND 365);

-- ---------------------------------------------------------------------------
-- Modelo semanal: um horário de retirada por linha. Dia sem horários ativos = fechado.
-- day_of_week segue a ISO-8601: 1 = segunda ... 7 = domingo.
-- ---------------------------------------------------------------------------
CREATE TABLE weekly_slot (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    day_of_week SMALLINT    NOT NULL,
    start_time  TIME        NOT NULL,
    capacity    INT         NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_weekly_slot UNIQUE (day_of_week, start_time),
    CONSTRAINT ck_weekly_slot_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_weekly_slot_capacity CHECK (capacity BETWEEN 1 AND 100),
    CONSTRAINT ck_weekly_slot_time CHECK (date_part('second', start_time) = 0)
);

-- ---------------------------------------------------------------------------
-- Bloqueios: start_time NULL = dia inteiro. NULLS NOT DISTINCT impede dois bloqueios
-- de dia inteiro na mesma data.
-- ---------------------------------------------------------------------------
CREATE TABLE schedule_block (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    block_date DATE         NOT NULL,
    start_time TIME,
    reason     VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_schedule_block UNIQUE NULLS NOT DISTINCT (block_date, start_time)
);

-- ---------------------------------------------------------------------------
-- Ocupação por horário concreto: contador usado no controle de concorrência.
-- A capacidade ocupada só aumenta quando o sinal é confirmado (reserva).
-- ---------------------------------------------------------------------------
CREATE TABLE slot_occupancy (
    slot_date      DATE        NOT NULL,
    slot_time      TIME        NOT NULL,
    reserved_count INT         NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_slot_occupancy PRIMARY KEY (slot_date, slot_time),
    CONSTRAINT ck_slot_occupancy_count CHECK (reserved_count >= 0)
);

-- ---------------------------------------------------------------------------
-- Reservas de capacidade por pedido. A FK para cake_order é criada na migration
-- dos pedidos (Fase 6). Reservas liberadas são mantidas como histórico.
-- ---------------------------------------------------------------------------
CREATE TABLE slot_reservation (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id       BIGINT       NOT NULL,
    slot_date      DATE         NOT NULL,
    slot_time      TIME         NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    released_at    TIMESTAMPTZ,
    release_reason VARCHAR(200),
    CONSTRAINT ck_slot_reservation_status CHECK (status IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT ck_slot_reservation_release
        CHECK ((status = 'ACTIVE' AND released_at IS NULL) OR (status = 'RELEASED' AND released_at IS NOT NULL))
);

-- Um pedido tem no máximo uma reserva ativa.
CREATE UNIQUE INDEX uk_slot_reservation_active_order ON slot_reservation (order_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_slot_reservation_slot ON slot_reservation (slot_date, slot_time) WHERE status = 'ACTIVE';
