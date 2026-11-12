package br.com.confeitaria.modules.file.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.confeitaria.modules.file.entity.StoredFile;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {

    List<StoredFile> findByOrderIdAndDeletedAtIsNullOrderByIdAsc(Long orderId);

    List<StoredFile> findByProductIdAndDeletedAtIsNullOrderByIdAsc(Long productId);

    List<StoredFile> findByProductIdInAndDeletedAtIsNullOrderByIdAsc(java.util.Collection<Long> productIds);
}
