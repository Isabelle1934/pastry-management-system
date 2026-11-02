package br.com.confeitaria.common.audit;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Monta o mapa "campo → {from, to}" de uma edição, só com o que realmente mudou (para auditoria). */
public final class Changes {

    private final Map<String, Object> changes = new LinkedHashMap<>();

    public Changes track(String field, Object before, Object after) {
        if (!same(before, after)) {
            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("from", plain(before));
            diff.put("to", plain(after));
            changes.put(field, diff);
        }
        return this;
    }

    /** Registra apenas que o campo mudou, sem os valores (ex.: dados de pagamento). */
    public Changes trackWithoutValues(String field, Object before, Object after) {
        if (!same(before, after)) {
            changes.put(field, "alterado");
        }
        return this;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public Map<String, Object> asMap() {
        return Map.copyOf(changes);
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }

    private static Object plain(Object value) {
        return value instanceof BigDecimal decimal ? decimal.toPlainString() : value;
    }
}
