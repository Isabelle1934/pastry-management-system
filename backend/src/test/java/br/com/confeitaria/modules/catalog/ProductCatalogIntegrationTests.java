package br.com.confeitaria.modules.catalog;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.modules.order.service.OrderRateLimiter;
import br.com.confeitaria.modules.schedule.ScheduleTestSupport;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Catálogo de produtos, catálogo público, pedidos com itens do catálogo e delivery.
 * "Agora" = segunda 02/11/2026 09:00 (São Paulo); horários às terças e quintas 14:00.
 */
@IntegrationTest
class ProductCatalogIntegrationTests {

    private static final String TUESDAY = "2026-11-03";
    private static final String THURSDAY = "2026-11-05";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTestSupport support;

    @Autowired
    private AuthRateLimiter authRateLimiter;

    @Autowired
    private OrderRateLimiter orderRateLimiter;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    private Cookie session;
    private long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2026-11-02T12:00:00Z"));
        authRateLimiter.clear();
        orderRateLimiter.clear();
        ScheduleTestSupport schedule = new ScheduleTestSupport(jdbc);
        schedule.reset(24, 90);
        schedule.slot(2, "14:00", 3, true);
        schedule.slot(4, "14:00", 3, true);
        session = login(mockMvc, support.createUser().getEmail(), PASSWORD);
        categoryId = body(send("POST", "/api/admin/catalog/categories",
                "{\"name\":\"%s\",\"displayOrder\":1}".formatted(unique("Doces")))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    @AfterEach
    void restoreSettings() {
        jdbc.update("update bakery_settings set pickup_enabled = true, delivery_enabled = false, "
                + "custom_cake_enabled = true, custom_cake_delivery = false");
    }

    // --- Apoio ---

    private static String unique(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions send(String method, String path, String body) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(path);
            case "PUT" -> put(path);
            case "PATCH" -> patch(path);
            default -> throw new IllegalArgumentException(method);
        };
        return mockMvc.perform(builder.with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode createProduct(String name, String extra) throws Exception {
        return body(send("POST", "/api/admin/catalog/products", """
                {"categoryId":%d,"name":"%s","description":"Feito na hora","price":"3.50","unitLabel":"unidade",
                 "minQuantity":10%s}
                """.formatted(categoryId, name, extra)).andExpect(status().isCreated()));
    }

    private Map<String, Object> productOrder(long productId, int quantity) {
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("customerName", "Beatriz Lima");
        order.put("customerWhatsapp", "(11) 91234-5678");
        order.put("pickupDate", TUESDAY);
        order.put("pickupTime", "14:00");
        order.put("customCake", false);
        order.put("items", List.of(Map.of("productId", productId, "quantity", quantity)));
        return order;
    }

    private ResultActions submit(Map<String, Object> order) throws Exception {
        return mockMvc.perform(multipart("/api/public/orders")
                .file(new MockMultipartFile("order", "", MediaType.APPLICATION_JSON_VALUE,
                        json.writeValueAsString(order).getBytes(StandardCharsets.UTF_8)))
                .with(csrf()).with(AuthTestSupport.uniqueIp()));
    }

    private long orderIdOf(ResultActions created) throws Exception {
        String code = body(created).get("trackingCode").asText().replace("-", "");
        Long id = jdbc.queryForObject("select id from cake_order where tracking_code = ?", Long.class, code);
        return id == null ? -1 : id;
    }

    private static byte[] jpeg() {
        byte[] data = new byte[2048];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        data[3] = (byte) 0xE0;
        return data;
    }

    // --- Testes ---

    @Test
    void productManagementRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/admin/catalog/products")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/catalog/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsEditsAndChangesStatusWithoutDeleting() throws Exception {
        String name = unique("Brigadeiro gourmet");
        JsonNode created = createProduct(name, ",\"availableDays\":[2,4],\"deliveryAllowed\":true");
        long id = created.get("id").asLong();
        assertThat(created.get("price").asText()).isEqualTo("3.50");
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.get("availableDays").toString()).isEqualTo("[2,4]");

        // Nome repetido (sem diferenciar maiúsculas) é recusado.
        send("POST", "/api/admin/catalog/products", """
                {"categoryId":%d,"name":"%s"}
                """.formatted(categoryId, name.toUpperCase())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));

