package nhnad.soeun_chat.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "대화 상세 응답 (메시지 목록 포함)")
public record ConversationResponse(
        @Schema(description = "대화 고유 ID", example = "conv-abc123")
        String conversationId,

        @Schema(description = "대화 제목", example = "이번 주 광고 성과 분석")
        String title,

        @Schema(description = "생성 시각 (Unix timestamp, ms)", example = "1709740800000")
        Long createdAt,

        @Schema(description = "마지막 수정 시각 (Unix timestamp, ms)", example = "1709827200000")
        Long updatedAt,

        @Schema(description = "메시지 목록 (시간순 정렬)")
        List<MessageItem> messages
) {}
