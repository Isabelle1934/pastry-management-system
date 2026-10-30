package br.com.confeitaria;

import org.springframework.boot.SpringApplication;

public class TestConfeitariaApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(ConfeitariaApiApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
