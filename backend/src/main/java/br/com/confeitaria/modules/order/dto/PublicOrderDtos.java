package br.com.confeitaria.modules.order.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import br.com.confeitaria.modules.order.entity.FulfillmentType;
import br.com.confeitaria.modules.order.service.OrderDetailsApplier.Details;
import br.com.confeitaria.modules.order.service.OrderDetailsApplier.ItemChoice;
import br.com.confeitaria.modules.order.entity.DeliveryZone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Contratos do formulário de encomenda. Regras que dependem do banco (e as que dependem de ter ou não
 * bolo personalizado, como tamanho/massa/recheio obrigatórios) ficam nos serviços.
 */
public final class PublicOrderDtos {

    private PublicOrderDtos() {
    }

    /** Produto do catálogo escolhido, com quantidade. */
    public record OrderItemRequest(
            @NotNull(message = "Produto inválido.") Long productId,
            @NotNull(message = "Informe a quantidade.")
            @Min(value = 1, message = "A quantidade deve ser pelo menos 1.") @Max(value = 9999, message = "Quantidade muito alta.")
            Integer quantity,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String notes) {

        ItemChoice toChoice() {
            return new ItemChoice(productId, quantity, notes);
        }
    }

    /**
     * Pedido completo: usado na criação pela cliente e na edição pela proprietária
     * ({@code version} obrigatório só na edição). {@code customCake} vazio = com bolo personalizado
     * (comportamento anterior); {@code fulfillmentType} vazio = retirada.
     */
    public record OrderRequest(
            // Etapa 1 — dados da cliente
            @NotBlank(message = "Informe o nome completo.")
            @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres.") String customerName,
            @NotBlank(message = "Informe o WhatsApp.") @Size(max = 30, message = "Número inválido.") String customerWhatsapp,
            // Agendamento
            @NotNull(message = "Escolha a data.") LocalDate pickupDate,
            @NotNull(message = "Escolha o horário.") LocalTime pickupTime,
            // Bolo personalizado
            Long sizeId,
            @Size(max = 10) List<@NotNull Long> flavorIds,
            @Size(max = 10) List<@NotNull Long> fillingIds,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String decoration,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String cakeText,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String notes,
            // Topo de bolo (opcional, cobrado à parte)
            Boolean topperRequested,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String topperTheme,
            @Size(max = 60, message = "Máximo de 60 caracteres.") String topperName,
            @Min(value = 0, message = "Idade inválida.") @Max(value = 120, message = "Idade inválida.") Integer topperAge,
            Long version,
            // Catálogo e atendimento
            Boolean customCake,
            @Size(max = 30, message = "Escolha no máximo 30 produtos diferentes.") List<@Valid OrderItemRequest> items,
            FulfillmentType fulfillmentType,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String deliveryAddress,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String deliveryDistrict,
            @Size(max = 200, message = "Máximo de 200 caracteres.") String deliveryReference,
            // Categoria "consulte valores" (sem tamanho), modalidade de entrega e e-mail opcional
            Long cakeCategoryId,
            DeliveryZone deliveryZone,
            @Email(message = "E-mail inválido.") @Size(max = 254, message = "E-mail muito longo.") String customerEmail) {

        public Details details() {
            return new Details(pickupDate, pickupTime, customCake == null || customCake, sizeId, flavorIds, fillingIds,
                    decoration, cakeText, notes, Boolean.TRUE.equals(topperRequested), topperTheme, topperName, topperAge, choices(items),
                    fulfillmentType, deliveryAddress, deliveryDistrict, deliveryReference, cakeCategoryId, deliveryZone);
        }
    }

    /** Edição pela cliente (acompanhamento): sem nome/WhatsApp, que identificam o pedido. */
    public record ClientEditRequest(
            @NotNull(message = "Escolha a data.") LocalDate pickupDate,
            @NotNull(message = "Escolha o horário.") LocalTime pickupTime,
            Long sizeId,
            @Size(max = 10) List<@NotNull Long> flavorIds,
            @Size(max = 10) List<@NotNull Long> fillingIds,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String decoration,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String cakeText,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String notes,
            Boolean topperRequested,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String topperTheme,
            @Size(max = 60, message = "Máximo de 60 caracteres.") String topperName,
            @Min(value = 0, message = "Idade inválida.") @Max(value = 120, message = "Idade inválida.") Integer topperAge,
            @NotNull(message = "Versão obrigatória.") Long version,
            Boolean customCake,
            @Size(max = 30, message = "Escolha no máximo 30 produtos diferentes.") List<@Valid OrderItemRequest> items,
            FulfillmentType fulfillmentType,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String deliveryAddress,
            @Size(max = 100, message = "Máximo de 100 caracteres.") String deliveryDistrict,
            @Size(max = 200, message = "Máximo de 200 caracteres.") String deliveryReference,
            Long cakeCategoryId,
            DeliveryZone deliveryZone) {

        public Details details() {
            return new Details(pickupDate, pickupTime, customCake == null || customCake, sizeId, flavorIds, fillingIds,
                    decoration, cakeText, notes, Boolean.TRUE.equals(topperRequested), topperTheme, topperName, topperAge, choices(items),
                    fulfillmentType, deliveryAddress, deliveryDistrict, deliveryReference, cakeCategoryId, deliveryZone);
        }
    }

    private static List<ItemChoice> choices(List<OrderItemRequest> items) {
        return items == null ? List.of() : items.stream().map(i -> i == null ? null : i.toChoice()).toList();
    }

    /** {@code trackingCode} no formato de exibição XXXX-XXXX-XXXX-XXXX. */
    public record OrderCreatedResponse(String trackingCode) {
    }
}
