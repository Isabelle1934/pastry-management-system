package br.com.confeitaria.modules.order.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import br.com.confeitaria.common.audit.Changes;
import br.com.confeitaria.common.exception.FieldValidationException;
import br.com.confeitaria.modules.catalog.entity.CakeSize;
import br.com.confeitaria.modules.catalog.entity.CatalogOption;
import br.com.confeitaria.modules.catalog.repository.CakeFillingRepository;
import br.com.confeitaria.modules.catalog.repository.CakeFlavorRepository;
import br.com.confeitaria.modules.catalog.repository.CakeSizeRepository;
import br.com.confeitaria.modules.order.entity.CakeOrder;
import br.com.confeitaria.modules.schedule.repository.WeeklySlotRepository;
import br.com.confeitaria.modules.schedule.service.AvailabilityService;

/**
 * Valida e aplica agendamento, personalização e topo a um pedido. Usado na criação pela cliente,
 * na edição pela cliente e na edição pela proprietária — as mesmas regras nos três casos.
 */
@Component
public class OrderDetailsApplier {

    /** Dados editáveis do pedido (sem dados da cliente). */
    public record Details(LocalDate pickupDate, LocalTime pickupTime, Long sizeId, List<Long> flavorIds,
            List<Long> fillingIds, String decoration, String cakeText, String notes, boolean topperRequested,
            String topperTheme, String topperName, Integer topperAge) {
    }

    /** Quem está alterando: a cliente só escolhe horários disponíveis; a proprietária, qualquer horário da agenda. */
    public enum Actor {
        CLIENT,
        OWNER
    }

    private final CakeSizeRepository sizes;
    private final CakeFlavorRepository flavors;
    private final CakeFillingRepository fillings;
    private final AvailabilityService availability;
    private final WeeklySlotRepository weeklySlots;

    public OrderDetailsApplier(CakeSizeRepository sizes, CakeFlavorRepository flavors, CakeFillingRepository fillings,
            AvailabilityService availability, WeeklySlotRepository weeklySlots) {
        this.sizes = sizes;
        this.flavors = flavors;
        this.fillings = fillings;
        this.availability = availability;
        this.weeklySlots = weeklySlots;
    }

    /**
     * Valida tudo antes de alterar qualquer campo. Itens já escolhidos no pedido continuam válidos
     * mesmo que tenham sido desativados depois (a edição não obriga a trocar o que já estava combinado).
     *
     * @return campos alterados (para o histórico); vazio na criação
     */
    public Map<String, Object> apply(CakeOrder order, Details details, Actor actor) {
        boolean isNew = order.getId() == null;

        CakeSize currentSize = isNew ? null : order.getSize();
        CakeSize size = sizes.findById(details.sizeId())
                .filter(s -> s.isActive() || (currentSize != null && s.getId().equals(currentSize.getId())))
                .orElseThrow(() -> new FieldValidationException("sizeId", "Este tamanho não está mais disponível."));
        Map<Long, String> chosenFlavors = resolveOptions(details.flavorIds(), size.getMaxFlavors(), "flavorIds",
                "massa", "massas", order.getFlavorChoices().keySet(), ids -> flavors.findAllById(ids));
        Map<Long, String> chosenFillings = resolveOptions(details.fillingIds(), size.getMaxFillings(), "fillingIds",
                "recheio", "recheios", order.getFillingChoices().keySet(), ids -> fillings.findAllById(ids));

        boolean scheduleChanged = isNew || !details.pickupDate().equals(order.getPickupDate())
                || !details.pickupTime().equals(order.getPickupTime());
        if (scheduleChanged) {
            validateSchedule(details.pickupDate(), details.pickupTime(), actor);
        }

        String topperTheme = blankToNull(details.topperTheme());
        if (details.topperRequested() && topperTheme == null) {
            throw new FieldValidationException("topperTheme", "Informe o tema do topo de bolo.");
        }

        Changes changes = new Changes();
        if (!isNew) {
            changes.track("pickupDate", String.valueOf(order.getPickupDate()), String.valueOf(details.pickupDate()))
                    .track("pickupTime", String.valueOf(order.getPickupTime()), String.valueOf(details.pickupTime()))
                    .track("size", order.getSizeNameSnapshot(), size.getName())
                    .track("flavors", String.join(", ", order.getFlavorNames()), String.join(", ", chosenFlavors.values()))
                    .track("fillings", String.join(", ", order.getFillingNames()), String.join(", ", chosenFillings.values()))
                    .track("decoration", order.getDecoration(), blankToNull(details.decoration()))
                    .track("cakeText", order.getCakeText(), blankToNull(details.cakeText()))
                    .track("notes", order.getNotes(), blankToNull(details.notes()))
                    .track("topperRequested", order.isTopperRequested(), details.topperRequested())
                    .track("topperTheme", order.getTopperTheme(), details.topperRequested() ? topperTheme : null)
                    .track("topperName", order.getTopperName(),
                            details.topperRequested() ? blankToNull(details.topperName()) : null)
                    .track("topperAge", order.getTopperAge() == null ? null : order.getTopperAge().intValue(),
                            details.topperRequested() ? details.topperAge() : null);
        }

        order.schedule(details.pickupDate(), details.pickupTime());
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
        return changes.asMap();
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
