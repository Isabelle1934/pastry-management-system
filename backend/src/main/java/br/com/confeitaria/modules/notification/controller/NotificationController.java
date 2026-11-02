package br.com.confeitaria.modules.notification.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.confeitaria.modules.notification.entity.Notification;
import br.com.confeitaria.modules.notification.entity.NotificationType;
import br.com.confeitaria.modules.notification.service.NotificationService;

@RestController
@RequestMapping("/api/admin/notifications")
public class NotificationController {

    public record NotificationView(Long id, NotificationType type, String title, Long orderId, String message,
            Instant createdAt, boolean read) {

        static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getType().title(), n.getOrderId(), n.getMessage(),
                    n.getCreatedAt(), n.getReadAt() != null);
        }
    }

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<NotificationView> list(@RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "30") int limit) {
        return notifications.recent(unread, limit).stream().map(NotificationView::from).toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notifications.unreadCount());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id) {
        notifications.markRead(id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead() {
        notifications.markAllRead();
    }
}
