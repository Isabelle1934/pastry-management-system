package br.com.confeitaria.common.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.com.confeitaria.common.web.RequestIdFilter;
import br.com.confeitaria.support.IntegrationTest;

/** Passa pela cadeia completa de filtros (segurança, CSRF, request id), como em produção. */
@IntegrationTest
class GlobalExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void beanValidationErrorsListEachField() throws Exception {
        mockMvc.perform(post("/api/test-probe/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON)
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
        mockMvc.perform(get("/api/test-probe/param/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    }

    @Test
    void malformedJsonDoesNotLeakParserDetails() throws Exception {
        mockMvc.perform(post("/api/test-probe/validate").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{nome:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("A requisição está incompleta ou em formato inválido."));
    }

    @Test
    void domainExceptionsKeepStatusAndCode() throws Exception {
        mockMvc.perform(get("/api/test-probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Pedido não encontrado."));

        mockMvc.perform(get("/api/test-probe/business"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SLOT_FULL"))
                .andExpect(jsonPath("$.detail").value("Este horário não tem mais vagas."));

        mockMvc.perform(get("/api/test-probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void unexpectedErrorsHideInternalMessage() throws Exception {
        mockMvc.perform(get("/api/test-probe/crash"))
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

        mockMvc.perform(delete("/api/test-probe/not-found").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(post("/api/test-probe/validate").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void requestIdIsReturnedInHeaderAndBody() throws Exception {
        mockMvc.perform(get("/api/test-probe/not-found").header(RequestIdFilter.HEADER, "proxy-id-12345678"))
                .andExpect(header().string(RequestIdFilter.HEADER, "proxy-id-12345678"))
                .andExpect(jsonPath("$.requestId").value("proxy-id-12345678"));
    }

    @Test
    void unsafeIncomingRequestIdIsReplaced() throws Exception {
        mockMvc.perform(get("/api/test-probe/not-found").header(RequestIdFilter.HEADER, "abc\nFAKE LOG LINE"))
                .andExpect(header().string(RequestIdFilter.HEADER, not(containsString("FAKE"))))
                .andExpect(jsonPath("$.requestId", not(containsString("FAKE"))));
    }
}
