package br.com.confeitaria.modules.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.confeitaria.modules.catalog.entity.CakeCategory;

public interface CakeCategoryRepository extends JpaRepository<CakeCategory, Long> {

    List<CakeCategory> findAllByOrderByDisplayOrderAscNameAsc();

    List<CakeCategory> findAllByActiveTrueOrderByDisplayOrderAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    interface SizeCount {
        Long getCategoryId();

        long getTotal();
    }

    @Query("select s.category.id as categoryId, count(s) as total from CakeSize s where s.category is not null "
            + "group by s.category.id")
    List<SizeCount> countSizes();
}
