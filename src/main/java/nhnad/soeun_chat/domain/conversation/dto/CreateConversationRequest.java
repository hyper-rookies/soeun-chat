package nhnad.soeun_chat.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대화 생성 요청")
public record CreateConversationRequest(
        @Schema(description = "대화 제목 (비워두면 기본 제목으로 저장됨)", example = "3월 광고 성과 분석", nullable = true)
        String title
) {}
