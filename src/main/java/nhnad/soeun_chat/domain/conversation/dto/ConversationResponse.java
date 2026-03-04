package nhnad.soeun_chat.domain.conversation.dto;

import java.util.List;

public record ConversationResponse(
        String conversationId,
        String title,
        Long createdAt,
        Long updatedAt,
        List<MessageItem> messages
) {
}
