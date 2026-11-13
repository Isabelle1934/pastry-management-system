# Modelo de Banco de Dados (inicial)

PostgreSQL 18. Esquema gerenciado exclusivamente por migrations Flyway. Convenções:

- Tabelas e colunas em `snake_case`, nomes em inglês (código em inglês, interface em português).
- Chave primária `id BIGINT GENERATED ALWAYS AS IDENTITY` (IDs nunca expostos à cliente — ela usa o código de acompanhamento).
- Instantes em `TIMESTAMPTZ`; data/horário de retirada em `DATE` + `TIME` (hora local de `America/Sao_Paulo`).
- Dinheiro em `NUMERIC(12,2)`; no Java, `BigDecimal`. Nunca `double`.
- Estados em `VARCHAR` com `CHECK` (legíveis e fáceis de evoluir por migration).
- `created_at`, `updated_at` e `version` (bloqueio otimista) em tabelas mutáveis, via `BaseEntity`. Edição concorrente → HTTP 409 `CONCURRENT_MODIFICATION`.
- Hibernate roda com `ddl-auto=validate`: qualquer divergência entre entidade e migration impede a aplicação de subir (e os testes falham).
- Registros financeiros e históricos **nunca são apagados** fisicamente.

## 1. Diagrama de relacionamentos

```
admin_user 1──* password_reset_token
bakery_settings (linha única)

cake_size ─┐        cake_flavor     cake_filling
           │            │                │
           ▼            ▼                ▼
cake_order 1──* order_flavor      order_filling *──1 cake_order
   │ 1──0..1 cake_topper
   │ 1──* stored_file (fotos, comprovantes)
   │ 1──* quote 1──* quote_item
   │ 1──* payment ──0..1 stored_file (comprovante)
   │ 1──0..1 slot_reservation ──* slot_occupancy (data+horário)
   │ 1──* order_history
   │ 1──* expense (opcional; tabela mantida sem uso desde a retirada do módulo de despesas)
   │ 1──* notification

weekly_slot (modelo semanal de horários)   schedule_block (bloqueios)
expense_category 1──* expense
audit_log, shedlock, spring_session*
```

## 2. Tabelas

### Autenticação
**admin_user** — `id`, `email` (único, minúsculas), `name`, `password_hash`, `enabled`, `must_change_password`, `password_changed_at`, `created_at`, `updated_at`.

**password_reset_token** — `id`, `admin_user_id` FK (`ON DELETE CASCADE`), `token_hash` (SHA-256 em hex minúsculo, `VARCHAR(64)` com `CHECK` de formato, único), `expires_at`, `used_at`, `created_at`. Tokens anteriores invalidados ao gerar um novo.

**spring_session / spring_session_attributes** — tabelas padrão do Spring Session JDBC.

### Configurações
**bakery_settings** (uma linha, `id = 1` com `CHECK`) — `name`, `about_text`, `whatsapp_contact`, `instagram`, `address`, `pickup_instructions`, `pix_key`, `pix_holder_name`, `min_lead_time_hours`, `max_booking_days_ahead`, `quote_validity_hours`, `quote_expiry_warning_hours`, `deposit_deadline_hours` (pendente de definição), `owner_notification_email`, `updated_at`.
O percentual do sinal (50%) é regra fixa em código, conforme requisito. Implementado na V2: identidade, contato, Pix, orçamentos e e-mail de avisos; os campos de agenda (`min_lead_time_hours`, `max_booking_days_ahead`) entram na migration da Fase 5 e `deposit_deadline_hours` depende da pendência P4.

### Catálogo
**cake_size** — `id`, `name`, `description`, `servings`, `base_price`, `max_flavors`, `max_fillings`, `active`, `display_order`.
**cake_flavor** (massa) — `id`, `name`, `description`, `active`, `display_order`.
**cake_filling** (recheio) — `id`, `name`, `description`, `active`, `display_order`.
Itens de catálogo são desativados, não apagados, quando já usados em pedidos.

### Agenda
**weekly_slot** — modelo semanal: `id`, `day_of_week` (1–7), `start_time`, `capacity` (> 0), `active`. Único (`day_of_week`, `start_time`). Um dia sem horários ativos = dia sem funcionamento.

**schedule_block** — `id`, `block_date`, `start_time` (NULL = dia inteiro), `reason`, `created_at`. Único (`block_date`, `start_time`).

**slot_occupancy** — contador por horário concreto, usado para garantir capacidade:
`slot_date`, `slot_time` (PK composta), `reserved_count INT NOT NULL CHECK (reserved_count >= 0)`, `updated_at`.

