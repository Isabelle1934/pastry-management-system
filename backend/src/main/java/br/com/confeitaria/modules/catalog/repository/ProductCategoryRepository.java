package br.com.confeitaria.modules.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.confeitaria.modules.catalog.entity.ProductCategory;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findAllByOrderByDisplayOrderAscNameAsc();

    List<ProductCategory> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
