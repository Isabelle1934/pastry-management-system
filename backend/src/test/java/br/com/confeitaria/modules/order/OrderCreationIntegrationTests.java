package br.com.confeitaria.modules.order;

import static br.com.confeitaria.support.AuthTestSupport.uniqueIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.confeitaria.common.audit.ActorType;
import br.com.confeitaria.modules.file.StorageProperties;
import br.com.confeitaria.modules.file.entity.FilePurpose;
import br.com.confeitaria.modules.file.service.FileStorageService;
import br.com.confeitaria.modules.order.service.OrderRateLimiter;
import br.com.confeitaria.modules.schedule.ScheduleTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;
import tools.jackson.databind.json.JsonMapper;

/**
 * "Agora" é segunda 02/11/2026 09:00 (São Paulo); há horário às terças 14:00, antecedência de 24 h.
 */
@IntegrationTest
class OrderCreationIntegrationTests {

    private static final String CODE_PATTERN = "^[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){3}$";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private OrderRateLimiter rateLimiter;

    @Autowired
    private StorageProperties storage;

    @Autowired
    private FileStorageService fileStorage;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JsonMapper json;

    private long sizeId;
    private long chocolate;
    private long baunilha;
    private long inactiveFlavor;
    private long brigadeiro;

    @BeforeEach
    void setUp() {
        clock.set(Instant.parse("2026-11-02T12:00:00Z"));
        rateLimiter.clear();
        ScheduleTestSupport schedule = new ScheduleTestSupport(jdbc);
        schedule.reset(24, 90);
        schedule.slot(2, "08:00", 2, true);
        schedule.slot(2, "14:00", 2, true);

        String suffix = " " + UUID.randomUUID().toString().substring(0, 8);
        sizeId = insert("insert into cake_size (name, base_price, max_flavors, max_fillings, created_at, updated_at) "
                + "values (?, 150, 2, 1, now(), now()) returning id", "Médio" + suffix);
        chocolate = insert(optionSql("cake_flavor", true), "Chocolate" + suffix);
        baunilha = insert(optionSql("cake_flavor", true), "Baunilha" + suffix);
        inactiveFlavor = insert(optionSql("cake_flavor", false), "Limão" + suffix);
        brigadeiro = insert(optionSql("cake_filling", true), "Brigadeiro" + suffix);
    }

    private long insert(String sql, String name) {
        Long id = jdbc.queryForObject(sql, Long.class, name);
        return id == null ? -1 : id;
    }

    private static String optionSql(String table, boolean active) {
        return "insert into " + table + " (name, active, created_at, updated_at) values (?, " + active
                + ", now(), now()) returning id";
    }

    // --- Arquivos de exemplo (conteúdo real mínimo de cada formato) ---

    private static byte[] jpeg() {
        byte[] data = new byte[2048];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        data[3] = (byte) 0xE0;
        return data;
    }

    private static byte[] png() {
        byte[] data = new byte[1024];
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(header, 0, data, 0, header.length);
        return data;
    }

    private static MockMultipartFile photo(String name, byte[] content) {
        // O Content-Type enviado é propositalmente "image/jpeg": o servidor deve ignorá-lo.
        return new MockMultipartFile("referencePhotos", name, "image/jpeg", content);
    }

    private Map<String, Object> validOrder() {
        Map<String, Object> order = new java.util.LinkedHashMap<>();
        order.put("customerName", "  Ana   Souza ");
        order.put("customerWhatsapp", "(11) 98765-4321");
        order.put("pickupDate", "2026-11-03");
        order.put("pickupTime", "14:00");
        order.put("sizeId", sizeId);
        order.put("flavorIds", List.of(baunilha, chocolate));
        order.put("fillingIds", List.of(brigadeiro));
        order.put("decoration", "Flores em tons de rosa");
        order.put("cakeText", "Feliz aniversário, Ana!");
        order.put("notes", "Sem corante azul");
        order.put("topperRequested", true);
        order.put("topperTheme", "Jardim");
        order.put("topperName", "Ana");
        order.put("topperAge", 30);
        return order;
    }

