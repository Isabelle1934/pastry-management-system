package br.com.confeitaria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ConfeitariaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfeitariaApiApplication.class, args);
    }

}
