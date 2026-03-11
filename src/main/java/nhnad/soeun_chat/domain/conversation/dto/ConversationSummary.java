package nhnad.soeun_chat.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대화 목록 요약 (사이드바 표시용)")
public record ConversationSummary(
        @Schema(description = "대화 고유 ID", example = "conv-abc123")
        String conversationId,

        @Schema(description = "대화 제목", example = "이번 주 광고 성과 분석")
        String title,

        @Schema(description = "마지막 수정 시각 (Unix timestamp, ms)", example = "1709740800000")
        Long updatedAt
) {}
