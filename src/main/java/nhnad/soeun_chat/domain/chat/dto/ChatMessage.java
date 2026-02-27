package nhnad.soeun_chat.domain.chat.dto;

/**
 * Bedrock 호출 시 대화 히스토리 전달용 내부 DTO
 */
public record ChatMessage(String role, String content) {}
