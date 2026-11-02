package br.com.confeitaria.modules.deadline;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Dispara o processamento de prazos periodicamente (padrão: a cada 5 minutos). Desligável com
 * {@code app.jobs.enabled=false} (usado nos testes, que chamam o {@link DeadlineService} diretamente).
 */
@Component
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class DeadlineScheduler {

    private final DeadlineService deadlines;

    public DeadlineScheduler(DeadlineService deadlines) {
        this.deadlines = deadlines;
    }

    @Scheduled(fixedDelayString = "${app.jobs.deadline-interval:PT5M}", initialDelayString = "PT1M")
    @SchedulerLock(name = "deadlines", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void run() {
        deadlines.runAll();
    }
}
