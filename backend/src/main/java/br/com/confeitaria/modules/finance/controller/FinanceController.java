package br.com.confeitaria.modules.finance.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.finance.dto.FinanceDtos.FinanceSummary;
import br.com.confeitaria.modules.finance.service.FinanceSummaryService;

/**
 * Indicadores financeiros do painel. O cadastro de despesas foi retirado a pedido da proprietária
 * (as tabelas antigas continuam no banco, sem uso, para não perder histórico).
 */
@RestController
@RequestMapping("/api/admin/finance")
public class FinanceController {

    private final FinanceSummaryService summary;

    public FinanceController(FinanceSummaryService summary) {
        this.summary = summary;
    }

    @GetMapping("/summary")
    public FinanceSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return summary.summary(from, to);
    }
}
