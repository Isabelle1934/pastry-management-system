package br.com.confeitaria.modules.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

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
            Long version) {
    }

    public record SizeResponse(Long id, String name, String description, Integer servings, BigDecimal basePrice,
            int maxFlavors, int maxFillings, boolean active, int displayOrder, long version) {

        public static SizeResponse from(CakeSize size) {
            return new SizeResponse(size.getId(), size.getName(), size.getDescription(), size.getServings(),
                    size.getBasePrice(), size.getMaxFlavors(), size.getMaxFillings(), size.isActive(),
                    size.getDisplayOrder(), size.getVersion());
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
            int maxFlavors, int maxFillings) {

        public static PublicSize from(CakeSize size) {
            return new PublicSize(size.getId(), size.getName(), size.getDescription(), size.getServings(),
                    size.getBasePrice(), size.getMaxFlavors(), size.getMaxFillings());
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
            boolean customCakeEnabled) {
    }
}
