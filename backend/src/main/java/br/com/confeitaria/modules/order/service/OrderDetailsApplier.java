package br.com.confeitaria.modules.order.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.com.confeitaria.common.audit.Changes;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.modules.catalog.entity.CakeSize;
import br.com.confeitaria.modules.catalog.entity.CatalogOption;
import br.com.confeitaria.modules.catalog.entity.Product;
import br.com.confeitaria.modules.catalog.repository.CakeFillingRepository;
import br.com.confeitaria.modules.catalog.repository.CakeFlavorRepository;
import br.com.confeitaria.modules.catalog.repository.CakeSizeRepository;
import br.com.confeitaria.modules.catalog.repository.ProductRepository;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.order.entity.FulfillmentType;
import br.com.confeitaria.modules.order.entity.OrderItem;
import br.com.confeitaria.modules.schedule.repository.WeeklySlotRepository;
import br.com.confeitaria.modules.schedule.service.AvailabilityService;
import br.com.confeitaria.modules.settings.entity.BakerySettings;
import br.com.confeitaria.modules.settings.service.SettingsService;

/**
 * Valida e aplica agendamento, bolo personalizado, topo, itens do catálogo e modalidade de atendimento a um
 * pedido. Usado na criação pela cliente, na edição pela cliente e na edição pela proprietária — as mesmas
 * regras nos três casos (a proprietária não fica presa à disponibilidade exibida no site).
 */
@Component
public class OrderDetailsApplier {

    static final int MAX_ITEMS = 30;

    /** Item do catálogo pedido. */
    public record ItemChoice(Long productId, Integer quantity, String notes) {
    }

    /** Dados editáveis do pedido (sem dados da cliente). */
    public record Details(LocalDate pickupDate, LocalTime pickupTime, boolean customCake, Long sizeId,
            List<Long> flavorIds, List<Long> fillingIds, String decoration, String cakeText, String notes,
            boolean topperRequested, String topperTheme, String topperName, Integer topperAge, List<ItemChoice> items,
            FulfillmentType fulfillmentType, String deliveryAddress, String deliveryDistrict,
            String deliveryReference) {

        public Details {
            flavorIds = flavorIds == null ? List.of() : flavorIds;
            fillingIds = fillingIds == null ? List.of() : fillingIds;
            items = items == null ? List.of() : items;
            fulfillmentType = fulfillmentType == null ? FulfillmentType.PICKUP : fulfillmentType;
        }
    }

    /** Quem está alterando: a cliente só escolhe o que está disponível; a proprietária, qualquer opção cadastrada. */
    public enum Actor {
        CLIENT,
        OWNER
    }

    private final CakeSizeRepository sizes;
    private final CakeFlavorRepository flavors;
    private final CakeFillingRepository fillings;
    private final ProductRepository products;
    private final AvailabilityService availability;
    private final WeeklySlotRepository weeklySlots;
    private final SettingsService settings;

    public OrderDetailsApplier(CakeSizeRepository sizes, CakeFlavorRepository flavors, CakeFillingRepository fillings,
            ProductRepository products, AvailabilityService availability, WeeklySlotRepository weeklySlots,
            SettingsService settings) {
        this.sizes = sizes;
        this.flavors = flavors;
        this.fillings = fillings;
        this.products = products;
        this.availability = availability;
        this.weeklySlots = weeklySlots;
        this.settings = settings;
    }