        // Preço vazio = sob orçamento.
        JsonNode edited = body(send("PUT", "/api/admin/catalog/products/" + id, """
                {"categoryId":%d,"name":"%s","price":null,"minQuantity":20,"version":%d}
                """.formatted(categoryId, name, created.get("version").asLong())).andExpect(status().isOk()));
        assertThat(edited.get("price").isNull()).isTrue();
        assertThat(edited.get("availableDays").size()).isZero();

        // Edição concorrente.
        send("PUT", "/api/admin/catalog/products/" + id, """
                {"categoryId":%d,"name":"%s","version":%d}
                """.formatted(categoryId, name, created.get("version").asLong()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        send("PATCH", "/api/admin/catalog/products/" + id + "/status",
                "{\"status\":\"INACTIVE\",\"version\":%d}".formatted(edited.get("version").asLong()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        assertThat(jdbc.queryForObject("select count(*) from product where id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void invalidProductIsRejected() throws Exception {
        send("POST", "/api/admin/catalog/products", """
                {"categoryId":%d,"name":"%s","price":"-1","availableDays":[8]}
                """.formatted(categoryId, unique("X"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("price")));
        send("POST", "/api/admin/catalog/products", """
                {"categoryId":%d,"name":"%s","pickupAllowed":false,"deliveryAllowed":false}
                """.formatted(categoryId, unique("X"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pickupAllowed"));
        send("POST", "/api/admin/catalog/products", """
                {"categoryId":%d,"name":"%s","availableFrom":"2026-12-10","availableUntil":"2026-12-01"}
                """.formatted(categoryId, unique("X"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("availableUntil"));
    }

    @Test
    void publicCatalogShowsOnlyVisibleProductsAndReflectsChanges() throws Exception {
        JsonNode active = createProduct(unique("Trufa"), "");
        JsonNode unavailable = createProduct(unique("Bombom"), "");
        JsonNode inactive = createProduct(unique("Cupcake"), "");
        JsonNode expired = createProduct(unique("Panetone"), ",\"seasonal\":true,\"availableUntil\":\"2026-10-31\"");
        send("PATCH", "/api/admin/catalog/products/" + unavailable.get("id").asLong() + "/status",
                "{\"status\":\"UNAVAILABLE\",\"version\":0}").andExpect(status().isOk());
        send("PATCH", "/api/admin/catalog/products/" + inactive.get("id").asLong() + "/status",
                "{\"status\":\"INACTIVE\",\"version\":0}").andExpect(status().isOk());

        mockMvc.perform(get("/api/public/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customCakeEnabled").value(true))
                .andExpect(jsonPath("$.products[*].id", hasItem(active.get("id").asInt())))
                .andExpect(jsonPath("$.products[?(@.id == %d)].available".formatted(unavailable.get("id").asLong()))
                        .value(false))
                .andExpect(jsonPath("$.products[*].id", not(hasItem(inactive.get("id").asInt()))))
                .andExpect(jsonPath("$.products[*].id", not(hasItem(expired.get("id").asInt()))))
                .andExpect(jsonPath("$.categories[*].id", hasItem((int) categoryId)));

        // Categoria desativada esconde os produtos dela.
        send("PATCH", "/api/admin/catalog/categories/" + categoryId + "/active",
                "{\"active\":false,\"version\":0}").andExpect(status().isOk());
        mockMvc.perform(get("/api/public/catalog"))
                .andExpect(jsonPath("$.products[*].id", not(hasItem(active.get("id").asInt()))))
                .andExpect(jsonPath("$.categories[*].id", not(hasItem((int) categoryId))));
    }

    @Test
    void photosAreValidatedServedPubliclyAndRemovedLogically() throws Exception {
        JsonNode product = createProduct(unique("Brownie"), "");
        long id = product.get("id").asLong();

        mockMvc.perform(multipart("/api/admin/catalog/products/" + id + "/photos")
                .file(new MockMultipartFile("photo", "x.jpg", "image/jpeg", "<html>".getBytes(StandardCharsets.UTF_8)))
                .with(csrf()).cookie(session))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("photo"));

        JsonNode withPhoto = body(mockMvc.perform(multipart("/api/admin/catalog/products/" + id + "/photos")
                .file(new MockMultipartFile("photo", "brownie.jpg", "image/jpeg", jpeg()))
                .with(csrf()).cookie(session)).andExpect(status().isCreated()));
        long fileId = withPhoto.get("photoIds").get(0).asLong();

        mockMvc.perform(get("/api/public/products/" + id + "/photos/" + fileId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"));
        // A foto não pode ser acessada por outro produto.
        JsonNode other = createProduct(unique("Cocada"), "");
        mockMvc.perform(get("/api/public/products/" + other.get("id").asLong() + "/photos/" + fileId))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/admin/catalog/products/" + id + "/photos/" + fileId).with(csrf()).cookie(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photoIds.length()").value(0));
        mockMvc.perform(get("/api/public/products/" + id + "/photos/" + fileId)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select deleted_at is not null from stored_file where id = ?", Boolean.class,
                fileId)).isTrue();
    }

    @Test
    void orderWithCatalogItemsKeepsTheOriginalProductData() throws Exception {
        String name = unique("Beijinho");
        JsonNode product = createProduct(name, "");
        long productId = product.get("id").asLong();

        long orderId = orderIdOf(submit(productOrder(productId, 50)).andExpect(status().isCreated()));

        // Depois da encomenda, a proprietária muda nome e preço e desativa o produto.
        JsonNode edited = body(send("PUT", "/api/admin/catalog/products/" + productId, """
                {"categoryId":%d,"name":"%s","price":"9.90","version":0}
                """.formatted(categoryId, name + " novo")).andExpect(status().isOk()));
        send("PATCH", "/api/admin/catalog/products/" + productId + "/status",
                "{\"status\":\"INACTIVE\",\"version\":%d}".formatted(edited.get("version").asLong()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/orders/" + orderId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customCake").value(false))
                .andExpect(jsonPath("$.sizeName").doesNotExist())
                .andExpect(jsonPath("$.items[0].name").value(name))
                .andExpect(jsonPath("$.items[0].unitPrice").value("3.50"))
                .andExpect(jsonPath("$.items[0].quantity").value(50))
                .andExpect(jsonPath("$.fulfillmentType").value("PICKUP"));
        mockMvc.perform(get("/api/admin/orders").cookie(session).param("q", "91234"))
                .andExpect(jsonPath("$.content[?(@.id == %d)].summary".formatted(orderId)).value(name + " ×50"));

        // Produto inativo não pode ser escolhido em novas encomendas.
        submit(productOrder(productId, 50)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("items"));
    }

    @Test
    void catalogRulesAreValidatedOnTheServer() throws Exception {
        long onlyThursday = createProduct(unique("Pão de mel"), ",\"availableDays\":[4]").get("id").asLong();
        long notOrderable = createProduct(unique("Bolo de pote"), ",\"orderable\":false").get("id").asLong();
        long regular = createProduct(unique("Cajuzinho"), "").get("id").asLong();

        record Case(String field, Map<String, Object> changes) {
        }
        List<Case> cases = List.of(
                new Case("pickupDate", Map.of("items", List.of(Map.of("productId", onlyThursday, "quantity", 10)))),
                new Case("items", Map.of("items", List.of(Map.of("productId", notOrderable, "quantity", 10)))),
                new Case("items", Map.of("items", List.of(Map.of("productId", regular, "quantity", 5)))), // mínimo 10
                new Case("items", Map.of("items", List.of(Map.of("productId", regular, "quantity", 10),
                        Map.of("productId", regular, "quantity", 10)))),
                new Case("items", Map.of("items", List.of())),
                new Case("topperRequested", Map.of("topperRequested", true, "topperTheme", "Flores")),
                new Case("fulfillmentType", Map.of("fulfillmentType", "DELIVERY", "deliveryAddress", "Rua A, 1")));
        for (Case c : cases) {
            Map<String, Object> order = productOrder(regular, 10);
            order.putAll(c.changes());
            submit(order).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value(c.field()));
        }

        Map<String, Object> thursday = productOrder(onlyThursday, 10);
        thursday.put("pickupDate", THURSDAY);
        submit(thursday).andExpect(status().isCreated());
    }

    @Test
    void deliveryFollowsSettingsAndProductRules() throws Exception {
        long deliverable = createProduct(unique("Kit festa"), ",\"deliveryAllowed\":true").get("id").asLong();
        long pickupOnly = createProduct(unique("Torta"), "").get("id").asLong();
        jdbc.update("update bakery_settings set delivery_enabled = true, delivery_info = 'Taxa conforme o bairro'");

        mockMvc.perform(get("/api/public/bakery"))
                .andExpect(jsonPath("$.deliveryEnabled").value(true))
                .andExpect(jsonPath("$.deliveryInfo").value("Taxa conforme o bairro"));

        Map<String, Object> noAddress = productOrder(deliverable, 10);
        noAddress.put("fulfillmentType", "DELIVERY");
        submit(noAddress).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("deliveryAddress"));

        Map<String, Object> wrongProduct = productOrder(pickupOnly, 10);
        wrongProduct.put("fulfillmentType", "DELIVERY");
        wrongProduct.put("deliveryAddress", "Rua das Flores, 10");
        submit(wrongProduct).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("fulfillmentType"));

        Map<String, Object> ok = productOrder(deliverable, 10);
        ok.put("fulfillmentType", "DELIVERY");
        ok.put("deliveryAddress", "Rua das Flores, 10");
        ok.put("deliveryDistrict", "Centro");
        long orderId = orderIdOf(submit(ok).andExpect(status().isCreated()));
        mockMvc.perform(get("/api/admin/orders/" + orderId).cookie(session))
                .andExpect(jsonPath("$.fulfillmentType").value("DELIVERY"))
                .andExpect(jsonPath("$.deliveryAddress").value("Rua das Flores, 10"))
                .andExpect(jsonPath("$.deliveryDistrict").value("Centro"));
    }

    @Test
    void customersAndReportsAreAvailableToTheOwner() throws Exception {
        long productId = createProduct(unique("Olho de sogra"), "").get("id").asLong();
        String whatsapp = "(21) 9" + (10000000 + (int) (Math.random() * 89999999));
        Map<String, Object> order = productOrder(productId, 30);
        order.put("customerWhatsapp", whatsapp);
        submit(order).andExpect(status().isCreated());
        String digits = whatsapp.replaceAll("\\D", "");

        mockMvc.perform(get("/api/admin/customers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/customers").cookie(session).param("q", digits))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Beatriz Lima"))
                .andExpect(jsonPath("$.content[0].orders").value(1))
                .andExpect(jsonPath("$.content[0].totalPaid").value("0.00"));

        mockMvc.perform(get("/api/admin/reports").cookie(session).param("from", "2026-11-01").param("to", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.statusCounts.PENDING_REVIEW").isNumber())
                .andExpect(jsonPath("$.totals.orders").isNumber());
        mockMvc.perform(get("/api/admin/reports").cookie(session).param("from", "2026-11-30").param("to", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }
}
