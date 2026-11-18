package hrm.controller;

import hrm.dto.response.ApiResponse;
import hrm.dto.response.NotificationDto;
import hrm.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Authorized accounts can only read or update their own inbox, including administrators. */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("@access.has(authentication, 'NOTIFICATION_VIEW')")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<Page<NotificationDto>> list(@RequestParam(defaultValue = "1") int pageIndex,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ApiResponse.success("Lấy danh sách thông báo thành công", service.list(pageIndex, pageSize, unreadOnly));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success("Lấy số thông báo chưa đọc thành công", Map.of("unreadCount", service.unreadCount()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<NotificationDto> markRead(@PathVariable UUID id) {
        return ApiResponse.success("Đã đánh dấu thông báo đã đọc", service.markRead(id));
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        service.markAllRead();
        return ApiResponse.success("Đã đánh dấu tất cả thông báo đã đọc", null);
    }
}
