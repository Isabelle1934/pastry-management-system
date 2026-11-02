package br.com.confeitaria.modules.catalog;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class CatalogIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTestSupport support;

    @Autowired
    private AuthRateLimiter rateLimiter;

    @Autowired
    private AuditService auditService;

    @Autowired
    private JsonMapper json;

    private Cookie session;

    @BeforeEach
    void setUp() throws Exception {
        rateLimiter.clear();
        session = login(mockMvc, support.createUser().getEmail(), PASSWORD);
    }

    private static String unique(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String sizeJson(String name, String price, int maxFlavors, int maxFillings, Long version) {
        return """
                {"name":"%s","description":"Serve bem","servings":20,"basePrice":"%s",
                 "maxFlavors":%d,"maxFillings":%d,"displayOrder":1%s}
                """.formatted(name, price, maxFlavors, maxFillings, version == null ? "" : ",\"version\":" + version);
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

    @Test
    void catalogManagementRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/admin/catalog/sizes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/public/catalog")).andExpect(status().isOk());
    }

    @Test
    void createsListsAndUpdatesASizeWithMoneyAsString() throws Exception {
        String name = unique("Médio");
        JsonNode created = body(send("POST", "/api/admin/catalog/sizes", sizeJson(name, "120.5", 2, 2, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.basePrice").value("120.50"))
                .andExpect(jsonPath("$.active").value(true)));
        long id = created.get("id").asLong();

        mockMvc.perform(get("/api/admin/catalog/sizes").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(name)));

        send("PUT", "/api/admin/catalog/sizes/" + id, sizeJson(name, "135.00", 2, 3, created.get("version").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePrice").value("135.00"))
                .andExpect(jsonPath("$.maxFillings").value(3));

        var history = auditService.historyOf("cake_size", id);
        assertThat(history).extracting("action").containsExactly("SIZE_CREATED", "SIZE_UPDATED");
        assertThat(history.get(1).getDetails()).containsKeys("basePrice", "maxFillings").doesNotContainKey("name");
    }

    @Test
    void invalidSizeIsRejectedWithFieldErrors() throws Exception {
        send("POST", "/api/admin/catalog/sizes", """
                {"name":" ","basePrice":"-1","maxFlavors":0,"maxFillings":11}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("basePrice")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("maxFlavors")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("maxFillings")));

        send("POST", "/api/admin/catalog/sizes", sizeJson(unique("P"), "10.999", 1, 1, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("basePrice"));
    }

    @Test
    void duplicateNamesAreRejectedIgnoringCase() throws Exception {
        String name = unique("Grande");
        send("POST", "/api/admin/catalog/sizes", sizeJson(name, "200", 3, 3, null)).andExpect(status().isCreated());

        send("POST", "/api/admin/catalog/sizes", sizeJson(name.toUpperCase(), "210", 3, 3, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"))
                .andExpect(jsonPath("$.detail").value("Já existe um tamanho com este nome."));
    }

    @Test
    void staleEditIsRejectedInsteadOfOverwriting() throws Exception {
        String name = unique("Mini");
        JsonNode created = body(send("POST", "/api/admin/catalog/sizes", sizeJson(name, "50", 1, 1, null)));
        long id = created.get("id").asLong();
        long loadedVersion = created.get("version").asLong();

        send("PUT", "/api/admin/catalog/sizes/" + id, sizeJson(name, "55", 1, 1, loadedVersion)).andExpect(status().isOk());

        send("PUT", "/api/admin/catalog/sizes/" + id, sizeJson(name, "60", 1, 1, loadedVersion))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void deactivatedItemsDisappearFromThePublicCatalog() throws Exception {
        String sizeName = unique("Andar");
        String flavorName = unique("Chocolate");
        JsonNode size = body(send("POST", "/api/admin/catalog/sizes", sizeJson(sizeName, "300", 2, 2, null)));
        JsonNode flavor = body(send("POST", "/api/admin/catalog/flavors",
                "{\"name\":\"" + flavorName + "\",\"displayOrder\":0}").andExpect(status().isCreated()));

        mockMvc.perform(get("/api/public/catalog"))
                .andExpect(jsonPath("$.sizes[*].name", hasItem(sizeName)))
                .andExpect(jsonPath("$.flavors[*].name", hasItem(flavorName)))
                .andExpect(jsonPath("$.sizes[0].active").doesNotExist())
                .andExpect(jsonPath("$.sizes[0].version").doesNotExist());

        send("PATCH", "/api/admin/catalog/sizes/" + size.get("id").asLong() + "/active",
                "{\"active\":false,\"version\":" + size.get("version").asLong() + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        send("PATCH", "/api/admin/catalog/flavors/" + flavor.get("id").asLong() + "/active",
                "{\"active\":false,\"version\":" + flavor.get("version").asLong() + "}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/catalog"))
                .andExpect(jsonPath("$.sizes[*].name", not(hasItem(sizeName))))
                .andExpect(jsonPath("$.flavors[*].name", not(hasItem(flavorName))));
        mockMvc.perform(get("/api/admin/catalog/sizes").cookie(session))
                .andExpect(jsonPath("$[*].name", hasItem(sizeName)));
    }

    @Test
    void flavorsAndFillingsHaveTheirOwnMessages() throws Exception {
        String name = unique("Ninho");
        send("POST", "/api/admin/catalog/fillings", "{\"name\":\"" + name + "\",\"description\":\"Creme de leite ninho\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Creme de leite ninho"));
        send("POST", "/api/admin/catalog/fillings", "{\"name\":\"" + name + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Já existe um recheio com este nome."));

        // O mesmo nome pode existir como massa (são listas independentes).
        send("POST", "/api/admin/catalog/flavors", "{\"name\":\"" + name + "\"}").andExpect(status().isCreated());

        mockMvc.perform(put("/api/admin/catalog/flavors/999999").with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"version\":0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Massa não encontrada."));
    }

    @Test
    void changesRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/api/admin/catalog/flavors").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sem CSRF\"}"))
                .andExpect(status().isForbidden());
    }
}
