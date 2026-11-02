package br.com.confeitaria.modules.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.confeitaria.modules.catalog.entity.CakeSize;

public interface CakeSizeRepository extends JpaRepository<CakeSize, Long> {

    List<CakeSize> findAllByOrderByDisplayOrderAscNameAsc();

    List<CakeSize> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
