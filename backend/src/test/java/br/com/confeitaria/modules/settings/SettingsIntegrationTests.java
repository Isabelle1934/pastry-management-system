package br.com.confeitaria.modules.settings;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.confeitaria.common.audit.AuditService;
import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
class SettingsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTestSupport support;

    @Autowired
    private AuthRateLimiter rateLimiter;

    @Autowired
    private AuditService auditService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    private Cookie session;

    @BeforeEach
    void setUp() throws Exception {
        rateLimiter.clear();
        session = login(mockMvc, support.createUser().getEmail(), PASSWORD);
    }

    private long currentVersion() throws Exception {
        String body = mockMvc.perform(get("/api/admin/settings").cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("version").asLong();
    }

    private ResultActions update(String whatsapp, String instagram, int validity, int warning, long version) throws Exception {
        String body = """
                {"name":"Doces da Ana","aboutText":"Bolos artesanais.","whatsappContact":"%s","instagram":"%s",
                 "address":"Rua das Flores, 10","pickupInstructions":"Tocar a campainha.","pixKey":"chave-pix-teste",
                 "pixHolderName":"Ana","quoteValidityHours":%d,"quoteExpiryWarningHours":%d,
                 "ownerNotificationEmail":"Avisos@Exemplo.com","version":%d}
                """.formatted(whatsapp, instagram, validity, warning, version);
        return mockMvc.perform(put("/api/admin/settings").with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void settingsRequireLoginButPublicInfoDoesNot() throws Exception {
        mockMvc.perform(get("/api/admin/settings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/public/bakery")).andExpect(status().isOk());
    }

    @Test
    void updateNormalizesContactsAndPublicInfoHidesPrivateFields() throws Exception {
        update("+55 (11) 99999-8888", "https://www.instagram.com/doces.da.ana/", 72, 24, currentVersion())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappContact").value("(11) 99999-8888"))
                .andExpect(jsonPath("$.instagram").value("doces.da.ana"))
                .andExpect(jsonPath("$.ownerNotificationEmail").value("avisos@exemplo.com"))
                .andExpect(jsonPath("$.quoteValidityHours").value(72));

        assertThat(jdbc.queryForObject("select whatsapp_contact from bakery_settings", String.class))
                .isEqualTo("+5511999998888");

        mockMvc.perform(get("/api/public/bakery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Doces da Ana"))
                .andExpect(jsonPath("$.whatsappLink").value("https://wa.me/5511999998888"))
                .andExpect(jsonPath("$.pixKey").doesNotExist())
                .andExpect(jsonPath("$.ownerNotificationEmail").doesNotExist())
                .andExpect(jsonPath("$.quoteValidityHours").doesNotExist());
    }

    @Test
    void auditRecordsWhichFieldsChangedWithoutPaymentValues() throws Exception {
        update("11999998888", "@ana", 48, 12, currentVersion()).andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/settings").with(csrf()).cookie(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Doces da Ana","pixKey":"nova-chave-secreta","quoteValidityHours":48,
                                 "quoteExpiryWarningHours":12,"version":%d}
                                """.formatted(currentVersion())))
                .andExpect(status().isOk());

        var last = auditService.historyOf("bakery_settings", 1).getLast();
        assertThat(last.getAction()).isEqualTo("SETTINGS_UPDATED");
        assertThat(last.getDetails()).containsEntry("pixKey", "alterado");
        assertThat(last.getDetails().toString()).doesNotContain("nova-chave-secreta");
    }

    @Test
    void invalidValuesAreRejected() throws Exception {
        long version = currentVersion();
        update("1234", "@ana", 48, 12, version)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("whatsappContact"));
        update("11999998888", "nome com espaço", 48, 12, version)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("instagram"));
        update("11999998888", "@ana", 24, 24, version)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quoteExpiryWarningHours"));
        update("11999998888", "@ana", 0, 12, version)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItem("quoteValidityHours")));
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        long version = currentVersion();
        update("11999998888", "@ana", 48, 12, version).andExpect(status().isOk());
        update("11999998888", "@ana", 50, 12, version)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void databaseKeepsASingleSettingsRowAndValidRules() {
        assertThatThrownBy(() -> jdbc.update("insert into bakery_settings (id) values (2)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update bakery_settings set quote_validity_hours = 10, quote_expiry_warning_hours = 10"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
