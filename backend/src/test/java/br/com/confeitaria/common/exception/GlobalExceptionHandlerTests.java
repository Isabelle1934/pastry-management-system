package br.com.confeitaria.common.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.common.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@WebMvcTest(controllers = GlobalExceptionHandlerTests.ProbeController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class, GlobalExceptionHandlerTests.ProbeController.class})
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    record ProbeRequest(@NotBlank(message = "Informe o nome.") String name,
            @Size(max = 5, message = "Máximo de 5 caracteres.") String code) {
    }

    @RestController
    static class ProbeController {

        @PostMapping("/probe/validate")
        String validate(@Valid @RequestBody ProbeRequest request) {
            return "ok";
        }

        @GetMapping("/probe/not-found")
        String notFound() {
            throw new NotFoundException("Pedido não encontrado.");
        }

        @GetMapping("/probe/business")
        String business() {
            throw new BusinessRuleException("SLOT_FULL", "Este horário não tem mais vagas.");
        }

        @GetMapping("/probe/conflict")
        String conflict() {
            throw new ConflictException("INVALID_STATUS_TRANSITION", "Transição não permitida.");
        }

        @GetMapping("/probe/crash")
        String crash() {
            throw new IllegalStateException("senha=segredo123 SELECT * FROM admin_user");
        }

        @GetMapping("/probe/param/{quantity}")
        String param(@PathVariable @Min(value = 1, message = "Deve ser no mínimo 1.") int quantity) {
            return "ok";
        }
    }

    @Test
    void beanValidationErrorsListEachField() throws Exception {
        mockMvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"code\":\"1234567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].message").value("Máximo de 5 caracteres."))
                .andExpect(jsonPath("$.errors[1].field").value("name"))
                .andExpect(jsonPath("$.errors[1].message").value("Informe o nome."));
    }

    @Test
    void methodParameterValidationIsReported() throws Exception {
        mockMvc.perform(get("/probe/param/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    }

    @Test
    void malformedJsonDoesNotLeakParserDetails() throws Exception {
        mockMvc.perform(post("/probe/validate").contentType(MediaType.APPLICATION_JSON).content("{nome:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("A requisição está incompleta ou em formato inválido."));
    }

    @Test
    void domainExceptionsKeepStatusAndCode() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Pedido não encontrado."));

        mockMvc.perform(get("/probe/business"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SLOT_FULL"))
                .andExpect(jsonPath("$.detail").value("Este horário não tem mais vagas."));

        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void unexpectedErrorsHideInternalMessage() throws Exception {
        mockMvc.perform(get("/probe/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("segredo123"))))
                .andExpect(content().string(not(containsString("SELECT"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void frameworkErrorsAreTranslated() throws Exception {
        mockMvc.perform(get("/rota/que/nao/existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(delete("/probe/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(post("/probe/validate").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void requestIdIsReturnedInHeaderAndBody() throws Exception {
        mockMvc.perform(get("/probe/not-found").header(RequestIdFilter.HEADER, "proxy-id-12345678"))
                .andExpect(header().string(RequestIdFilter.HEADER, "proxy-id-12345678"))
                .andExpect(jsonPath("$.requestId").value("proxy-id-12345678"));
    }

    @Test
    void unsafeIncomingRequestIdIsReplaced() throws Exception {
        mockMvc.perform(get("/probe/not-found").header(RequestIdFilter.HEADER, "abc\nFAKE LOG LINE"))
                .andExpect(header().string(RequestIdFilter.HEADER, not(containsString("FAKE"))))
                .andExpect(jsonPath("$.requestId", not(containsString("FAKE"))));
    }
}
