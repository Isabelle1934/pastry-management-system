package br.com.confeitaria.modules.finance.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.catalog.dto.CatalogDtos.ActiveRequest;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.CategoryRequest;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.CategoryResponse;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.ExpenseRequest;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.ExpenseView;
import br.com.confeitaria.modules.finance.dto.FinanceDtos.FinanceSummary;
import br.com.confeitaria.modules.finance.service.ExpenseCategoryService;
import br.com.confeitaria.modules.finance.service.ExpenseService;
import br.com.confeitaria.modules.finance.service.FinanceSummaryService;
import br.com.confeitaria.modules.order.dto.OrderAdminDtos.PageResponse;
import br.com.confeitaria.security.AdminPrincipal;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/finance")
public class FinanceController {

    private final FinanceSummaryService summary;
    private final ExpenseService expenses;
    private final ExpenseCategoryService categories;

    public FinanceController(FinanceSummaryService summary, ExpenseService expenses,
            ExpenseCategoryService categories) {
        this.summary = summary;
        this.expenses = expenses;
        this.categories = categories;
    }

    @GetMapping("/summary")
    public FinanceSummary summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return summary.summary(from, to);
    }

    // --- Despesas ---

    @GetMapping("/expenses")
    public PageResponse<ExpenseView> listExpenses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return expenses.list(from, to, categoryId, orderId, page, size);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseView createExpense(@Valid @RequestBody ExpenseRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return expenses.create(body, admin.getId());
    }

    @PutMapping("/expenses/{id}")
    public ExpenseView updateExpense(@PathVariable Long id, @Valid @RequestBody ExpenseRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return expenses.update(id, body, admin.getId());
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@PathVariable Long id, @RequestParam long version,
            @AuthenticationPrincipal AdminPrincipal admin) {
        expenses.delete(id, version, admin.getId());
    }

    // --- Categorias ---

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories() {
        return categories.list();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return categories.create(body, admin.getId());
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse renameCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return categories.rename(id, body, admin.getId());
    }

    @PatchMapping("/categories/{id}/active")
    public CategoryResponse setCategoryActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest body,
            @AuthenticationPrincipal AdminPrincipal admin) {
        return categories.setActive(id, body.active(), body.version(), admin.getId());
    }
}
