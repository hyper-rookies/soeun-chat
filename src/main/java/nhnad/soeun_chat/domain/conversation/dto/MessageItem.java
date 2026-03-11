package nhnad.soeun_chat.domain.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "메시지 항목")
public record MessageItem(
        @Schema(description = "메시지 고유 ID", example = "msg-001")
        String messageId,

        @Schema(description = "발신자 역할 — user(사용자) 또는 assistant(AI)", example = "assistant",
                allowableValues = {"user", "assistant"})
        String role,

        @Schema(description = "메시지 내용 (텍스트). 차트/테이블 응답도 텍스트 설명을 포함합니다.", example = "이번 주 카카오 광고 비용은 1,200,000원입니다.")
        String content,

        @Schema(description = "메시지 생성 시각 (ISO 8601 형식)", example = "2024-03-07T10:00:05")
        String createdAt,

        @Schema(description = """
                차트 타입 (AI 응답에만 존재, 일반 텍스트 응답은 null)
                - null : 텍스트 전용 응답
                - bar : 막대 차트
                - line : 라인 차트
                - pie : 파이 차트
                - table : 테이블
                """,
                example = "bar", nullable = true,
                allowableValues = {"bar", "line", "pie", "table"})
        String chartType,

        @Schema(description = "차트/테이블 데이터 배열 (chartType이 null이면 비어 있음). 데이터 구조는 chartType에 따라 다릅니다.",
                nullable = true, example = "[{\"date\":\"2024-03-01\",\"cost\":150000}]")
        List<Object> data
) {}
