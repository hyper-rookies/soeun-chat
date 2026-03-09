package nhnad.soeun_chat.domain.conversation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nhnad.soeun_chat.domain.chat.repository.ConversationRepository;
import nhnad.soeun_chat.domain.chat.repository.MessageRepository;
import nhnad.soeun_chat.domain.conversation.dto.ConversationResponse;
import nhnad.soeun_chat.domain.report.service.ReportS3Loader;
import nhnad.soeun_chat.domain.conversation.dto.ConversationSummary;
import nhnad.soeun_chat.domain.conversation.dto.MessageItem;
import nhnad.soeun_chat.global.error.ErrorCode;
import nhnad.soeun_chat.global.exception.EntityNotFoundException;
import nhnad.soeun_chat.global.exception.ForbiddenException;
import nhnad.soeun_chat.global.exception.InvalidValueException;
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
    private final ObjectMapper objectMapper;
    private final ReportS3Loader reportS3Loader;

    public ConversationResponse createConversation(String userId, String title) {
        String conversationId = UUID.randomUUID().toString();
        String effectiveTitle = (title != null && !title.isBlank()) ? title : "새 대화";
        long now = Instant.now().toEpochMilli();

        conversationRepository.save(conversationId, userId, effectiveTitle);
        log.info("대화 생성 완료 - conversationId: {}", conversationId);

        return new ConversationResponse(conversationId, effectiveTitle, now, now, List.of());
    }

    public List<ConversationSummary> getConversations(String userId) {
        long now = Instant.now().toEpochMilli();
        return conversationRepository.findByUserId(userId).stream()
                .map(item -> new ConversationSummary(
                        attr(item, "conversationId"),
                        attrOrDefault(item, "title", "새 대화"),
                        attrNOrDefault(item, "updatedAt", now)
                ))
                .toList();
    }

    public ConversationResponse getConversation(String userId, String conversationId) {
        Map<String, AttributeValue> item = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));

        checkOwner(item, userId, conversationId);

        String reportS3Key = attrOrDefault(item, "reportS3Key", null);
        if (reportS3Key != null) {
            return reportS3Loader.load(item, reportS3Key);
        }

        long now = Instant.now().toEpochMilli();

        List<MessageItem> messages = messageRepository.findByConversationId(conversationId).stream()
                .map(msg -> new MessageItem(
                        attr(msg, "messageId"),
                        attr(msg, "role"),
                        attr(msg, "content"),
                        attr(msg, "createdAt"),
                        attr(msg, "chartType"),
                        parseStructuredData(msg)
                ))
                .toList();

        return new ConversationResponse(
                attr(item, "conversationId"),
                attrOrDefault(item, "title", "새 대화"),
                attrNOrDefault(item, "createdAt", now),
                attrNOrDefault(item, "updatedAt", now),
                messages
        );
    }

    public void updateTitle(String conversationId, String title) {
        conversationRepository.updateTitle(conversationId, title);
        log.info("대화 제목 업데이트 - conversationId: {}, title: {}", conversationId, title);
    }

    public void updateTitle(String conversationId, String userId, String title) {
        if (title == null || title.isBlank()) {
            throw new InvalidValueException();
        }
        Map<String, AttributeValue> item = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND));
        checkOwner(item, userId, conversationId);
        conversationRepository.updateTitle(conversationId, title);
        log.info("대화 제목 수정 - conversationId: {}, userId: {}, title: {}", conversationId, userId, title);
    }

    public void updateUpdatedAt(String conversationId, long updatedAt) {
        conversationRepository.updateUpdatedAt(conversationId, updatedAt);
        log.debug("대화 updatedAt 업데이트 - conversationId: {}", conversationId);
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

    private List<Object> parseStructuredData(Map<String, AttributeValue> msg) {
        AttributeValue v = msg.get("structuredData");
        if (v == null || v.s() == null) return null;
        try {
            return objectMapper.readValue(v.s(), new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("structuredData 파싱 실패: {}", e.getMessage());
            return null;
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

    private Long attrNOrDefault(Map<String, AttributeValue> item, String key, Long defaultValue) {
        AttributeValue v = item.get(key);
        if (v != null && v.n() != null) {
            return Long.parseLong(v.n());
        }
        return defaultValue;
    }
}
