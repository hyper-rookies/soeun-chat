package nhnad.soeun_chat.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.chat.dto.ChatMessage;
import nhnad.soeun_chat.domain.chat.repository.ConversationRepository;
import nhnad.soeun_chat.domain.chat.repository.MessageRepository;
import nhnad.soeun_chat.global.exception.BusinessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final BedrockService bedrockService;

    @Async("chatExecutor")
    public void processChat(SseEmitter emitter,
                            String conversationId,
                            String userId,
                            String userMessage) {
        try {
            // 1. 대화 컨텍스트 로드 또는 생성
            if (conversationRepository.findById(conversationId).isEmpty()) {
                conversationRepository.save(conversationId, userId);
            }

            // 2. 이전 메시지 히스토리 조회
            List<ChatMessage> history = messageRepository.findByConversationId(conversationId).stream()
                    .map(item -> new ChatMessage(
                            item.get("role").s(),
                            item.get("content").s()
                    ))
                    .toList();

            // 3. Bedrock Agentic Loop (SQL 생성 → Athena 쿼리 → 답변 스트리밍)
            String fullAnswer = bedrockService.runAgenticLoop(emitter, userMessage, history);

            // 4. DynamoDB에 대화 기록 저장
            messageRepository.save(conversationId, "user", userMessage);
            messageRepository.save(conversationId, "assistant", fullAnswer);
            conversationRepository.updateTimestamp(conversationId);

            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();

        } catch (BusinessException e) {
            log.error("[{}] 채팅 처리 실패: {} ({})", conversationId, e.getErrorCode().name(), e.getMessage());
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
            } catch (Exception ignored) {}
            emitter.complete();
        } catch (Exception e) {
            log.error("[{}] 예상치 못한 오류: {}", conversationId, e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data("처리 중 오류가 발생했습니다."));
            } catch (Exception ignored) {}
            emitter.complete();
        }
    }
}