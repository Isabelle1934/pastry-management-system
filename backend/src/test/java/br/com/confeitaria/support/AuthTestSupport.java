package br.com.confeitaria.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.confeitaria.modules.auth.entity.AdminUser;
import br.com.confeitaria.modules.auth.repository.AdminUserRepository;
import jakarta.servlet.http.Cookie;

/** Utilitários para testes de autenticação: criação de usuárias e login real via HTTP. */
@Component
public class AuthTestSupport {

    public static final String PASSWORD = "senha-forte-de-teste";
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger(1);

    private final AdminUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AuthTestSupport(AdminUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public AdminUser createUser() {
        return createUser(false);
    }

    public AdminUser createUser(boolean mustChangePassword) {
        AdminUser user = new AdminUser("dona." + UUID.randomUUID() + "@exemplo.com", "Dona Teste",
                passwordEncoder.encode(PASSWORD), Instant.now());
        if (mustChangePassword) {
            user.requirePasswordChange();
        }
        return users.saveAndFlush(user);
    }

    /** IP distinto por chamada, para que os limites por IP de um teste não afetem outro. */
    public static RequestPostProcessor uniqueIp() {
        int n = IP_SEQUENCE.getAndIncrement();
        String ip = "10.%d.%d.%d".formatted((n >> 16) & 255, (n >> 8) & 255, n & 255);
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    public static String loginJson(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }

    /** Faz login e devolve o cookie de sessão. */
    public static Cookie login(MockMvc mockMvc, String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/admin/auth/login").with(csrf()).with(uniqueIp())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        if (session == null) {
            throw new IllegalStateException("Login não gerou cookie de sessão");
        }
        return session;
    }
}
