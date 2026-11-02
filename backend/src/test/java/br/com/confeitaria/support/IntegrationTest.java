package br.com.confeitaria.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import br.com.confeitaria.TestcontainersConfiguration;

/**
 * Teste de integração com contexto completo, MockMvc (com filtros de segurança e sessão)
 * e PostgreSQL real (Testcontainers). Todas as classes com esta anotação compartilham
 * o mesmo contexto e o mesmo container.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestSupportConfiguration.class})
public @interface IntegrationTest {
}
