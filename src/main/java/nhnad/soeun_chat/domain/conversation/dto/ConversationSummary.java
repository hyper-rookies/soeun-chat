package nhnad.soeun_chat.domain.conversation.dto;

public record ConversationSummary(
        String conversationId,
        String title,
        Long updatedAt
) {
}
