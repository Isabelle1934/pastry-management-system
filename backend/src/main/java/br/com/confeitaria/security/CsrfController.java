package br.com.confeitaria.security;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Permite ao frontend obter o cookie {@code XSRF-TOKEN} antes do primeiro POST.
 * O cookie é emitido pelo filtro de CSRF ({@link SpaCsrfTokenRequestHandler}); aqui só respondemos 204.
 */
@RestController
class CsrfController {

    @GetMapping("/api/public/csrf")
    ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }
}
