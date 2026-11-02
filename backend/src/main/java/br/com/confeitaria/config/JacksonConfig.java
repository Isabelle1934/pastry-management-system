package br.com.confeitaria.config;

import java.math.BigDecimal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Valores decimais (dinheiro, quantidades) saem na API como string ({@code "120.00"}),
 * evitando arredondamentos de ponto flutuante no JavaScript (ADR-008).
 */
@Configuration
public class JacksonConfig {

    @Bean
    SimpleModule decimalAsStringModule() {
        SimpleModule module = new SimpleModule("decimal-as-string");
        module.addSerializer(BigDecimal.class, new ValueSerializer<>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(value.toPlainString());
            }
        });
        return module;
    }
}
