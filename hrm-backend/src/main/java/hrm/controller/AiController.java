package hrm.controller;

import hrm.dto.request.AiChatRequest;
import hrm.dto.response.ApiResponse;
import hrm.service.AiChatService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class AiController {
    private final AiChatService service;
    public AiController(AiChatService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("@access.has(authentication, 'AI_CHAT')")
    public ApiResponse<AiChatService.Answer> chat(@RequestBody AiChatRequest request) {
        return ApiResponse.success("Đã xử lý câu hỏi", service.answer(request));
    }
}
