package br.com.confeitaria.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação (UTC). Serviços recebem {@link Clock} em vez de chamar
 * {@code Instant.now()} diretamente, o que permite testar prazos e vencimentos.
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
