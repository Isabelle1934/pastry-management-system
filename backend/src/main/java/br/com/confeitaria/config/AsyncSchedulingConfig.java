package br.com.confeitaria.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita tarefas assíncronas (envio de e-mail) e agendadas (limpeza de contadores, vencimentos). */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {
}
