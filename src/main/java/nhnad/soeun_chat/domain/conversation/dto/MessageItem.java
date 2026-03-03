package nhnad.soeun_chat.domain.conversation.dto;

public record MessageItem(
        String messageId,
        String role,
        String content,
        String createdAt
) {
}
