package nhnad.soeun_chat.domain.conversation.dto;

import java.util.List;

public record MessageItem(
        String messageId,
        String role,
        String content,
        String createdAt,
        String chartType,
        List<Object> data
) {
}
