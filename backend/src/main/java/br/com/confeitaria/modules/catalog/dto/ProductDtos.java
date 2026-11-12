package br.com.confeitaria.modules.catalog.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import br.com.confeitaria.modules.catalog.entity.Product;
import br.com.confeitaria.modules.catalog.entity.ProductCategory;
import br.com.confeitaria.modules.catalog.entity.ProductStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos do catálogo de produtos (painel e área pública). */
public final class ProductDtos {

    private ProductDtos() {
    }

    // --- Categorias ---

    public record CategoryRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 60, message = "Máximo de 60 caracteres.") String name,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String description,
            @Min(value = 0, message = "Use 0 ou mais.") @Max(value = 9999, message = "Máximo de 9999.") Integer displayOrder,
            Long version) {
    }

    public record CategoryResponse(Long id, String name, String description, boolean active, int displayOrder,
            long productCount, long version) {

        public static CategoryResponse from(ProductCategory category, long productCount) {
            return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                    category.isActive(), category.getDisplayOrder(), productCount, category.getVersion());
        }
    }

    // --- Produtos ---

    /**
     * {@code price} vazio = preço sob orçamento. {@code availableDays}: 1 = segunda ... 7 = domingo; vazio = todos.
     * Campos booleanos vazios assumem o padrão (aceita encomendas, retirada sim, delivery não).
     */
    public record ProductRequest(
            @NotNull(message = "Escolha a categoria.") Long categoryId,
            @NotBlank(message = "Informe o nome.") @Size(max = 100, message = "Máximo de 100 caracteres.") String name,
            @Size(max = 1000, message = "Máximo de 1000 caracteres.") String description,
            @DecimalMin(value = "0.00", message = "O preço não pode ser negativo.")
            @DecimalMax(value = "99999.99", message = "Valor muito alto.")
            @Digits(integer = 5, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal price,
            @Size(max = 40, message = "Máximo de 40 caracteres.") String unitLabel,
            @Min(value = 1, message = "Mínimo de 1.") @Max(value = 9999, message = "Máximo de 9999.") Integer minQuantity,
            Boolean orderable,
            Boolean seasonal,
            LocalDate availableFrom,
            LocalDate availableUntil,
            @Size(max = 7) List<@NotNull @Min(value = 1, message = "Dia inválido.") @Max(value = 7, message = "Dia inválido.") Integer> availableDays,
            Boolean pickupAllowed,
            Boolean deliveryAllowed,
            @Min(value = 0, message = "Use 0 ou mais.") @Max(value = 9999, message = "Máximo de 9999.") Integer displayOrder,
            Long version) {
    }

    public record StatusRequest(
            @NotNull(message = "Informe a situação.") ProductStatus status,
            @NotNull(message = "Versão obrigatória.") Long version) {
    }

    public record ProductResponse(Long id, Long categoryId, String categoryName, String name, String description,
            BigDecimal price, String unitLabel, int minQuantity, ProductStatus status, boolean orderable,
            boolean seasonal, LocalDate availableFrom, LocalDate availableUntil, List<Integer> availableDays,
            boolean pickupAllowed, boolean deliveryAllowed, int displayOrder, List<Long> photoIds, long version,
            Instant createdAt, Instant updatedAt) {

        public static ProductResponse from(Product p, List<Long> photoIds) {
            return new ProductResponse(p.getId(), p.getCategory().getId(), p.getCategory().getName(), p.getName(),
                    p.getDescription(), p.getPrice(), p.getUnitLabel(), p.getMinQuantity(), p.getStatus(),
                    p.isOrderable(), p.isSeasonal(), p.getAvailableFrom(), p.getAvailableUntil(), days(p),
                    p.isPickupAllowed(), p.isDeliveryAllowed(), p.getDisplayOrder(), photoIds, p.getVersion(),
                    p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    // --- Área pública ---

    public record PublicCategory(Long id, String name, String description) {

        public static PublicCategory from(ProductCategory c) {
            return new PublicCategory(c.getId(), c.getName(), c.getDescription());
        }
    }

    /** {@code available} = pode ser escolhido agora (ativo e aceita encomendas pelo site). */
    public record PublicProduct(Long id, Long categoryId, String name, String description, BigDecimal price,
            String unitLabel, int minQuantity, boolean available, boolean orderable, boolean seasonal,
            LocalDate availableFrom, LocalDate availableUntil, List<Integer> availableDays, boolean pickupAllowed,
            boolean deliveryAllowed, List<Long> photoIds) {

        public static PublicProduct from(Product p, List<Long> photoIds) {
            return new PublicProduct(p.getId(), p.getCategory().getId(), p.getName(), p.getDescription(), p.getPrice(),
                    p.getUnitLabel(), p.getMinQuantity(), p.isSelectable(), p.isOrderable(), p.isSeasonal(),
                    p.getAvailableFrom(), p.getAvailableUntil(), days(p), p.isPickupAllowed(), p.isDeliveryAllowed(),
                    photoIds);
        }
    }

    private static List<Integer> days(Product p) {
        return p.getAvailableDays().stream().map(DayOfWeek::getValue).sorted().toList();
    }
}