    private ResultActions submit(Map<String, Object> order, RequestPostProcessor ip, MockMultipartFile... files)
            throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/public/orders");
        request.file(new MockMultipartFile("order", "", MediaType.APPLICATION_JSON_VALUE,
                json.writeValueAsString(order).getBytes(StandardCharsets.UTF_8)));
        for (MockMultipartFile file : files) {
            request.file(file);
        }
        return mockMvc.perform(request.with(csrf()).with(ip));
    }

    private ResultActions submit(Map<String, Object> order, MockMultipartFile... files) throws Exception {
        return submit(order, uniqueIp(), files);
    }

    private Set<Path> filesOnDisk() {
        Path root = storage.uploadDir().toAbsolutePath();
        if (!Files.exists(root)) {
            return Set.of();
        }
        try (var stream = Files.walk(root)) {
            return new HashSet<>(stream.filter(Files::isRegularFile).toList());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    // --- Cenários ---

    @Test
    void createsTheOrderWithPhotosTopperAndHistory() throws Exception {
        String body = submit(validOrder(), photo("referência bolo.jpg", jpeg()), photo("../../outra.png", png()),
                new MockMultipartFile("topperPhoto", "topo.jpg", "image/jpeg", jpeg()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingCode").value(matchesPattern(CODE_PATTERN)))
                .andReturn().getResponse().getContentAsString();
        String code = json.readTree(body).get("trackingCode").asText().replace("-", "");

        Map<String, Object> order = jdbc.queryForMap("select * from cake_order where tracking_code = ?", code);
        assertThat(order).containsEntry("customer_name", "Ana Souza")
                .containsEntry("customer_whatsapp", "+5511987654321")
                .containsEntry("commercial_status", "PENDING_REVIEW")
                .containsEntry("production_status", "NOT_STARTED")
                .containsEntry("payment_status", "NONE")
                .containsEntry("topper_requested", true)
                .containsEntry("topper_theme", "Jardim")
                .containsEntry("cake_text", "Feliz aniversário, Ana!");
        long orderId = ((Number) order.get("id")).longValue();

        assertThat(jdbc.queryForList("select name_snapshot from order_flavor where order_id = ? order by position",
                String.class, orderId)).hasSize(2).first().asString().startsWith("Baunilha");

        List<Map<String, Object>> files = jdbc.queryForList(
                "select * from stored_file where order_id = ? order by id", orderId);
        assertThat(files).hasSize(3);
        assertThat(files).extracting(f -> f.get("content_type")).containsExactly("image/jpeg", "image/png", "image/jpeg");
        assertThat(files).extracting(f -> f.get("purpose"))
                .containsExactly("ORDER_REFERENCE", "ORDER_REFERENCE", "TOPPER_REFERENCE");
        assertThat(files).extracting(f -> f.get("original_name")).contains("referência bolo.jpg", "outra.png");
        for (Map<String, Object> file : files) {
            String key = (String) file.get("storage_key");
            assertThat(key).matches("[0-9a-f]{2}/[0-9a-f-]{36}\\.bin").doesNotContain("bolo");
            assertThat(storage.uploadDir().resolve(key)).exists();
        }

        assertThat(jdbc.queryForObject("select event_type from order_history where order_id = ?", String.class, orderId))
                .isEqualTo("CREATED");
        assertThat(jdbc.queryForObject("select count(*) from slot_occupancy where reserved_count > 0", Integer.class))
                .as("a solicitação não ocupa vaga").isZero();
    }

    @Test
    void ordersWithoutPhotosOrTopperAreAccepted() throws Exception {
        Map<String, Object> order = validOrder();
        order.put("topperRequested", false);
        order.remove("topperTheme");
        order.remove("topperName");
        order.remove("topperAge");
        order.remove("decoration");

        submit(order).andExpect(status().isCreated());
    }

    @Test
    void eachOrderGetsADifferentCode() throws Exception {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            String body = submit(validOrder()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            codes.add(json.readTree(body).get("trackingCode").asText());
        }
        assertThat(codes).hasSize(3);
    }

    @Test
    void businessRulesAreValidatedOnTheServer() throws Exception {
        record Case(String field, Map<String, Object> changes) {
        }
        List<Case> cases = List.of(
                new Case("customerWhatsapp", Map.of("customerWhatsapp", "1234")),
                new Case("pickupTime", Map.of("pickupTime", "08:00")),          // antes da antecedência mínima
                new Case("pickupTime", Map.of("pickupTime", "15:00")),          // horário inexistente
                new Case("pickupTime", Map.of("pickupDate", "2026-11-04")),     // quarta: sem horários
                new Case("flavorIds", Map.of("flavorIds", List.of(chocolate, baunilha, inactiveFlavor))), // acima do limite
                new Case("flavorIds", Map.of("flavorIds", List.of(inactiveFlavor))),
                new Case("flavorIds", Map.of("flavorIds", List.of(chocolate, chocolate))),
                new Case("fillingIds", Map.of("fillingIds", List.of())),
                new Case("sizeId", Map.of("sizeId", 999_999)),
                new Case("topperTheme", Map.of("topperTheme", " ")),
                new Case("customerName", Map.of("customerName", "A")));

        for (Case c : cases) {
            Map<String, Object> order = validOrder();
            order.putAll(c.changes());
            submit(order)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].field").value(c.field()));
        }
        assertThat(jdbc.queryForObject("select count(*) from cake_order where size_id = ?", Integer.class, sizeId)).isZero();
    }

    @Test
    void fileContentIsVerifiedAndNothingIsStoredWhenRejected() throws Exception {
        Set<Path> before = filesOnDisk();

        submit(validOrder(), photo("bolo.jpg", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("referencePhotos"));
        submit(validOrder(), photo("comprovante.pdf", "%PDF-1.7 conteúdo".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());
        submit(validOrder(), photo("foto.jpg", png()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("extensão")));
        submit(validOrder(), photo("a.jpg", jpeg()), photo("b.jpg", jpeg()), photo("c.jpg", jpeg()), photo("d.jpg", jpeg()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Envie no máximo 3 fotos de referência."));
        byte[] big = Arrays.copyOf(jpeg(), 5 * 1024 * 1024 + 1);
        submit(validOrder(), photo("grande.jpg", big))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Cada arquivo pode ter no máximo 5 MB."));

        Map<String, Object> withoutTopper = validOrder();
        withoutTopper.put("topperRequested", false);
        withoutTopper.remove("topperTheme");
        submit(withoutTopper, new MockMultipartFile("topperPhoto", "topo.jpg", "image/jpeg", jpeg()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("topperPhoto"));

        assertThat(filesOnDisk()).isEqualTo(before);
    }

    @Test
    void storedFileIsDeletedFromDiskIfTheTransactionIsRolledBack() throws Exception {
        String body = submit(validOrder()).andReturn().getResponse().getContentAsString();
        long orderId = jdbc.queryForObject("select id from cake_order where tracking_code = ?", Long.class,
                json.readTree(body).get("trackingCode").asText().replace("-", ""));
        Set<Path> before = filesOnDisk();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            var valid = fileStorage.validate(photo("x.jpg", jpeg()), FilePurpose.ORDER_REFERENCE, "f");
            fileStorage.store(valid, orderId, FilePurpose.ORDER_REFERENCE, ActorType.CLIENT);
            assertThat(filesOnDisk()).hasSize(before.size() + 1);
            throw new IllegalStateException("falha depois de gravar");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(filesOnDisk()).isEqualTo(before);
    }

    @Test
    void submissionsAreLimitedPerIp() throws Exception {
        RequestPostProcessor sameIp = uniqueIp();
        for (int i = 0; i < 5; i++) {
            submit(validOrder(), sameIp).andExpect(status().isCreated());
        }
        submit(validOrder(), sameIp)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void csrfTokenAndJsonPartAreRequired() throws Exception {
        mockMvc.perform(multipart("/api/public/orders")
                        .file(new MockMultipartFile("order", "", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes()))
                        .with(uniqueIp()))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/public/orders").with(csrf()).with(uniqueIp()))
                .andExpect(status().isBadRequest());
    }
}
