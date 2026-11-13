package br.com.confeitaria.modules.order;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.modules.deadline.DeadlineService;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderItemRequest;
import br.com.confeitaria.modules.order.dto.PublicOrderDtos.OrderRequest;
import br.com.confeitaria.modules.order.entity.DeliveryZone;
import br.com.confeitaria.modules.order.entity.FulfillmentType;
import br.com.confeitaria.modules.order.service.OrderAdminService;
import br.com.confeitaria.modules.order.service.OrderCreationService;
import br.com.confeitaria.modules.payment.dto.PaymentDtos.RegisterRequest;
import br.com.confeitaria.modules.payment.entity.Payment;
import br.com.confeitaria.modules.payment.service.PaymentService;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.DraftRequest;
import br.com.confeitaria.modules.quote.dto.QuoteDtos.ItemRequest;
import br.com.confeitaria.modules.quote.entity.QuoteItemKind;
import br.com.confeitaria.modules.quote.service.QuoteService;
import br.com.confeitaria.modules.schedule.ScheduleTestSupport;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;
import br.com.confeitaria.support.OrderFixtures;
import br.com.confeitaria.support.RecordingMailSender;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Requisitos da proprietária (tabela de bolos das imagens enviadas, taxas por modalidade, agenda sem limite
 * padrão, lembrete de pagamento, aviso de pontualidade e preços históricos).
 * "Agora" é segunda 02/11/2026 12:00 UTC; os pedidos são para terça 03/11/2026 às 14:00.
 */
