package br.com.confeitaria.modules.auth;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.login;
import static br.com.confeitaria.support.AuthTestSupport.loginJson;
import static br.com.confeitaria.support.AuthTestSupport.uniqueIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.confeitaria.modules.auth.entity.AdminUser;
import br.com.confeitaria.modules.auth.repository.AdminUserRepository;
import br.com.confeitaria.modules.auth.service.AuthRateLimiter;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.IntegrationTest;
import br.com.confeitaria.support.MutableClock;
import jakarta.servlet.http.Cookie;

@IntegrationTest
class LoginIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthTestSupport support;

    @Autowired
    private AdminUserRepository users;

    @Autowired
    private AuthRateLimiter rateLimiter;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        rateLimiter.clear();
        clock.reset();
    }

    @Test
    void validCredentialsCreateSessionAndMeReturnsTheUser() throws Exception {
        AdminUser user = support.createUser();

        Cookie session = login(mockMvc, user.getEmail(), PASSWORD);

        mockMvc.perform(get("/api/admin/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.name").value("Dona Teste"))
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void emailIsCaseInsensitiveOnLogin() throws Exception {
        AdminUser user = support.createUser();
        login(mockMvc, user.getEmail().toUpperCase(), PASSWORD);
    }

    @Test
    void sessionCookieIsHttpOnlySecureAndSameSiteLax() throws Exception {
        AdminUser user = support.createUser();

        var response = mockMvc.perform(post("/api/admin/auth/login").with(csrf()).with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String sessionHeader = response.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith("SESSION=")).findFirst().orElseThrow();
        assertThat(sessionHeader).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/");
    }

    @Test
    void wrongPasswordUnknownEmailAndDisabledAccountGetTheSameAnswer() throws Exception {
        AdminUser user = support.createUser();
        AdminUser disabled = support.createUser();
        disabled.disable();
        users.saveAndFlush(disabled);

        String[][] attempts = {
                {user.getEmail(), "senha-errada-123"},
                {"ninguem@exemplo.com", PASSWORD},
                {disabled.getEmail(), PASSWORD},
        };
        for (String[] attempt : attempts) {
            mockMvc.perform(post("/api/admin/auth/login").with(csrf()).with(uniqueIp())
                            .contentType(MediaType.APPLICATION_JSON).content(loginJson(attempt[0], attempt[1])))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("E-mail ou senha incorretos."));
        }
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        AdminUser user = support.createUser();

        mockMvc.perform(post("/api/admin/auth/login").with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(header().doesNotExist("WWW-Authenticate"));

        mockMvc.perform(get("/api/admin/qualquer-coisa"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgedSessionCookieIsNotAccepted() throws Exception {
        mockMvc.perform(get("/api/admin/auth/me").cookie(new Cookie("SESSION", "ZmFrZS1zZXNzaW9u")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tooManyFailuresBlockTheEmailEvenWithTheCorrectPassword() throws Exception {
        AdminUser user = support.createUser();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/admin/auth/login").with(csrf()).with(uniqueIp())
                            .contentType(MediaType.APPLICATION_JSON).content(loginJson(user.getEmail(), "errada-" + i + "-xxxx")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/admin/auth/login").with(csrf()).with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));

        clock.advance(java.time.Duration.ofMinutes(16));
        login(mockMvc, user.getEmail(), PASSWORD);
    }

    @Test
    void tooManyFailuresFromOneIpBlockThatIp() throws Exception {
        RequestPostProcessor sameIp = uniqueIp();
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/admin/auth/login").with(csrf()).with(sameIp)
                            .contentType(MediaType.APPLICATION_JSON).content(loginJson("tentativa" + i + "@exemplo.com", "qualquer-senha")))
                    .andExpect(status().isUnauthorized());
        }
        AdminUser user = support.createUser();
        mockMvc.perform(post("/api/admin/auth/login").with(csrf()).with(sameIp)
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void sessionIdChangesOnEveryLogin() throws Exception {
        AdminUser user = support.createUser();
        Cookie first = login(mockMvc, user.getEmail(), PASSWORD);

        var result = mockMvc.perform(post("/api/admin/auth/login").with(csrf()).with(uniqueIp()).cookie(first)
                        .contentType(MediaType.APPLICATION_JSON).content(loginJson(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie second = result.getResponse().getCookie("SESSION");

        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        mockMvc.perform(get("/api/admin/auth/me").cookie(first)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/auth/me").cookie(second)).andExpect(status().isOk());
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        AdminUser user = support.createUser();
        Cookie session = login(mockMvc, user.getEmail(), PASSWORD);

        mockMvc.perform(post("/api/admin/auth/logout").with(csrf()).cookie(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/api/public/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));
    }
}