    /**
     * Valida tudo antes de alterar qualquer campo. Itens já escolhidos no pedido continuam válidos
     * mesmo que tenham sido desativados depois (a edição não obriga a trocar o que já estava combinado)
     * e mantêm a cópia original de nome e preço.
     *
     * @return campos alterados (para o histórico); vazio na criação
     */
    public Map<String, Object> apply(CakeOrder order, Details details, Actor actor) {
        boolean isNew = order.getId() == null;
        boolean client = actor == Actor.CLIENT;
        BakerySettings bakery = settings.current();

        // --- Bolo personalizado (opcional) ---
        CakeSize size = null;
        Map<Long, String> chosenFlavors = Map.of();
        Map<Long, String> chosenFillings = Map.of();
        boolean hadCake = !isNew && order.isCustomCake();
        if (details.customCake()) {
            if (client && !hadCake && !bakery.isCustomCakeEnabled()) {
                throw new FieldValidationException("customCake",
                        "No momento não estamos aceitando encomendas de bolo personalizado.");
            }
            if (details.sizeId() == null) {
                throw new FieldValidationException("sizeId", "Escolha o tamanho.");
            }
            CakeSize currentSize = hadCake ? order.getSize() : null;
            size = sizes.findById(details.sizeId())
                    .filter(s -> s.isActive() || (currentSize != null && s.getId().equals(currentSize.getId())))
                    .orElseThrow(() -> new FieldValidationException("sizeId", "Este tamanho não está mais disponível."));
            if (details.flavorIds().isEmpty()) {
                throw new FieldValidationException("flavorIds", "Escolha pelo menos uma massa.");
            }
            chosenFlavors = resolveOptions(details.flavorIds(), size.getMaxFlavors(), "flavorIds", "massa", "massas",
                    order.getFlavorChoices().keySet(), ids -> flavors.findAllById(ids));
            if (details.fillingIds().isEmpty()) {
                throw new FieldValidationException("fillingIds", "Escolha pelo menos um recheio.");
            }
            chosenFillings = resolveOptions(details.fillingIds(), size.getMaxFillings(), "fillingIds", "recheio",
                    "recheios", order.getFillingChoices().keySet(), ids -> fillings.findAllById(ids));
        }

        // --- Itens do catálogo ---
        Map<Long, OrderItem> currentItems = order.getItemsByProduct();
        Map<Long, Product> chosenProducts = loadProducts(details.items());
        if (!details.customCake() && details.items().isEmpty()) {
            throw new FieldValidationException("items", "Escolha pelo menos um produto ou um bolo personalizado.");
        }
        if (client) {
            for (ItemChoice item : details.items()) {
                Product product = chosenProducts.get(item.productId());
                if (!currentItems.containsKey(product.getId()) && !product.isSelectable()) {
                    throw new FieldValidationException("items", "\"" + product.getName() + "\" não está disponível no momento.");
                }
                if (item.quantity() < product.getMinQuantity()) {
                    throw new FieldValidationException("items", "A quantidade mínima de \"" + product.getName() + "\" é "
                            + product.getMinQuantity() + ".");
                }
            }
        }

        // --- Agenda ---
        boolean scheduleChanged = isNew || !details.pickupDate().equals(order.getPickupDate())
                || !details.pickupTime().equals(order.getPickupTime());
        if (scheduleChanged) {
            validateSchedule(details.pickupDate(), details.pickupTime(), actor);
        }
        if (client) {
            for (Product product : chosenProducts.values()) {
                boolean recheck = scheduleChanged || !currentItems.containsKey(product.getId());
                if (recheck && !product.acceptsDate(details.pickupDate())) {
                    throw new FieldValidationException("pickupDate",
                            "\"" + product.getName() + "\" não está disponível na data escolhida.");
                }
            }
        }

        // --- Modalidade de atendimento ---
        FulfillmentType fulfillment = details.fulfillmentType();
        if (client) {
            validateFulfillment(order, isNew, details, fulfillment, bakery, chosenProducts, currentItems);
        }
        String deliveryAddress = blankToNull(details.deliveryAddress());
        if (fulfillment == FulfillmentType.DELIVERY && deliveryAddress == null) {
            throw new FieldValidationException("deliveryAddress", "Informe o endereço de entrega.");
        }

        // --- Topo de bolo (só com bolo personalizado) ---
        String topperTheme = blankToNull(details.topperTheme());
        if (details.topperRequested() && !details.customCake()) {
            throw new FieldValidationException("topperRequested",
                    "O topo de bolo só pode ser pedido junto com um bolo personalizado.");
        }
        if (details.topperRequested() && topperTheme == null) {
            throw new FieldValidationException("topperTheme", "Informe o tema do topo de bolo.");
        }

        Map<Long, OrderItem> newItems = toItems(details.items(), chosenProducts, currentItems);

        Changes changes = new Changes();
        if (!isNew) {
            changes.track("pickupDate", String.valueOf(order.getPickupDate()), String.valueOf(details.pickupDate()))
                    .track("pickupTime", String.valueOf(order.getPickupTime()), String.valueOf(details.pickupTime()))
                    .track("customCake", order.isCustomCake(), details.customCake())
                    .track("size", order.getSizeNameSnapshot(), size == null ? null : size.getName())
                    .track("flavors", String.join(", ", order.getFlavorNames()), String.join(", ", chosenFlavors.values()))
                    .track("fillings", String.join(", ", order.getFillingNames()), String.join(", ", chosenFillings.values()))
                    .track("decoration", order.getDecoration(), details.customCake() ? blankToNull(details.decoration()) : null)
                    .track("cakeText", order.getCakeText(), details.customCake() ? blankToNull(details.cakeText()) : null)
                    .track("notes", order.getNotes(), blankToNull(details.notes()))
                    .track("items", describe(order.getItemEntries().stream().map(Map.Entry::getValue).toList()),
                            describe(List.copyOf(newItems.values())))
                    .track("fulfillmentType", order.getFulfillmentType().name(), fulfillment.name())
                    .track("deliveryAddress", order.getDeliveryAddress(),
                            fulfillment == FulfillmentType.DELIVERY ? deliveryAddress : null)
                    .track("deliveryDistrict", order.getDeliveryDistrict(),
                            fulfillment == FulfillmentType.DELIVERY ? blankToNull(details.deliveryDistrict()) : null)
                    .track("topperRequested", order.isTopperRequested(), details.topperRequested())
                    .track("topperTheme", order.getTopperTheme(), details.topperRequested() ? topperTheme : null)
                    .track("topperName", order.getTopperName(),
                            details.topperRequested() ? blankToNull(details.topperName()) : null)
                    .track("topperAge", order.getTopperAge() == null ? null : order.getTopperAge().intValue(),
                            details.topperRequested() ? details.topperAge() : null);
        }

        order.schedule(details.pickupDate(), details.pickupTime());
        if (details.customCake()) {
            order.customize(size, blankToNull(details.decoration()), blankToNull(details.cakeText()),
                    blankToNull(details.notes()));
            order.chooseFlavors(chosenFlavors);
            order.chooseFillings(chosenFillings);
            if (details.topperRequested()) {
                order.requestTopper(topperTheme, blankToNull(details.topperName()),
                        details.topperAge() == null ? null : details.topperAge().shortValue());
            } else {
                order.removeTopper();
            }
        } else {
            order.withoutCustomCake(blankToNull(details.notes()));
        }
        order.chooseItems(newItems);
        order.setFulfillment(fulfillment, deliveryAddress, blankToNull(details.deliveryDistrict()),
                blankToNull(details.deliveryReference()));
        return changes.asMap();
    }

