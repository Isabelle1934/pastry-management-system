package br.com.confeitaria.modules.finance.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.confeitaria.modules.finance.entity.Expense;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndDeletedAtIsNull(Long id);

    /** Despesas não excluídas no período, com filtros opcionais de categoria e pedido. */
    @Query(value = """
            select e from Expense e join fetch e.category
            where e.deletedAt is null and e.expenseDate between :from and :to
              and (:categoryId is null or e.category.id = :categoryId)
              and (:orderId is null or e.orderId = :orderId)
            """, countQuery = """
            select count(e) from Expense e
            where e.deletedAt is null and e.expenseDate between :from and :to
              and (:categoryId is null or e.category.id = :categoryId)
              and (:orderId is null or e.orderId = :orderId)
            """)
    Page<Expense> search(LocalDate from, LocalDate to, Long categoryId, Long orderId, Pageable pageable);
}
