package nhnad.soeun_chat.domain.chat.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.chat.dto.ChatRequest;
import nhnad.soeun_chat.domain.chat.service.ChatService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController implements ChatApi {

    private final ChatService chatService;

    @Override
    public SseEmitter chat(
            @PathVariable String conversationId,
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal String userId) {

        log.info("[{}] 채팅 요청 수신 - userId: {}", conversationId, userId);
        SseEmitter emitter = new SseEmitter(180_000L);
        chatService.processChat(emitter, conversationId, userId, request.message());
        return emitter;
    }
}
