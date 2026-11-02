package br.com.confeitaria.modules.order.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import br.com.confeitaria.modules.order.service.OrderDetailsApplier.Details;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos do formulário de encomenda. Regras que dependem do banco ficam nos serviços. */
public final class PublicOrderDtos {

    private PublicOrderDtos() {
    }

    /**
     * Pedido completo: usado na criação pela cliente e na edição pela proprietária
     * ({@code version} obrigatório só na edição).
     */
    public record OrderRequest(
            // Etapa 1 — dados da cliente
            @NotBlank(message = "Informe o nome completo.")
            @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres.") String customerName,
            @NotBlank(message = "Informe o WhatsApp.") @Size(max = 30, message = "Número inválido.") String customerWhatsapp,
            // Etapa 2 — agendamento
            @NotNull(message = "Escolha a data.") LocalDate pickupDate,
            @NotNull(message = "Escolha o horário.") LocalTime pickupTime,
            // Etapa 3 — personalização
            @NotNull(message = "Escolha o tamanho.") Long sizeId,
            @NotEmpty(message = "Escolha pelo menos uma massa.") @Size(max = 10) List<@NotNull Long> flavorIds,
            @NotEmpty(message = "Escolha pelo menos um recheio.") @Size(max = 10) List<@NotNull Long> fillingIds,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String decoration,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String cakeText,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String notes,
            // Etapa 4 — topo de bolo (opcional)
            boolean topperRequested,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String topperTheme,
            @Size(max = 60, message = "Máximo de 60 caracteres.") String topperName,
            @Min(value = 0, message = "Idade inválida.") @Max(value = 120, message = "Idade inválida.") Integer topperAge,
            Long version) {

        public Details details() {
            return new Details(pickupDate, pickupTime, sizeId, flavorIds, fillingIds, decoration, cakeText, notes,
                    topperRequested, topperTheme, topperName, topperAge);
        }
    }

    /** Edição pela cliente (acompanhamento): sem nome/WhatsApp, que identificam o pedido. */
    public record ClientEditRequest(
            @NotNull(message = "Escolha a data.") LocalDate pickupDate,
            @NotNull(message = "Escolha o horário.") LocalTime pickupTime,
            @NotNull(message = "Escolha o tamanho.") Long sizeId,
            @NotEmpty(message = "Escolha pelo menos uma massa.") @Size(max = 10) List<@NotNull Long> flavorIds,
            @NotEmpty(message = "Escolha pelo menos um recheio.") @Size(max = 10) List<@NotNull Long> fillingIds,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String decoration,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String cakeText,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String notes,
            boolean topperRequested,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String topperTheme,
            @Size(max = 60, message = "Máximo de 60 caracteres.") String topperName,
            @Min(value = 0, message = "Idade inválida.") @Max(value = 120, message = "Idade inválida.") Integer topperAge,
            @NotNull(message = "Versão obrigatória.") Long version) {

        public Details details() {
            return new Details(pickupDate, pickupTime, sizeId, flavorIds, fillingIds, decoration, cakeText, notes,
                    topperRequested, topperTheme, topperName, topperAge);
        }
    }

    /** {@code trackingCode} no formato de exibição XXXX-XXXX-XXXX-XXXX. */
    public record OrderCreatedResponse(String trackingCode) {
    }
}