    private void validateFulfillment(CakeOrder order, boolean isNew, Details details, FulfillmentType type,
            BakerySettings bakery, Map<Long, Product> chosenProducts, Map<Long, OrderItem> currentItems) {
        boolean typeChanged = isNew || order.getFulfillmentType() != type;
        boolean delivery = type == FulfillmentType.DELIVERY;
        if (typeChanged) {
            if (delivery && !bakery.isDeliveryEnabled()) {
                throw new FieldValidationException("fulfillmentType", "No momento não estamos fazendo entregas.");
            }
            if (!delivery && !bakery.isPickupEnabled()) {
                throw new FieldValidationException("fulfillmentType",
                        "No momento não estamos com retirada na confeitaria. Escolha a entrega.");
            }
        }
        boolean cakeAdded = details.customCake() && (isNew || !order.isCustomCake());
        if (delivery && details.customCake() && (typeChanged || cakeAdded) && !bakery.isCustomCakeDelivery()) {
            throw new FieldValidationException("fulfillmentType",
                    "Bolos personalizados são apenas para retirada na confeitaria.");
        }
        for (Product product : chosenProducts.values()) {
            boolean recheck = typeChanged || !currentItems.containsKey(product.getId());
            if (recheck && !product.allows(type)) {
                throw new FieldValidationException("fulfillmentType", "\"" + product.getName() + "\" não está disponível "
                        + (delivery ? "para delivery." : "para retirada na confeitaria."));
            }
        }
    }

