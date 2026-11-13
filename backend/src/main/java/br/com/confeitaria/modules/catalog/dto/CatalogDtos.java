package br.com.confeitaria.modules.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

import br.com.confeitaria.modules.catalog.entity.CakeCategory;
import br.com.confeitaria.modules.catalog.entity.CakeSize;
import br.com.confeitaria.modules.catalog.entity.CatalogOption;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos da API do catálogo (painel e área pública). */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    /** {@code version} é obrigatório na edição (controle de edição concorrente) e ignorado na criação. */
    public record SizeRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 80, message = "Máximo de 80 caracteres.") String name,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String description,
            @Min(value = 1, message = "Informe um número maior que zero.") @Max(value = 500, message = "Máximo de 500.") Integer servings,
            @NotNull(message = "Informe o preço-base.")
            @DecimalMin(value = "0.00", message = "O preço não pode ser negativo.")
            @DecimalMax(value = "99999.99", message = "Valor muito alto.")
            @Digits(integer = 5, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal basePrice,
            @NotNull(message = "Informe quantas massas podem ser escolhidas.")
            @Min(value = 1, message = "Mínimo de 1.") @Max(value = 10, message = "Máximo de 10.") Integer maxFlavors,
            @NotNull(message = "Informe quantos recheios podem ser escolhidos.")
            @Min(value = 1, message = "Mínimo de 1.") @Max(value = 10, message = "Máximo de 10.") Integer maxFillings,
            @Min(value = 0, message = "Use 0 ou mais.") @Max(value = 9999, message = "Máximo de 9999.") Integer displayOrder,
            Long version,
            // Categoria, peso e diâmetro (opcionais)
            Long categoryId,
            @DecimalMin(value = "0.01", message = "Informe um peso maior que zero.")
            @DecimalMax(value = "999.99", message = "Peso muito alto.")
            @Digits(integer = 3, fraction = 2, message = "Use no máximo 2 casas decimais.") BigDecimal weightKg,
            @Min(value = 1, message = "Informe um diâmetro maior que zero.") @Max(value = 200, message = "Máximo de 200 cm.")
            Integer diameterCm) {
    }

    public record SizeResponse(Long id, String name, String description, Integer servings, BigDecimal basePrice,
            int maxFlavors, int maxFillings, boolean active, int displayOrder, long version, Long categoryId,
            String categoryName, BigDecimal weightKg, Integer diameterCm) {

        public static SizeResponse from(CakeSize size) {
            CakeCategory category = size.getCategory();
            return new SizeResponse(size.getId(), size.getName(), size.getDescription(), size.getServings(),
                    size.getBasePrice(), size.getMaxFlavors(), size.getMaxFillings(), size.isActive(),
                    size.getDisplayOrder(), size.getVersion(), category == null ? null : category.getId(),
                    category == null ? null : category.getName(), size.getWeightKg(), size.getDiameterCm());
        }
    }

    /** Categoria de bolo. {@code version} obrigatório na edição. */
    public record CakeCategoryRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 80, message = "Máximo de 80 caracteres.") String name,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String description,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String notice,
            Boolean consultOnly,
            @Min(value = 1, message = "Mínimo de 1.") @Max(value = 10, message = "Máximo de 10.") Integer maxFlavors,
            @Min(value = 1, message = "Mínimo de 1.") @Max(value = 10, message = "Máximo de 10.") Integer maxFillings,
            @Min(value = 0, message = "Use 0 ou mais.") @Max(value = 9999, message = "Máximo de 9999.") Integer displayOrder,
            Long version) {
    }

    public record CakeCategoryResponse(Long id, String name, String description, String notice, boolean consultOnly,
            int maxFlavors, int maxFillings, boolean active, int displayOrder, long sizeCount, long version) {

        public static CakeCategoryResponse from(CakeCategory c, long sizeCount) {
            return new CakeCategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getNotice(), c.isConsultOnly(),
                    c.getMaxFlavors(), c.getMaxFillings(), c.isActive(), c.getDisplayOrder(), sizeCount, c.getVersion());
        }
    }

    public record PublicCakeCategory(Long id, String name, String description, String notice, boolean consultOnly,
            int maxFlavors, int maxFillings) {

        public static PublicCakeCategory from(CakeCategory c) {
            return new PublicCakeCategory(c.getId(), c.getName(), c.getDescription(), c.getNotice(), c.isConsultOnly(),
                    c.getMaxFlavors(), c.getMaxFillings());
        }
    }

    public record OptionRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 80, message = "Máximo de 80 caracteres.") String name,
            @Size(max = 300, message = "Máximo de 300 caracteres.") String description,
            @Min(value = 0, message = "Use 0 ou mais.") @Max(value = 9999, message = "Máximo de 9999.") Integer displayOrder,
            Long version) {
    }

    public record OptionResponse(Long id, String name, String description, boolean active, int displayOrder,
            long version) {

        public static OptionResponse from(CatalogOption option) {
            return new OptionResponse(option.getId(), option.getName(), option.getDescription(), option.isActive(),
                    option.getDisplayOrder(), option.getVersion());
        }
    }

    public record ActiveRequest(
            @NotNull(message = "Informe se o item fica ativo.") Boolean active,
            @NotNull(message = "Versão obrigatória.") Long version) {
    }

    // --- Área pública: somente itens ativos e campos necessários ao formulário de encomenda ---

    public record PublicSize(Long id, String name, String description, Integer servings, BigDecimal basePrice,
            int maxFlavors, int maxFillings, Long categoryId, BigDecimal weightKg, Integer diameterCm) {

        public static PublicSize from(CakeSize size) {
            return new PublicSize(size.getId(), size.getName(), size.getDescription(), size.getServings(),
                    size.getBasePrice(), size.getMaxFlavors(), size.getMaxFillings(),
                    size.getCategory() == null ? null : size.getCategory().getId(), size.getWeightKg(),
                    size.getDiameterCm());
        }
    }

    public record PublicOption(Long id, String name, String description) {

        public static PublicOption from(CatalogOption option) {
            return new PublicOption(option.getId(), option.getName(), option.getDescription());
        }
    }

    /** {@code customCakeEnabled} = o formulário aceita novas encomendas de bolo personalizado. */
    public record PublicCatalog(List<PublicSize> sizes, List<PublicOption> flavors, List<PublicOption> fillings,
            List<ProductDtos.PublicCategory> categories, List<ProductDtos.PublicProduct> products,
            boolean customCakeEnabled, List<PublicCakeCategory> cakeCategories) {
    }
}