@IntegrationTest
class CakeTableDeliveryAndRemindersIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderFixtures fixtures;

    @Autowired
    private OrderCreationService creation;

    @Autowired
    private OrderAdminService admin;

    @Autowired
    private QuoteService quotes;

    @Autowired
    private PaymentService payments;

    @Autowired
    private DeadlineService deadlines;

    @Autowired
    private MutableClock clock;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private AuthTestSupport auth;

    @Autowired
    private AuthRateLimiter authLimiter;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    private long baunilha;
    private long chocolate;
    private final List<Long> fillings = new ArrayList<>();
    private long ownerId;
    private OrderFixtures.Catalog catalog;

    @BeforeEach
    void setUp() {
        catalog = fixtures.prepare();
        authLimiter.clear();
        ownerId = auth.createUser().getId();
        baunilha = idOf("select id from cake_flavor where lower(name) = 'baunilha'");
        chocolate = idOf("select id from cake_flavor where lower(name) = 'chocolate'");
        fillings.clear();
        String suffix = " " + UUID.randomUUID().toString().substring(0, 6);
        for (String name : List.of("Brigadeiro", "Ninho", "Doce de leite")) {
            fillings.add(idOf("insert into cake_filling (name, created_at, updated_at) values ('" + name + suffix
                    + "', now(), now()) returning id"));
        }
        jdbc.update("update bakery_settings set delivery_enabled = true, pickup_enabled = true, custom_cake_enabled = true, "
                + "custom_cake_delivery = true, delivery_fee = 20.00, delivery_fee_sweets = 5.00, party_venue_fee = 30.00, "
                + "party_venue_fee_confirmed = false, deposit_reminder_hours = 12");
    }

    @AfterEach
    void restore() {
        creation.setDuplicateWindow(Duration.ZERO);
        jdbc.update("update bakery_settings set delivery_enabled = false, custom_cake_delivery = false, "
                + "party_venue_fee_confirmed = false, deposit_reminder_hours = 12");
    }

    private long idOf(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private long sizeId(String name) {
        return jdbc.queryForObject("select id from cake_size where name = ?", Long.class, name);
    }

    private long cakeCategoryId(String name) {
        return jdbc.queryForObject("select id from cake_category where name = ?", Long.class, name);
    }

    private static OrderRequest request(Long sizeId, Long cakeCategoryId, List<Long> flavors, List<Long> fillings,
            String decoration, List<OrderItemRequest> items, FulfillmentType type, DeliveryZone zone, String email) {
        boolean cake = sizeId != null || cakeCategoryId != null;
        boolean delivery = type == FulfillmentType.DELIVERY;
        return new OrderRequest("Ana Souza", OrderFixtures.WHATSAPP, OrderFixtures.TUESDAY, OrderFixtures.TWO_PM, sizeId,
                cake ? flavors : List.of(), cake ? fillings : List.of(), decoration, null, null, false, null, null,
                null, null, cake, items, type, delivery ? "Rua das Flores, 10" : null, delivery ? "Centro" : null,
                null, cakeCategoryId, zone, email);
    }

    private long create(OrderRequest request) {
        String code = creation.create(request, List.of(), null, "198.51.100." + (1 + (int) (Math.random() * 200)))
                .trackingCode();
        return jdbc.queryForObject("select id from cake_order where tracking_code = ?", Long.class, code.replace("-", ""));
    }

    private long cakeOrder(String sizeName) {
        return create(request(sizeId(sizeName), null, List.of(chocolate), fillings.subList(0, 2), "Flores",
                List.of(), FulfillmentType.PICKUP, null, null));
    }

    // 1–3: tabela de bolos baixos, altos e de 2 andares, como nas imagens, e pedido com o preço da tabela.
    @Test
    void cakeTableFromTheOwnerImagesIsAvailableToClientsAndOrdersKeepTheTablePrice() throws Exception {
        JsonNode catalog = json.readTree(mockMvc.perform(get("/api/public/catalog")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(sizesOf(catalog, "Bolos baixos")).containsExactly(
                "Baixo 15 cm · 1,5 kg|15|105.00", "Baixo 20 cm · 2 kg|20|140.00", "Baixo 25 cm · 3 kg|30|210.00",
                "Baixo 30 cm · 4 kg|40|280.00", "Baixo 35 cm · 6 kg|60|420.00");
        assertThat(sizesOf(catalog, "Bolos altos")).containsExactly(
                "Alto 12 cm · 1 kg|10|70.00", "Alto 15 cm · 2 kg|18|140.00", "Alto 20 cm · 3 kg|30|210.00",
                "Alto 22 cm · 4 kg|40|280.00", "Alto 25 cm · 6 kg|60|420.00", "Alto 30 cm · 8 kg|80|560.00");
        assertThat(sizesOf(catalog, "Bolos de 2 andares")).containsExactly(
                "2 andares · 3,5 kg|35|298.00", "2 andares · 5 kg|50|400.00", "2 andares · 6 kg|60|470.00",
                "2 andares · 8 kg|80|610.00", "2 andares · 10 kg|100|750.00");
        JsonNode twoTiers = category(catalog, "Bolos de 2 andares");
        assertThat(twoTiers.get("notice").asText()).isEqualTo("Topo não incluso.");
        assertThat(category(catalog, "Bolos baixos").get("description").asText())
                .contains("2 camadas de recheio e 3 camadas de massa");
        assertThat(category(catalog, "Bolos altos").get("description").asText())
                .contains("3 camadas de recheio e 4 camadas de massa");
        JsonNode flavors = catalog.get("flavors");
        assertThat(flavors.toString()).contains("Baunilha", "Chocolate", "Red Velvet");

        long low = cakeOrder("Baixo 20 cm · 2 kg");
        long tall = cakeOrder("Alto 12 cm · 1 kg");
        long tiers = cakeOrder("2 andares · 3,5 kg");
        assertThat(admin.detail(low).sizePrice()).isEqualByComparingTo("140.00");
        assertThat(admin.detail(low).cakeCategoryName()).isEqualTo("Bolos baixos");
        assertThat(admin.detail(tall).sizePrice()).isEqualByComparingTo("70.00");
        assertThat(admin.detail(tall).cakeCategoryName()).isEqualTo("Bolos altos");
        assertThat(admin.detail(tiers).sizePrice()).isEqualByComparingTo("298.00");
        assertThat(admin.detail(tiers).cakeCategoryName()).isEqualTo("Bolos de 2 andares");
    }

    // 4: bolos de 3 andares ou mais — "consulte valores", sem tamanho nem preço de tabela.
    @Test
    void threeOrMoreTiersIsAConsultationWithoutTablePrice() throws Exception {
        long consult = cakeCategoryId("Bolos de 3 andares ou mais");
        JsonNode catalog = json.readTree(mockMvc.perform(get("/api/public/catalog"))
                .andReturn().getResponse().getContentAsString());
        JsonNode category = category(catalog, "Bolos de 3 andares ou mais");
        assertThat(category.get("consultOnly").asBoolean()).isTrue();
        assertThat(category.get("notice").asText()).isEqualTo("Consulte valores.");
        assertThat(sizesOf(catalog, "Bolos de 3 andares ou mais")).isEmpty();

        assertThatThrownBy(() -> create(request(null, consult, List.of(chocolate), fillings.subList(0, 2), null,
                List.of(), FulfillmentType.PICKUP, null, null)))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(e -> assertThat(((FieldValidationException) e).getField()).isEqualTo("decoration"));
        long id = create(request(null, consult, List.of(chocolate), fillings.subList(0, 2),
                "3 andares, branco com flores naturais", List.of(), FulfillmentType.PICKUP, null, null));
        var detail = admin.detail(id);
        assertThat(detail.sizeId()).isNull();
        assertThat(detail.sizePrice()).isNull();
        assertThat(detail.cakeCategoryName()).isEqualTo("Bolos de 3 andares ou mais");
        assertThat(jdbc.queryForObject("select count(*) from cake_order where id = ? and custom_cake", Integer.class, id))
                .isEqualTo(1);
        // Uma categoria de tabela não aceita pedido sem tamanho.
        assertThatThrownBy(() -> create(request(null, cakeCategoryId("Bolos baixos"), List.of(chocolate),
                fillings.subList(0, 1), "x", List.of(), FulfillmentType.PICKUP, null, null)))
                .isInstanceOf(FieldValidationException.class);
    }

    // 5: uma massa e até dois sabores de recheio.
    @Test
    void cakeAcceptsOneFlavorAndUpToTwoFillings() {
        long size = sizeId("Baixo 15 cm · 1,5 kg");
        long ok = create(request(size, null, List.of(baunilha), fillings.subList(0, 2), "Flores", List.of(),
                FulfillmentType.PICKUP, null, null));
        assertThat(admin.detail(ok).fillings()).hasSize(2);
        assertThatThrownBy(() -> create(request(size, null, List.of(baunilha), fillings, "Flores", List.of(),
                FulfillmentType.PICKUP, null, null)))
                .isInstanceOf(FieldValidationException.class).hasMessageContaining("até 2 recheios");
        assertThatThrownBy(() -> create(request(size, null, List.of(baunilha, chocolate), fillings.subList(0, 1),
                "Flores", List.of(), FulfillmentType.PICKUP, null, null)))
                .isInstanceOf(FieldValidationException.class).hasMessageContaining("até 1 massa");
    }

    // 6, 7, 8 e 14: outros doces com preço, subtotal, taxa por modalidade e preços históricos.
    @Test
    void sweetsHaveSubtotalsDeliveryFeesDependOnTheModalityAndHistoricalPricesAreKept() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        long category = idOf("insert into product_category (name, created_at, updated_at) values ('Brigadeiros " + suffix
                + "', now(), now()) returning id");
        long brigadeiro = idOf("insert into product (category_id, name, price, unit_label, min_quantity, status, "
                + "pickup_allowed, delivery_allowed, created_at, updated_at) values (" + category + ", 'Brigadeiro "
                + suffix + "', 2.50, 'unidade', 25, 'ACTIVE', true, true, now(), now()) returning id");
        List<OrderItemRequest> items = List.of(new OrderItemRequest(brigadeiro, 40, null));

        long pickup = create(request(null, null, null, null, null, items, FulfillmentType.PICKUP, null, null));
        var item = admin.detail(pickup).items().get(0);
        assertThat(item.unitPrice()).isEqualByComparingTo("2.50");
        assertThat(item.subtotal()).isEqualByComparingTo("100.00");
        assertThat(admin.detail(pickup).suggestedDeliveryFee()).isNull();

        long sweetsCity = create(request(null, null, null, null, null, items, FulfillmentType.DELIVERY,
                DeliveryZone.CITY, null));
        assertThat(admin.detail(sweetsCity).suggestedDeliveryFee()).isEqualByComparingTo("5.00");
        long cakeCity = create(request(sizeId("Alto 15 cm · 2 kg"), null, List.of(chocolate), fillings.subList(0, 1),
                "Flores", List.of(), FulfillmentType.DELIVERY, DeliveryZone.CITY, null));
        assertThat(admin.detail(cakeCity).suggestedDeliveryFee()).isEqualByComparingTo("20.00");
        long party = create(request(sizeId("Alto 15 cm · 2 kg"), null, List.of(chocolate), fillings.subList(0, 1),
                "Flores", List.of(), FulfillmentType.DELIVERY, DeliveryZone.PARTY_VENUE, null));
        assertThat(admin.detail(party).suggestedDeliveryFee()).as("R$ 30 só depois da confirmação").isNull();
        jdbc.update("update bakery_settings set party_venue_fee_confirmed = true");
        assertThat(admin.detail(party).suggestedDeliveryFee()).isEqualByComparingTo("30.00");
        long outside = create(request(null, null, null, null, null, items, FulfillmentType.DELIVERY,
                DeliveryZone.OUTSIDE_CITY, null));
        assertThat(admin.detail(outside).suggestedDeliveryFee()).as("fora da cidade: valor manual").isNull();
        assertThat(admin.detail(outside).deliveryZone()).isEqualTo(DeliveryZone.OUTSIDE_CITY);

        // Novos preços no catálogo não mudam pedidos já feitos.
        jdbc.update("update product set price = 3.00 where id = ?", brigadeiro);
        jdbc.update("update cake_size set base_price = 160.00 where name = 'Alto 15 cm · 2 kg'");
        try {
            assertThat(admin.detail(pickup).items().get(0).unitPrice()).isEqualByComparingTo("2.50");
            assertThat(admin.detail(cakeCity).sizePrice()).isEqualByComparingTo("140.00");
            long newer = create(request(sizeId("Alto 15 cm · 2 kg"), null, List.of(chocolate), fillings.subList(0, 1),
                    "Outra decoração", List.of(), FulfillmentType.PICKUP, null, null));
            assertThat(admin.detail(newer).sizePrice()).isEqualByComparingTo("160.00");
        } finally {
            jdbc.update("update cake_size set base_price = 140.00 where name = 'Alto 15 cm · 2 kg'");
        }
    }

    // 9: várias encomendas no mesmo dia e horário, sem limite padrão por horário.
    @Test
    void manyOrdersShareTheSameDateAndTimeWhenTheSlotHasNoLimit() {
        jdbc.update("update weekly_slot set capacity = null where day_of_week = 2 and start_time = '14:00'");
        List<Long> confirmed = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            var order = fixtures.acceptedOrder(catalog);
            payments.register(order.id(), new RegisterRequest(Payment.Kind.DEPOSIT, Payment.Method.PIX,
                    new BigDecimal("82.50"), null, false), ownerId);
            confirmed.add(order.id());
        }
        assertThat(confirmed).allSatisfy(id -> assertThat(jdbc.queryForObject(
                "select commercial_status from cake_order where id = ?", String.class, id)).isEqualTo("CONFIRMED"));
        assertThat(jdbc.queryForObject("select reserved_count from slot_occupancy where slot_date = ? and slot_time = ?",
                Integer.class, OrderFixtures.TUESDAY, LocalTime.of(14, 0))).isGreaterThanOrEqualTo(5);
    }

    // 10: data bloqueada some da agenda pública e não aceita solicitação.
    @Test
    void blockedDateIsNotOfferedNorAccepted() throws Exception {
        new ScheduleTestSupport(jdbc).block(OrderFixtures.TUESDAY, null);
        mockMvc.perform(get("/api/public/availability"))
                .andExpect(jsonPath("$.days[?(@.date == '2026-11-03')]").isEmpty());
        assertThatThrownBy(() -> cakeOrder("Baixo 20 cm · 2 kg"))
                .isInstanceOf(FieldValidationException.class).hasMessageContaining("não está mais disponível");
    }

    // 11 e 12: lembrete antes do prazo (painel + e-mail da cliente) e cancelamento depois.
    @Test
    void clientIsRemindedBeforeTheDeadlineAndUnpaidOrderIsCancelledAfter() throws Exception {
        jdbc.update("update bakery_settings set deposit_reminder_hours = 6");
        mail.clear();
        var order = fixtures.acceptedOrder(catalog);
        jdbc.update("update cake_order set customer_email = 'cliente@exemplo.test' where id = ?", order.id());

        clock.advance(Duration.ofHours(41)); // prazo de 48 h após o aceite; lembrete 6 h antes
        deadlines.runAll();
        assertThat(mail.sent()).noneMatch(m -> List.of(m.getTo()).contains("cliente@exemplo.test"));

        clock.advance(Duration.ofHours(2));
        deadlines.runAll();
        deadlines.runAll();
        var toClient = mail.sent().stream().filter(m -> List.of(m.getTo()).contains("cliente@exemplo.test")).toList();
        assertThat(toClient).singleElement().satisfies(m -> {
            assertThat(m.getSubject()).isEqualTo("Lembrete: pagamento do sinal da sua encomenda");
            assertThat(m.getText()).contains("R$ 82,50", order.trackingCode(), "/acompanhar");
        });
        assertThat(jdbc.queryForObject("select count(*) from order_history where order_id = ? and event_type = "
                + "'DEPOSIT_REMINDER'", Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notification where order_id = ? and type = "
                + "'DEPOSIT_DEADLINE_NEAR'", Integer.class, order.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select commercial_status from cake_order where id = ?", String.class,
                order.id())).isEqualTo("AWAITING_DEPOSIT");

        clock.advance(Duration.ofHours(6));
        deadlines.runAll();
        assertThat(jdbc.queryForObject("select commercial_status from cake_order where id = ?", String.class,
                order.id())).isEqualTo("CANCELLED");
    }

    // 13: aviso de pontualidade editável pela proprietária e público para a cliente.
    @Test
    void punctualityNoticeIsEditableAndShownToClients() throws Exception {
        mockMvc.perform(get("/api/public/bakery"))
                .andExpect(jsonPath("$.punctualityNotice").value(org.hamcrest.Matchers.containsString("horário combinado")));
        Cookie owner = login(mockMvc, auth.createUser().getEmail(), PASSWORD);
        ObjectNode settings = (ObjectNode) json.readTree(mockMvc.perform(get("/api/admin/settings").cookie(owner))
                .andReturn().getResponse().getContentAsString());
        assertThat(settings.get("deliveryFeeSweets").asText()).isEqualTo("5.00");
        assertThat(settings.get("partyVenueFee").asText()).isEqualTo("30.00");
        String original = settings.get("punctualityNotice").asText();
        settings.put("punctualityNotice", "Chegue no horário combinado, por favor.");
        try {
            mockMvc.perform(put("/api/admin/settings").with(csrf()).cookie(owner)
                    .contentType(MediaType.APPLICATION_JSON).content(settings.toString()))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/public/bakery"))
                    .andExpect(jsonPath("$.punctualityNotice").value("Chegue no horário combinado, por favor."))
                    .andExpect(jsonPath("$.deliveryFeeSweets").value("5.00"))
                    .andExpect(jsonPath("$.partyVenueFee").doesNotExist());
        } finally {
            jdbc.update("update bakery_settings set punctuality_notice = ?", original);
        }
    }

    // Solicitação repetida (ex.: duplo clique) é recusada; uma diferente no mesmo horário é aceita.
    @Test
    void repeatedSubmissionIsRejected() {
        creation.setDuplicateWindow(Duration.ofMinutes(30));
        cakeOrder("Baixo 25 cm · 3 kg");
        assertThatThrownBy(() -> cakeOrder("Baixo 25 cm · 3 kg"))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((ConflictException) e).getCode()).isEqualTo("DUPLICATE_ORDER"));
        cakeOrder("Baixo 30 cm · 4 kg");
    }

    // O orçamento de um bolo da tabela parte do preço do tamanho e soma topo e entrega separados.
    @Test
    void quoteStartsFromTheTablePriceWithTopperAndDeliverySeparated() {
        long id = create(request(sizeId("2 andares · 5 kg"), null, List.of(chocolate), fillings.subList(0, 2),
                "Flores", List.of(), FulfillmentType.DELIVERY, DeliveryZone.CITY, null));
        var detail = admin.detail(id);
        quotes.saveDraft(id, new DraftRequest(List.of(
                new ItemRequest(QuoteItemKind.PRODUCT, "Bolo " + detail.sizeName(), null, BigDecimal.ONE, detail.sizePrice()),
                new ItemRequest(QuoteItemKind.EXTRA, "Decoração com flores", null, BigDecimal.ONE, new BigDecimal("35.00")),
                new ItemRequest(QuoteItemKind.TOPPER, "Topo", null, BigDecimal.ONE, new BigDecimal("25.00")),
                new ItemRequest(QuoteItemKind.DELIVERY, "Taxa de entrega", null, BigDecimal.ONE,
                        detail.suggestedDeliveryFee())), null, 48));
        var quote = quotes.send(id, ownerId);
        assertThat(quote.breakdown().products()).isEqualByComparingTo("400.00");
        assertThat(quote.breakdown().extras()).isEqualByComparingTo("35.00");
        assertThat(quote.breakdown().topper()).isEqualByComparingTo("25.00");
        assertThat(quote.breakdown().delivery()).isEqualByComparingTo("20.00");
        assertThat(quote.total()).isEqualByComparingTo("480.00");
        assertThat(quote.depositAmount()).isEqualByComparingTo("240.00");
    }

    private static JsonNode category(JsonNode catalog, String name) {
        for (JsonNode c : catalog.get("cakeCategories")) {
            if (c.get("name").asText().equals(name)) {
                return c;
            }
        }
        throw new AssertionError("Categoria não encontrada: " + name);
    }

    private static List<String> sizesOf(JsonNode catalog, String categoryName) {
        long id = category(catalog, categoryName).get("id").asLong();
        List<String> result = new ArrayList<>();
        for (JsonNode s : catalog.get("sizes")) {
            if (!s.get("categoryId").isNull() && s.get("categoryId").asLong() == id) {
                result.add(s.get("name").asText() + "|" + s.get("servings").asInt() + "|" + s.get("basePrice").asText());
            }
        }
        return result;
    }
}
