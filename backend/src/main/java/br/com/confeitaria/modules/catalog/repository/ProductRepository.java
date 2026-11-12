package br.com.confeitaria.modules.catalog.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.confeitaria.modules.catalog.entity.Product;
import br.com.confeitaria.modules.catalog.entity.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("select p from Product p join fetch p.category c order by c.displayOrder, c.name, p.displayOrder, p.name")
    List<Product> findAllForListing();

    @Query("""
            select p from Product p join fetch p.category c
            where p.status <> :hidden and c.active = true
            order by c.displayOrder, c.name, p.displayOrder, p.name""")
    List<Product> findPublicCandidates(ProductStatus hidden);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("select p.category.id as categoryId, count(p) as total from Product p group by p.category.id")
    List<CategoryCount> countByCategory();

    interface CategoryCount {
        Long getCategoryId();

        long getTotal();
    }
}
