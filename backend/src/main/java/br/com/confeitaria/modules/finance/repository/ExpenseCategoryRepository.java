package br.com.confeitaria.modules.finance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.confeitaria.modules.finance.entity.ExpenseCategory;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