**slot_reservation** — `id`, `order_id` FK **único**, `slot_date`, `slot_time`, `status` (`ACTIVE`, `RELEASED`), `created_at`, `released_at`, `release_reason`.
Índice único parcial: um pedido tem no máximo uma reserva `ACTIVE`.

### Pedidos
**cake_order**
- Identificação: `id`, `tracking_code` (único, 16 caracteres aleatórios), `created_at`, `updated_at`, `version`.
- Cliente: `customer_name`, `customer_whatsapp` (normalizado E.164, ex. `+5511999998888`).
- Agendamento solicitado: `pickup_date`, `pickup_time`.
- Personalização: `size_id` FK, `size_name_snapshot`, `decoration`, `cake_text`, `notes`.
- **Estados separados:** `commercial_status`, `production_status`, `payment_status` (ver BUSINESS_RULES.md). `payment_status` é derivado dos pagamentos e mantido pelo serviço na mesma transação.
- Encerramento: `cancelled_at`, `cancellation_reason`, `cancelled_by` (`OWNER`, `CLIENT`, `SYSTEM`).

**order_flavor / order_filling** — `order_id`, `flavor_id`/`filling_id`, `name_snapshot`. PK (`order_id`, item). Quantidade validada contra `max_flavors`/`max_fillings` do tamanho.

**Topo de bolo** — colunas `topper_requested`, `topper_theme`, `topper_name`, `topper_age` em `cake_order` (0..1 por pedido; `CHECK` garante tema quando solicitado). Mais simples que uma tabela 1:1.

**order_history** — `id`, `order_id`, `event_type` (`CREATED`, `EDITED_BY_CLIENT`, `EDITED_BY_OWNER`, `STATUS_CHANGED`, `QUOTE_SENT`, `QUOTE_ACCEPTED`, `PAYMENT_CONFIRMED`, `EXPIRED`, …), `actor` (`CLIENT`, `OWNER`, `SYSTEM`), `description`, `details JSONB` (valores antes/depois, sem dados sensíveis desnecessários), `created_at`. Somente inserção.

### Orçamentos
**quote** — `id`, `order_id`, `version_number`, `status`, `total`, `deposit_amount`, `notes`, `valid_until`, `sent_at`, `responded_at`, `created_at`, `expired_at`, `expiry_reason`.
- Único (`order_id`, `version_number`).
- Índice único parcial: no máximo um orçamento `SENT` ou `ACCEPTED` por pedido.
- Após `SENT`, o conteúdo é imutável; alterar = nova versão (anterior → `SUPERSEDED`).

**quote_item** — `id`, `quote_id`, `position`, `name`, `description`, `quantity NUMERIC(10,2) > 0` (padrão 1), `unit_price >= 0`, `subtotal` (= quantidade × unitário, calculado no backend, conferido por `CHECK`).

### Pagamentos
**payment** — `id`, `order_id`, `quote_id` (orçamento vigente no registro), `kind` (`DEPOSIT`, `BALANCE`, `REFUND` — pendente), `method` (`PIX`, `CASH`), `amount > 0`, `status` (`PENDING_REVIEW`, `CONFIRMED`, `REJECTED`), `proof_file_id` FK nullable, `submitted_by` (`CLIENT`, `OWNER`), `submitted_at`, `reviewed_at`, `review_note`, `created_at`.
Nunca apagado; correções são feitas por novo registro ou rejeição com motivo.

### Arquivos
**stored_file** — `id`, `storage_key` (UUID, único), `order_id` FK, `purpose` (`ORDER_REFERENCE`, `TOPPER_REFERENCE`, `PAYMENT_PROOF`), `original_name` (saneado), `content_type` (detectado), `size_bytes`, `sha256`, `uploaded_by`, `created_at`, `deleted_at`.

### Financeiro
O resumo financeiro é calculado sobre `quote` e `payment`. As tabelas abaixo continuam no banco (dados preservados), mas não são mais usadas pela aplicação desde a V9:
**expense_category** — `id`, `name` (único), `active`.
**expense** — `id`, `description`, `amount > 0`, `expense_date`, `category_id` FK, `order_id` FK nullable, `created_at`, `updated_at`, `deleted_at` (exclusão lógica com registro em `audit_log`).
Uma despesa é um único registro: o vínculo com pedido é apenas um atributo — por isso nunca é somada duas vezes.