    private Map<Long, Product> loadProducts(List<ItemChoice> items) {
        if (items.size() > MAX_ITEMS) {
            throw new FieldValidationException("items", "Escolha no máximo " + MAX_ITEMS + " produtos diferentes.");
        }
        Set<Long> ids = new HashSet<>();
        for (ItemChoice item : items) {
            if (item == null || item.productId() == null) {
                throw new FieldValidationException("items", "Produto inválido.");
            }
            if (item.quantity() == null || item.quantity() < 1 || item.quantity() > 9999) {
                throw new FieldValidationException("items", "Informe uma quantidade entre 1 e 9999.");
            }
            if (!ids.add(item.productId())) {
                throw new FieldValidationException("items", "O mesmo produto foi escolhido duas vezes; ajuste a quantidade.");
            }
        }
        Map<Long, Product> found = products.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        if (found.size() != ids.size()) {
            throw new FieldValidationException("items", "Um dos produtos escolhidos não existe mais.");
        }
        return found;
    }

    /** Novos itens copiam os dados atuais do produto; itens que já estavam no pedido mantêm a cópia original. */
    private static Map<Long, OrderItem> toItems(List<ItemChoice> items, Map<Long, Product> products,
            Map<Long, OrderItem> current) {
        Map<Long, OrderItem> result = new LinkedHashMap<>();
        int position = 0;
        for (ItemChoice item : items) {
            Product product = products.get(item.productId());
            OrderItem previous = current.get(item.productId());
            String notes = blankToNull(item.notes());
            if (notes != null && notes.length() > 300) {
                throw new FieldValidationException("items", "Observação do item: máximo de 300 caracteres.");
            }
            result.put(product.getId(), previous != null
                    ? new OrderItem(previous.getNameSnapshot(), previous.getCategorySnapshot(),
                            previous.getUnitPriceSnapshot(), previous.getUnitLabelSnapshot(), item.quantity(), notes,
                            position++)
                    : new OrderItem(product.getName(), product.getCategory().getName(), product.getPrice(),
                            product.getUnitLabel(), item.quantity(), notes, position++));
        }
        return result;
    }

    private static String describe(List<OrderItem> items) {
        return items.stream().map(OrderItem::describe).collect(Collectors.joining(", "));
    }

    private void validateSchedule(LocalDate date, LocalTime time, Actor actor) {
        if (actor == Actor.CLIENT) {
            if (!availability.isBookable(date, time)) {
                throw new FieldValidationException("pickupTime",
                        "Este horário não está mais disponível. Escolha outra data ou horário.");
            }
            return;
        }
        // A proprietária pode usar qualquer horário cadastrado na agenda (inclusive com antecedência menor);
        // o limite de vagas é verificado na confirmação do sinal.
        boolean slotExists = weeklySlots.findByDayOfWeekAndStartTime((short) date.getDayOfWeek().getValue(), time)
                .filter(slot -> slot.isActive())
                .isPresent();
        if (!slotExists) {
            throw new FieldValidationException("pickupTime",
                    "Não há horário de retirada ativo às " + time + " nesse dia da semana.");
        }
    }

    private static Map<Long, String> resolveOptions(List<Long> ids, int max, String field, String singular,
            String plural, Set<Long> alreadyChosen, Function<List<Long>, List<? extends CatalogOption>> loader) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new FieldValidationException(field, "Não repita a mesma opção.");
        }
        if (ids.size() > max) {
            throw new FieldValidationException(field, "Este tamanho permite escolher até " + max + " "
                    + (max == 1 ? singular : plural) + ".");
        }
        Map<Long, CatalogOption> found = new LinkedHashMap<>();
        loader.apply(ids).forEach(option -> found.put(option.getId(), option));
        Map<Long, String> result = new LinkedHashMap<>();
        for (Long id : ids) {
            CatalogOption option = found.get(id);
            if (option == null || (!option.isActive() && !alreadyChosen.contains(id))) {
                throw new FieldValidationException(field, "Uma das opções escolhidas não está mais disponível.");
            }
            result.put(id, option.getName());
        }
        return result;
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
