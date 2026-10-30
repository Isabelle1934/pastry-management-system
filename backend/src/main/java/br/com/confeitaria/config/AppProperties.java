package br.com.confeitaria.config;

import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(ZoneId timezone, Cors cors) {

    public AppProperties {
        if (timezone == null) {
            timezone = ZoneId.of("America/Sao_Paulo");
        }
        if (cors == null) {
            cors = new Cors(List.of());
        }
    }

    public record Cors(List<String> allowedOrigins) {

        public Cors {
            allowedOrigins = allowedOrigins == null
                    ? List.of()
                    : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }
}