### Avisos e auditoria
**notification** — `id`, `type` (`QUOTE_EXPIRING`, `QUOTE_EXPIRED`, `QUOTE_ACCEPTED`, `PROOF_SUBMITTED`, `NEW_ORDER`, …), `order_id`, `quote_id`, `message`, `dedup_key` (**único** — garante idempotência), `created_at`, `read_at`, `email_status`, `email_sent_at`, `email_attempts`.

**audit_log** — `id`, `actor_type` (`OWNER`/`CLIENT`/`SYSTEM`), `actor_id`, `action`, `entity_type`, `entity_id` (texto), `details JSONB`, `created_at`. **Imutável:** trigger `trg_audit_log_immutable` rejeita UPDATE e DELETE (função reutilizável `prevent_history_modification()`, que também protegerá `order_history`).

**shedlock** — tabela padrão do ShedLock.

## 3. Índices principais

- `cake_order (commercial_status, pickup_date)`, `cake_order (pickup_date, pickup_time)`, `cake_order (tracking_code)` único.
- `quote (status, valid_until)` — busca de vencimentos.
- `payment (order_id)`, `payment (status)`.
- `expense (expense_date)`, `order_history (order_id, created_at)`.

## 4. Migrations planejadas

| Versão | Conteúdo |
|---|---|
| V1 ✅ | admin_user, password_reset_token, spring_session, audit_log, shedlock, função `prevent_history_modification()` |
| V2 ✅ | bakery_settings (linha única id=1, textos vazios; validade 48 h e aviso 12 h como padrão editável), cake_size, cake_flavor, cake_filling (nomes únicos por `lower(name)`) |
| V3 ✅ | agenda: weekly_slot, schedule_block (`UNIQUE NULLS NOT DISTINCT`), slot_occupancy, slot_reservation (FK para cake_order criada na V4); `min_lead_time_hours` (24) e `max_booking_days_ahead` (90) em bakery_settings |
| V4 ✅ | pedidos: cake_order (topo de bolo nas colunas `topper_*`, em vez de tabela própria), order_flavor, order_filling, order_history (imutável), stored_file; FK slot_reservation → cake_order |
| V5 ✅ | quote, quote_item; índices únicos parciais (1 vigente e 1 rascunho por pedido); triggers impedem alterar itens/valores ou excluir orçamento depois de enviado |
| V6 ✅ | payment, notification, prazo do sinal em bakery_settings |
| V7 ✅ | expense_category, expense |
| V8 ✅ | catálogo e atendimento: product_category, product (situação ACTIVE/UNAVAILABLE/INACTIVE, preço opcional, período, dias da semana em máscara de bits, retirada/delivery), order_item (cópia de nome/categoria/preço/unidade; PK pedido+produto), stored_file passa a aceitar fotos de produto (`product_id`, finalidade `PRODUCT_PHOTO`), cake_order ganha `custom_cake`, `fulfillment_type` e endereço de entrega (tamanho passa a ser opcional), bakery_settings ganha `pickup_enabled`, `delivery_enabled`, `delivery_info`, `custom_cake_enabled`, `custom_cake_delivery`. Pedidos existentes ficam como bolo personalizado com retirada |
| V9 ✅ | bakery_settings ganha `delivery_fee` (padrão 20.00), `card_payment_link` (só https) e `deposit_cutoff_hours` (padrão 24); `payment.method` aceita `CARD_LINK`; `quote_item.kind` (`PRODUCT`/`EXTRA`/`TOPPER`/`DELIVERY`, itens existentes ficam `PRODUCT`); massas confirmadas Baunilha, Chocolate e Red Velvet inseridas se ainda não existirem. Só acréscimos |
| V10 ✅ | `cake_category` (tipos de bolo: características, aviso, `consult_only`, limites de massa/recheio); `cake_size` ganha `category_id`, `weight_kg`, `diameter_cm`; tabela da proprietária (5 baixos, 6 altos, 5 de 2 andares e o tipo "3 andares ou mais — consulte valores") inserida só se não existir; `cake_order` ganha `cake_category_id`, `cake_category_snapshot`, `size_price_snapshot`, `customer_email`, `delivery_zone` (entregas antigas ficam `CITY`) e índice para detectar envio repetido; `weekly_slot.capacity` passa a aceitar vazio (= sem limite); `bakery_settings` ganha `delivery_fee_sweets` (5.00), `party_venue_fee` (30.00) + `party_venue_fee_confirmed`, `punctuality_notice` e `deposit_reminder_hours` (12). Nenhum dado existente apagado |

Migrations são criadas na fase de cada módulo (não todas de uma vez) e nunca editadas depois de aplicadas. Nenhum dado fictício é inserido por migration; dados de demonstração existirão apenas em perfil `dev`.
