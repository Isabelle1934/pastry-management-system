package br.com.confeitaria.modules.notification.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import br.com.confeitaria.modules.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<Notification> findByReadAtIsNullOrderByCreatedAtDescIdDesc(Pageable pageable);

    long countByReadAtIsNull();

    @Query("""
            select n from Notification n
            where n.emailStatus in ('PENDING', 'FAILED') and n.emailAttempts < :maxAttempts and n.createdAt <= :before
            order by n.createdAt
            """)
    List<Notification> findEmailsToRetry(int maxAttempts, Instant before, Pageable pageable);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.readAt is null")
    int markAllRead(Instant now);
}
