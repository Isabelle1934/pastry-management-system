package br.com.confeitaria.modules.auth;

import static br.com.confeitaria.support.AuthTestSupport.PASSWORD;
import static br.com.confeitaria.support.AuthTestSupport.loginJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import br.com.confeitaria.TestcontainersConfiguration;
import br.com.confeitaria.modules.auth.entity.AdminUser;
import br.com.confeitaria.support.AuthTestSupport;
import br.com.confeitaria.support.TestSupportConfiguration;

/**
 * Fluxo de cookies e CSRF contra o servidor HTTP real (Tomcat), sem MockMvc:
 * verifica exatamente os headers Set-Cookie que o navegador receberá.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
class SessionCookieHttpIntegrationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private AuthTestSupport support;

    private final HttpClient http = HttpClient.newHttpClient();

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String cookie(HttpResponse<?> response, String name) {
        return setCookies(response).stream().filter(h -> h.startsWith(name + "=")).findFirst().orElse(null);
    }

    private static List<String> setCookies(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie");
    }

    private static String valueOf(String setCookieHeader) {
        String pair = setCookieHeader.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }

    private HttpResponse<String> postLogin(String email, String xsrf, boolean sendHeader) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/admin/auth/login"))
                .header("Content-Type", "application/json")
                .header("Cookie", "XSRF-TOKEN=" + xsrf)
                .POST(HttpRequest.BodyPublishers.ofString(loginJson(email, PASSWORD)));
        if (sendHeader) {
            request.header("X-XSRF-TOKEN", xsrf);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void csrfCookieThenLoginThenSessionWorksEndToEnd() throws Exception {
        AdminUser user = support.createUser();

        HttpResponse<Void> csrf = http.send(HttpRequest.newBuilder(uri("/api/public/csrf")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(csrf.statusCode()).isEqualTo(204);
        String xsrfHeader = cookie(csrf, "XSRF-TOKEN");
        assertThat(xsrfHeader).isNotNull()
                .contains("Path=/", "Secure", "SameSite=Lax")
                .doesNotContain("HttpOnly");
        String xsrf = valueOf(xsrfHeader);

        assertThat(postLogin(user.getEmail(), xsrf, false).statusCode()).as("sem header CSRF").isEqualTo(403);

        HttpResponse<String> login = postLogin(user.getEmail(), xsrf, true);
        assertThat(login.statusCode()).isEqualTo(200);
        String sessionHeader = cookie(login, "SESSION");
        assertThat(sessionHeader).isNotNull().contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/");
        assertThat(cookie(login, "XSRF-TOKEN")).as("token CSRF renovado no login").isNotNull()
                .isNotEqualTo(xsrfHeader);

        HttpResponse<String> me = http.send(HttpRequest.newBuilder(uri("/api/admin/auth/me"))
                        .header("Cookie", "SESSION=" + valueOf(sessionHeader)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(user.getEmail()).doesNotContain("password");
    }
}
