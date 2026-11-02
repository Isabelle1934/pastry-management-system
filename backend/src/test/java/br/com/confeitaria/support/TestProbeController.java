package br.com.confeitaria.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.common.exception.BusinessRuleException;
import br.com.confeitaria.common.exception.ConflictException;
import br.com.confeitaria.common.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Endpoints existentes apenas nos testes. Classe de nível superior (e não aninhada na classe de teste)
 * para ser registrada pelo component scan sem criar um contexto Spring separado.
 */
@RestController
public class TestProbeController {

    public record ProbeRequest(@NotBlank(message = "Informe o nome.") String name,
            @Size(max = 5, message = "Máximo de 5 caracteres.") String code) {
    }

    @PostMapping("/api/test-probe/validate")
    String validate(@Valid @RequestBody ProbeRequest request) {
        return "ok";
    }

    @GetMapping("/api/test-probe/not-found")
    String notFound() {
        throw new NotFoundException("Pedido não encontrado.");
    }

    @GetMapping("/api/test-probe/business")
    String business() {
        throw new BusinessRuleException("SLOT_FULL", "Este horário não tem mais vagas.");
    }

    @GetMapping("/api/test-probe/conflict")
    String conflict() {
        throw new ConflictException("INVALID_STATUS_TRANSITION", "Transição não permitida.");
    }

    @GetMapping("/api/test-probe/crash")
    String crash() {
        throw new IllegalStateException("senha=segredo123 SELECT * FROM admin_user");
    }

    @GetMapping("/api/test-probe/param/{quantity}")
    String param(@PathVariable @Min(value = 1, message = "Deve ser no mínimo 1.") int quantity) {
        return "ok";
    }

    /** Endpoint administrativo qualquer, fora de /api/admin/auth (bloqueio por senha provisória). */
    @GetMapping("/api/admin/test-probe")
    String adminProbe() {
        return "ok";
    }
}
