package nhnad.soeun_chat.domain.conversation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.chat.repository.ConversationRepository;
import nhnad.soeun_chat.domain.chat.repository.MessageRepository;
import nhnad.soeun_chat.domain.conversation.dto.ConversationResponse;
import nhnad.soeun_chat.domain.conversation.dto.ConversationSummary;
import nhnad.soeun_chat.domain.conversation.dto.MessageItem;
import nhnad.soeun_chat.global.error.ErrorCode;
import nhnad.soeun_chat.global.exception.EntityNotFoundException;
import nhnad.soeun_chat.global.exception.ForbiddenException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationResponse createConversation(String userId, String title) {
        String conversationId = UUID.randomUUID().toString();
        String effectiveTitle = (title != null && !title.isBlank()) ? title : "새 대화";
        String now = Instant.now().toString();

        conversationRepository.save(conversationId, userId, effectiveTitle);
        log.info("대화 생성 완료 - conversationId: {}", conversationId);

        return new ConversationResponse(conversationId, effectiveTitle, now, now, List.of());
    }

    public List<ConversationSummary> getConversations(String userId) {
        return conversationRepository.findByUserId(userId).stream()
                .map(item -> new ConversationSummary(
                        attr(item, "conversationId"),
                        attrOrDefault(item, "title", "새 대화"),
                        attr(item, "updatedAt")
                ))
                .toList();
    }

    public ConversationResponse getConversation(String userId, String conversationId) {
        Map<String, AttributeValue> item = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));

        checkOwner(item, userId, conversationId);

        List<MessageItem> messages = messageRepository.findByConversationId(conversationId).stream()
                .map(msg -> new MessageItem(
                        attr(msg, "messageId"),
                        attr(msg, "role"),
                        attr(msg, "content"),
                        attr(msg, "createdAt")
                ))
                .toList();

        return new ConversationResponse(
                attr(item, "conversationId"),
                attrOrDefault(item, "title", "새 대화"),
                attr(item, "createdAt"),
                attr(item, "updatedAt"),
                messages
        );
    }

    public void deleteConversation(String userId, String conversationId) {
        Map<String, AttributeValue> item = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));

        checkOwner(item, userId, conversationId);

        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(conversationId);
        log.info("대화 삭제 완료 - conversationId: {}", conversationId);
    }

    private void checkOwner(Map<String, AttributeValue> item, String userId, String conversationId) {
        String owner = attrOrDefault(item, "userId", "");
        if (userId != null && !userId.equals(owner)) {
            log.warn("대화 접근 권한 없음 - conversationId: {}, userId: {}", conversationId, userId);
            throw new ForbiddenException();
        }
    }

    private String attr(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null ? v.s() : null;
    }

    private String attrOrDefault(Map<String, AttributeValue> item, String key, String defaultValue) {
        AttributeValue v = item.get(key);
        return (v != null && v.s() != null) ? v.s() : defaultValue;
    }
}
