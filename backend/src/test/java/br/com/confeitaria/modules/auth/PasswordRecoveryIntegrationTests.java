package br.com.confeitaria.modules.auth;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static br.com.confeitaria.support.AuthTestSupport.loginJson;
import static br.com.confeitaria.support.AuthTestSupport.uniqueIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.confeitaria.modules.auth.entity.AdminUser;
import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;
import br.com.confeitaria.support.RecordingMailSender;
import jakarta.servlet.http.Cookie;

@IntegrationTest
class PasswordRecoveryIntegrationTests {

    private static final Pattern TOKEN_IN_LINK = Pattern.compile("/admin/redefinir-senha#token=([A-Za-z0-9_-]{43})");
    private static final String NEW_PASSWORD = "uma-nova-senha-segura";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTestSupport support;

    @Autowired
    private RecordingMailSender mail;

    @Autowired
    private AuthRateLimiter rateLimiter;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        rateLimiter.clear();
        clock.reset();
        mail.clear();
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/password/forgot").with(csrf()).with(uniqueIp())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/api/admin/auth/password/reset").with(csrf()).with(uniqueIp())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, newPassword)));
    }

    private String requestTokenFor(AdminUser user) throws Exception {
        forgot(user.getEmail()).andExpect(status().isAccepted());
        var messages = mail.awaitMessages(1, Duration.ofSeconds(5));
        assertThat(messages).hasSize(1);
        Matcher matcher = TOKEN_IN_LINK.matcher(messages.getFirst().getText());
        assertThat(matcher.find()).as("link com token no e-mail").isTrue();
        return matcher.group(1);
    }

    @Test
    void forgotPasswordEmailsALinkAndStoresOnlyTheHash() throws Exception {
        AdminUser user = support.createUser();

        String token = requestTokenFor(user);

        SimpleMailMessage message = mail.sent().getFirst();
        assertThat(message.getTo()).containsExactly(user.getEmail());
        assertThat(message.getSubject()).isEqualTo("Redefinição de senha");
        assertThat(message.getText()).startsWith("Olá, Dona Teste.").contains("http://localhost:5173/admin/redefinir-senha#token=");

        String storedHash = jdbc.queryForObject(
                "select token_hash from password_reset_token where admin_user_id = ? and used_at is null",
                String.class, user.getId());
        assertThat(storedHash).hasSize(64).isNotEqualTo(token).doesNotContain(token);
    }

    @Test
    void unknownEmailGetsTheSameAnswerAndNoEmail() throws Exception {
        AdminUser user = support.createUser();
        String knownAnswer = forgot(user.getEmail()).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        mail.awaitMessages(1, Duration.ofSeconds(5));
        mail.clear();

        String unknownAnswer = forgot("nao-cadastrado@exemplo.com").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownAnswer).isEqualTo(knownAnswer);
        assertThat(mail.awaitMessages(1, Duration.ofMillis(500))).isEmpty();
    }

    @Test
    void resetChangesThePasswordAndEndsExistingSessions() throws Exception {
        AdminUser user = support.createUser();
        Cookie oldSession = login(mockMvc, user.getEmail(), PASSWORD);
        String token = requestTokenFor(user);

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/auth/me").cookie(oldSession)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/auth/login").with(csrf()).with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized());
        login(mockMvc, user.getEmail(), NEW_PASSWORD);
    }

    @Test
    void tokenCanBeUsedOnlyOnce() throws Exception {
        AdminUser user = support.createUser();
        String token = requestTokenFor(user);

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
        reset(token, "outra-senha-qualquer").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void newRequestInvalidatesThePreviousLink() throws Exception {
        AdminUser user = support.createUser();
        String first = requestTokenFor(user);
        mail.clear();
        String second = requestTokenFor(user);

        reset(first, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        AdminUser user = support.createUser();
        String token = requestTokenFor(user);

        clock.advance(Duration.ofMinutes(31));

        reset(token, NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void unknownTokenIsRejected() throws Exception {
        reset("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", NEW_PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void weakPasswordIsRejectedAndTheLinkRemainsValid() throws Exception {
        AdminUser user = support.createUser();
        String token = requestTokenFor(user);

        reset(token, "curta").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void requestsPerEmailAreLimitedSilently() throws Exception {
        AdminUser user = support.createUser();

        for (int i = 0; i < 4; i++) {
            forgot(user.getEmail()).andExpect(status().isAccepted());
        }

        assertThat(mail.awaitMessages(4, Duration.ofSeconds(2))).hasSize(3);
    }

    @Test
    void changePasswordRequiresTheCurrentOneAndEndsOtherSessions() throws Exception {
        AdminUser user = support.createUser();
        Cookie otherDevice = login(mockMvc, user.getEmail(), PASSWORD);
        Cookie thisDevice = login(mockMvc, user.getEmail(), PASSWORD);

        mockMvc.perform(post("/api/admin/auth/password/change").with(csrf()).cookie(thisDevice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"errada-errada\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));

        mockMvc.perform(post("/api/admin/auth/password/change").with(csrf()).cookie(thisDevice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/auth/me").cookie(thisDevice)).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/auth/me").cookie(otherDevice)).andExpect(status().isUnauthorized());
        login(mockMvc, user.getEmail(), NEW_PASSWORD);
    }
}
