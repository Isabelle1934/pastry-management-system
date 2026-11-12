package br.com.confeitaria.modules.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Contratos do módulo de clientes (somente leitura, derivado dos pedidos). */
public final class CustomerDtos {

    private CustomerDtos() {
    }

    /**
     * @param whatsappDigits número sem "+" (usado para filtrar os pedidos da cliente)
     * @param totalPaid      pagamentos confirmados menos reembolsos
     */
    public record CustomerSummary(String name, String whatsapp, String whatsappDigits, String whatsappLink,
            long orders, long confirmedOrders, long completedOrders, BigDecimal totalPaid, Instant firstOrderAt,
            Instant lastOrderAt, LocalDate lastPickupDate) {
    }
}
