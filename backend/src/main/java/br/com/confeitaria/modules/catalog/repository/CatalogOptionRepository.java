package br.com.confeitaria.modules.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import br.com.confeitaria.modules.catalog.entity.CatalogOption;

/** Consultas comuns a massas e recheios. */
@NoRepositoryBean
public interface CatalogOptionRepository<T extends CatalogOption> extends JpaRepository<T, Long> {

    List<T> findAllByOrderByDisplayOrderAscNameAsc();

    List<T> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
